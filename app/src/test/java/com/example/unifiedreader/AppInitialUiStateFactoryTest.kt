package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferences
import com.example.unifiedreader.feature.navigation.SourceNavigationStateCoordinator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInitialUiStateFactoryTest {
    @Test
    fun `create builds initial shell without hydrating app state`() {
        val fixture = AppInitialUiStateFactoryFixture()

        val state = fixture.factory.create()

        assertEquals(listOf("book-1"), state.items.map(ReadableItem::id))
        assertEquals(emptyList<String>(), fixture.events())
        assertEquals(0, fixture.startupTasks.calls.get())
        assertEquals(0, fixture.remoteCatalogHydrator.calls.get())
        assertEquals(0, fixture.localDocumentHydrator.calls.get())
        assertEquals(0, fixture.readerSessionHydrator.calls.get())
    }

    @Test
    fun `create seeds startup source cache before first snapshot without hydrating app state`() {
        val cachedLibrary = Library(
            id = SourceType.KAVITA.remoteLibraryId("cached-comics"),
            sourceType = SourceType.KAVITA,
            title = "Cached Comics",
            remoteId = "cached-comics",
            contentType = LibraryContentType.COMICS,
        )
        val cachedItem = sampleItem(
            id = "cached-kavita-book",
            title = "Cached Kavita Book",
            format = FormatType.EPUB,
        )
        val libraryRepository = FakeLibraryRepository()
        val readableItemRepository = FakeReadableItemRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val startupSeeder = RecordingStartupRemoteCatalogCacheSeeder { sourceType ->
            if (sourceType == SourceType.KAVITA) {
                libraryRepository.emitLibraries(listOf(cachedLibrary))
                readableItemRepository.emitItems(listOf(cachedItem))
            }
        }
        val startupReaderSeeder = RecordingStartupReaderSessionCacheSeeder { itemIds ->
            if (cachedItem.id in itemIds) {
                readerSessionStore.seedProgress(
                    ReaderProgressSnapshot(
                        itemId = cachedItem.id,
                        locatorType = LocatorType.PAGE,
                        page = 4,
                        percent = 0.35,
                        updatedAtEpochMillis = 25L,
                    ),
                )
                readerSessionStore.seedSession(
                    ReaderSessionSnapshot(
                        itemId = cachedItem.id,
                        pageCount = 12,
                    ),
                )
            }
        }
        val fixture = AppInitialUiStateFactoryFixture(
            sourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(
                SourceNavigationPreferences(sourceOrder = listOf(SourceType.KAVITA)),
            ),
            libraryRepository = libraryRepository,
            readableItemRepository = readableItemRepository,
            readerSessionStore = readerSessionStore,
            startupRemoteCatalogCacheSeeder = startupSeeder,
            startupReaderSessionCacheSeeder = startupReaderSeeder,
        )

        val state = fixture.factory.create()

        assertEquals(listOf(SourceType.KAVITA), startupSeeder.seededSources)
        assertEquals(listOf(listOf(cachedItem.id)), startupReaderSeeder.seededItemIds)
        assertEquals(listOf(cachedItem.id), state.items.map(ReadableItem::id))
        assertEquals(listOf(cachedItem.id), state.visibleItems.map(ReadableItem::id))
        assertTrue(state.libraryShelvesState.hasContent)
        assertEquals(cachedItem.id, state.libraryShelvesState.continueReading.single().item.id)
        assertEquals(false, state.libraryShelvesState.isLoading)
        assertEquals(emptyList<String>(), fixture.events())
        assertEquals(0, fixture.startupTasks.calls.get())
        assertEquals(0, fixture.remoteCatalogHydrator.calls.get())
        assertEquals(0, fixture.localDocumentHydrator.calls.get())
        assertEquals(0, fixture.readerSessionHydrator.calls.get())
        assertEquals(false, state.isSelectedSourceHydrated)
    }

    @Test
    fun `create seeds restored route source before first snapshot`() {
        val cachedLibrary = Library(
            id = SourceType.STUMP.remoteLibraryId("cached-comics"),
            sourceType = SourceType.STUMP,
            title = "Cached Stump Comics",
            remoteId = "cached-comics",
            contentType = LibraryContentType.COMICS,
        )
        val cachedItem = sampleItem(
            id = "cached-stump-book",
            title = "Cached Stump Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.STUMP,
            libraryId = SourceType.STUMP.remoteLibraryId("cached-comics"),
            remoteId = "cached-stump-book",
        )
        val libraryRepository = FakeLibraryRepository()
        val readableItemRepository = FakeReadableItemRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val startupSeeder = RecordingStartupRemoteCatalogCacheSeeder { sourceType ->
            if (sourceType == SourceType.STUMP) {
                libraryRepository.emitLibraries(listOf(cachedLibrary))
                readableItemRepository.emitItems(listOf(cachedItem))
            }
        }
        val startupReaderSeeder = RecordingStartupReaderSessionCacheSeeder { itemIds ->
            if (cachedItem.id in itemIds) {
                readerSessionStore.seedProgress(
                    ReaderProgressSnapshot(
                        itemId = cachedItem.id,
                        locatorType = LocatorType.PAGE,
                        page = 4,
                        percent = 0.35,
                        updatedAtEpochMillis = 25L,
                    ),
                )
                readerSessionStore.seedSession(
                    ReaderSessionSnapshot(
                        itemId = cachedItem.id,
                        pageCount = 12,
                    ),
                )
            }
        }
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
            libraryRepository = libraryRepository,
            readableItemRepository = readableItemRepository,
            readerSessionStore = readerSessionStore,
            startupRemoteCatalogCacheSeeder = startupSeeder,
            startupReaderSessionCacheSeeder = startupReaderSeeder,
        )

        val state = fixture.factory.create()

        assertEquals(listOf(SourceType.STUMP), startupSeeder.seededSources)
        assertEquals(listOf(listOf(cachedItem.id)), startupReaderSeeder.seededItemIds)
        assertEquals(SourceType.STUMP, state.selectedSource)
        assertEquals(listOf(cachedItem.id), state.items.map(ReadableItem::id))
        assertEquals(listOf(cachedItem.id), state.visibleItems.map(ReadableItem::id))
        assertTrue(state.libraryShelvesState.hasContent)
        assertEquals(cachedItem.id, state.libraryShelvesState.continueReading.single().item.id)
        assertEquals(false, state.libraryShelvesState.isLoading)
        assertEquals(false, state.isSelectedSourceHydrated)
    }

    @Test
    fun `create ignores stale fallback reader item when choosing startup cache source`() {
        val staleFallbackItem = sampleItem(
            id = "stale-reader-item",
            title = "Stale Reader Item",
            format = FormatType.EPUB,
        )
        val startupSeeder = RecordingStartupRemoteCatalogCacheSeeder { }
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
            startupRemoteCatalogCacheSeeder = startupSeeder,
        )

        val state = fixture.factory.create()

        assertEquals(listOf(SourceType.STUMP), startupSeeder.seededSources)
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
    private val startupRemoteCatalogCacheSeeder: StartupRemoteCatalogCacheSeeder = TestNoOpStartupRemoteCatalogCacheSeeder,
    private val startupReaderSessionCacheSeeder: StartupReaderSessionCacheSeeder = TestNoOpStartupReaderSessionCacheSeeder,
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
            startupRemoteCatalogCacheSeeder = startupRemoteCatalogCacheSeeder,
            startupReaderSessionCacheSeeder = startupReaderSessionCacheSeeder,
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

private object TestNoOpStartupRemoteCatalogCacheSeeder : StartupRemoteCatalogCacheSeeder {
    override suspend fun seedForStartupSource(
        sourceType: SourceType,
    ) = Unit
}

private object TestNoOpStartupReaderSessionCacheSeeder : StartupReaderSessionCacheSeeder {
    override suspend fun seedForStartupItemIds(
        itemIds: List<String>,
    ) = Unit
}

private class RecordingStartupRemoteCatalogCacheSeeder(
    private val onSeed: (SourceType) -> Unit,
) : StartupRemoteCatalogCacheSeeder {
    val seededSources = mutableListOf<SourceType>()

    override suspend fun seedForStartupSource(
        sourceType: SourceType,
    ) {
        seededSources += sourceType
        onSeed(sourceType)
    }
}

private class RecordingStartupReaderSessionCacheSeeder(
    private val onSeed: (List<String>) -> Unit,
) : StartupReaderSessionCacheSeeder {
    val seededItemIds = mutableListOf<List<String>>()

    override suspend fun seedForStartupItemIds(
        itemIds: List<String>,
    ) {
        seededItemIds += itemIds
        onSeed(itemIds)
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
