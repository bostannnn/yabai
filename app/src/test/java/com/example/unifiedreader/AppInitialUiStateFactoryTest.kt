package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferences
import com.example.unifiedreader.feature.navigation.SourceNavigationStateCoordinator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppInitialUiStateFactoryTest {
    @Test
    fun `create builds initial shell from in-memory catalog without hydrating`() {
        val fixture = AppInitialUiStateFactoryFixture()

        val state = fixture.factory.create()

        // initialSnapshot reads only the already-in-memory repository state (cheap, non-blocking)
        // and must not trigger any hydration on the construction thread.
        assertEquals(listOf("book-1"), state.items.map(ReadableItem::id))
        assertEquals(emptyList<String>(), fixture.events())
        assertEquals(0, fixture.startupTasks.calls.get())
        assertEquals(0, fixture.remoteCatalogHydrator.calls.get())
        assertEquals(0, fixture.localDocumentHydrator.calls.get())
        assertEquals(0, fixture.readerSessionHydrator.calls.get())
    }

    @Test
    fun `create applies restored route source without hydrating`() {
        val fixture = AppInitialUiStateFactoryFixture(
            sourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(
                SourceNavigationPreferences(sourceOrder = listOf(SourceType.KAVITA, SourceType.STUMP)),
            ),
            activeRouteStore = FakeAppActiveRouteStore(
                AppActiveRouteSnapshot(
                    topLevel = TopLevelDestination.Library,
                    selectedSource = SourceType.STUMP,
                    kavitaLibrarySection = KavitaLibrarySection.Home,
                ),
            ),
        )

        val state = fixture.factory.create()

        assertEquals(SourceType.STUMP, state.selectedSource)
        assertEquals(listOf("book-1"), state.items.map(ReadableItem::id))
        assertEquals(false, state.isSelectedSourceHydrated)
        assertEquals(emptyList<String>(), fixture.events())
        assertEquals(0, fixture.remoteCatalogHydrator.calls.get())
    }

    @Test
    fun `create clears stale fallback reader route`() {
        val staleFallbackItem = sampleItem(
            id = "stale-reader-item",
            title = "Stale Reader Item",
            format = FormatType.EPUB,
        )
        val fixture = AppInitialUiStateFactoryFixture(
            sourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(
                SourceNavigationPreferences(sourceOrder = listOf(SourceType.KAVITA, SourceType.STUMP)),
            ),
            activeRouteStore = FakeAppActiveRouteStore(
                AppActiveRouteSnapshot(
                    topLevel = TopLevelDestination.Library,
                    selectedSource = SourceType.STUMP,
                    kavitaLibrarySection = KavitaLibrarySection.Home,
                    readerItemId = staleFallbackItem.id,
                    readerFormat = FormatType.PDF,
                    readerFallbackItem = staleFallbackItem,
                ),
            ),
        )

        val state = fixture.factory.create()

        assertEquals(SourceType.STUMP, state.selectedSource)
        assertEquals(null, state.activeReader)
    }

    @Test
    fun `hydrate after initial state runs full app hydration later`() = runBlocking {
        val fixture = AppInitialUiStateFactoryFixture()
        fixture.factory.create()

        fixture.factory.hydrateAfterInitialState()

        val events = fixture.events()
        assertEquals("startup", events.first())
        assertEquals(setOf("remote", "local", "reader"), events.drop(1).toSet())
        assertEquals(4, events.size)
        assertEquals(1, fixture.startupTasks.calls.get())
        assertEquals(1, fixture.remoteCatalogHydrator.calls.get())
        assertEquals(1, fixture.localDocumentHydrator.calls.get())
        assertEquals(1, fixture.readerSessionHydrator.calls.get())
    }
}

private class AppInitialUiStateFactoryFixture(
    private val sourceNavigationPreferencesStore: FakeSourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(),
    private val activeRouteStore: FakeAppActiveRouteStore = FakeAppActiveRouteStore(),
    private val libraryRepository: FakeLibraryRepository = FakeLibraryRepository(),
    private val readableItemRepository: FakeReadableItemRepository = FakeReadableItemRepository(
        items = listOf(sampleItem(id = "book-1", format = FormatType.EPUB)),
    ),
    private val readerSessionStore: FakeReaderSessionStore = FakeReaderSessionStore(),
) {
    private val recordedEvents = mutableListOf<String>()
    val startupTasks = RecordingStartupTasks(recordedEvents)
    val remoteCatalogHydrator = RecordingRemoteCatalogHydrator(recordedEvents)
    val localDocumentHydrator = RecordingLocalDocumentHydrator(recordedEvents)
    val readerSessionHydrator = RecordingReaderSessionHydrator(recordedEvents)

    private val appDispatchers = AppDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
    )
    private val localDocumentRepository = FakeLocalDocumentRepository()
    private val libraryStateAssembler = AppLibraryStateAssembler(
        localDocumentRepository = localDocumentRepository,
        remoteDownloadDataSources = emptyList(),
    )

    val factory = AppInitialUiStateFactory(
        navigationController = AppNavigationController(
            sourceNavigationStateCoordinator = SourceNavigationStateCoordinator(
                preferencesStore = sourceNavigationPreferencesStore,
            ),
        ),
        activeRouteStore = activeRouteStore,
        appStateHydrator = AppStateHydrator(
            appStartupTasks = startupTasks,
            remoteCatalogHydrator = remoteCatalogHydrator,
            localDocumentHydrator = localDocumentHydrator,
            readerSessionHydrator = readerSessionHydrator,
        ),
        catalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
            settingsPreferencesStore = FakeSettingsPreferencesStore(),
            libraryRepository = libraryRepository,
            readableItemRepository = readableItemRepository,
            remoteCatalogReadiness = FakeRemoteCatalogReadiness(initialHydrated = false),
            localDocumentRepository = localDocumentRepository,
            readerSessionStore = readerSessionStore,
            libraryStateAssembler = libraryStateAssembler,
            appDispatchers = appDispatchers,
        ),
        libraryStateRecomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        ),
    )

    fun events(): List<String> = synchronized(recordedEvents) {
        recordedEvents.toList()
    }
}

private class RecordingStartupTasks(
    private val events: MutableList<String>,
) : AppStartupTasks {
    val calls = AtomicInteger()

    override suspend fun initialize() {
        calls.incrementAndGet()
        events.record("startup")
    }
}

private class RecordingRemoteCatalogHydrator(
    private val events: MutableList<String>,
) : RemoteCatalogHydrator {
    val calls = AtomicInteger()

    override suspend fun hydrate() {
        calls.incrementAndGet()
        events.record("remote")
    }
}

private class RecordingLocalDocumentHydrator(
    private val events: MutableList<String>,
) : LocalDocumentHydrator {
    val calls = AtomicInteger()

    override suspend fun hydrate() {
        calls.incrementAndGet()
        events.record("local")
    }
}

private class RecordingReaderSessionHydrator(
    private val events: MutableList<String>,
) : ReaderSessionHydrator {
    val calls = AtomicInteger()

    override suspend fun hydrate() {
        calls.incrementAndGet()
        events.record("reader")
    }
}

private fun MutableList<String>.record(
    event: String,
) {
    synchronized(this) {
        add(event)
    }
}
