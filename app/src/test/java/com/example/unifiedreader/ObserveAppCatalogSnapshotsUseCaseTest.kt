package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObserveAppCatalogSnapshotsUseCaseTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `observed snapshot includes cached remote local state without scanning downloads`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val item = sampleItem(id = "epub-1", format = FormatType.EPUB)
        val localStateCache = InMemoryRemoteItemLocalStateCache().apply {
            store(
                items = listOf(item),
                state = RemoteItemLocalState(
                    downloadedItemIds = setOf(item.id),
                    locallyAvailableItemIds = setOf(item.id),
                ),
            )
        }
        val downloadDataSource = CountingRemoteItemDownloadDataSource()
        val useCase = ObserveAppCatalogSnapshotsUseCase(
            settingsPreferencesStore = FakeSettingsPreferencesStore(),
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
            localDocumentRepository = FakeLocalDocumentRepository(),
            readerSessionStore = FakeReaderSessionStore(),
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = listOf(downloadDataSource),
                remoteItemLocalStateCache = localStateCache,
            ),
            appDispatchers = AppDispatchers(
                io = dispatcher,
                default = dispatcher,
            ),
        )

        val snapshot = useCase.observe().first()

        assertEquals(setOf(item.id), snapshot.downloadedRemoteItemIds)
        assertEquals(setOf(item.id), snapshot.locallyAvailableRemoteItemIds)
        assertEquals(true, snapshot.includesRemoteItemLocalState)
        assertEquals(0, downloadDataSource.storedDocumentItemIdsCalls)
    }

    @Test
    fun `cached remote local state is scoped to source library and filtered to current ids`() {
        val item = sampleItem(id = "epub-1", format = FormatType.EPUB)
        val otherLibraryItem = item.copy(
            id = "epub-other-library",
            libraryId = SourceType.KAVITA.remoteLibraryId("other-library"),
        )
        val otherSourceItem = item.copy(
            id = "stump-1",
            sourceType = SourceType.STUMP,
            libraryId = SourceType.STUMP.remoteLibraryId("k-comics"),
        )
        val cache = InMemoryRemoteItemLocalStateCache()
        cache.store(
            items = listOf(item),
            state = RemoteItemLocalState(
                downloadedItemIds = setOf(item.id),
                locallyAvailableItemIds = setOf(item.id),
            ),
        )

        assertEquals(
            RemoteItemLocalState(
                downloadedItemIds = setOf(item.id),
                locallyAvailableItemIds = setOf(item.id),
                knownSourceLibraryScopeIds = setOf(item.remoteItemLocalStateSourceLibraryScopeId()),
            ),
            cache.load(listOf(item, otherLibraryItem, otherSourceItem)),
        )
        assertEquals(
            null,
            cache.load(listOf(otherLibraryItem)),
        )
        assertEquals(
            null,
            cache.load(listOf(otherSourceItem)),
        )
        assertEquals(
            RemoteItemLocalState(
                downloadedItemIds = emptySet(),
                locallyAvailableItemIds = emptySet(),
                knownSourceLibraryScopeIds = setOf(item.remoteItemLocalStateSourceLibraryScopeId()),
            ),
            cache.load(
                listOf(item.copy(id = "epub-2")),
            ),
        )
    }

    @Test
    fun `shared preferences cache persists source library entries independently`() {
        val suffix = System.nanoTime().toString()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstLibraryItem = sampleItem(
            id = "epub-$suffix",
            format = FormatType.EPUB,
        ).copy(
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics-$suffix"),
        )
        val secondLibraryItem = sampleItem(
            id = "pdf-$suffix",
            format = FormatType.PDF,
        ).copy(
            libraryId = SourceType.KAVITA.remoteLibraryId("k-books-$suffix"),
        )
        val storedState = RemoteItemLocalState(
            downloadedItemIds = setOf(firstLibraryItem.id, secondLibraryItem.id),
            locallyAvailableItemIds = setOf(firstLibraryItem.id, secondLibraryItem.id),
            knownSourceLibraryScopeIds = setOf(
                firstLibraryItem.remoteItemLocalStateSourceLibraryScopeId(),
                secondLibraryItem.remoteItemLocalStateSourceLibraryScopeId(),
            ),
        )

        SharedPreferencesRemoteItemLocalStateCache(context).store(
            items = listOf(firstLibraryItem, secondLibraryItem),
            state = storedState,
        )

        val reloadedCache = SharedPreferencesRemoteItemLocalStateCache(context)
        assertEquals(
            RemoteItemLocalState(
                downloadedItemIds = setOf(firstLibraryItem.id),
                locallyAvailableItemIds = setOf(firstLibraryItem.id),
                knownSourceLibraryScopeIds = setOf(firstLibraryItem.remoteItemLocalStateSourceLibraryScopeId()),
            ),
            reloadedCache.load(listOf(firstLibraryItem)),
        )
        assertEquals(
            RemoteItemLocalState(
                downloadedItemIds = setOf(secondLibraryItem.id),
                locallyAvailableItemIds = setOf(secondLibraryItem.id),
                knownSourceLibraryScopeIds = setOf(secondLibraryItem.remoteItemLocalStateSourceLibraryScopeId()),
            ),
            reloadedCache.load(listOf(secondLibraryItem)),
        )
        assertEquals(
            storedState,
            reloadedCache.load(listOf(firstLibraryItem, secondLibraryItem)),
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `fresh remote local availability scan updates cache`() = runTest {
        val item = sampleItem(id = "epub-1", format = FormatType.EPUB)
        val cache = InMemoryRemoteItemLocalStateCache()
        val assembler = AppLibraryStateAssembler(
            localDocumentRepository = FakeLocalDocumentRepository(),
            remoteDownloadDataSources = listOf(
                CountingRemoteItemDownloadDataSource(
                    sourceType = SourceType.KAVITA,
                    storedItemIds = setOf(item.id),
                ),
            ),
            remoteItemLocalStateCache = cache,
        )

        val freshState = assembler.loadRemoteItemLocalState(listOf(item))

        assertEquals(
            RemoteItemLocalState(
                downloadedItemIds = setOf(item.id),
                locallyAvailableItemIds = setOf(item.id),
                knownSourceLibraryScopeIds = setOf(item.remoteItemLocalStateSourceLibraryScopeId()),
            ),
            freshState,
        )
        assertEquals(freshState, cache.load(listOf(item)))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `progress changes do not rebuild catalog snapshots`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val item = sampleItem(id = "epub-1", format = FormatType.EPUB)
        val downloadDataSource = CountingRemoteItemDownloadDataSource()
        val readerSessionStore = FakeReaderSessionStore()
        val useCase = ObserveAppCatalogSnapshotsUseCase(
            settingsPreferencesStore = FakeSettingsPreferencesStore(),
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
            localDocumentRepository = FakeLocalDocumentRepository(),
            readerSessionStore = readerSessionStore,
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = listOf(downloadDataSource),
            ),
            appDispatchers = AppDispatchers(
                io = dispatcher,
                default = dispatcher,
            ),
        )
        useCase.observe().first()
        assertEquals(0, downloadDataSource.storedDocumentItemIdsCalls)
        val additionalEmissions = mutableListOf<AppCatalogSnapshot>()
        val collectionJob = backgroundScope.launch {
            useCase.observe()
                .drop(1)
                .collect(additionalEmissions::add)
        }
        advanceUntilIdle()

        readerSessionStore.seedProgress(
            com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = LocatorType.PAGE,
                page = 4,
                syncState = SyncState.CLEAN,
            ),
        )
        advanceUntilIdle()

        assertEquals(0, additionalEmissions.size)
        assertEquals(0, downloadDataSource.storedDocumentItemIdsCalls)

        collectionJob.cancel()
    }
}

private class CountingRemoteItemDownloadDataSource(
    override val sourceType: SourceType = SourceType.GRIMMORY,
    private val storedItemIds: Set<String> = emptySet(),
) : RemoteItemDownloadDataSource {
    var storedDocumentItemIdsCalls: Int = 0
        private set

    override fun storedDocumentUri(item: ReadableItem): Uri? = if (item.id in storedItemIds) {
        Uri.parse("file:///downloads/${item.id}.${item.format.name.lowercase()}")
    } else {
        null
    }

    override suspend fun downloadToLocalStorage(item: ReadableItem): Uri? = null

    override suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String> {
        storedDocumentItemIdsCalls += 1
        return items
            .filter { item -> item.id in storedItemIds }
            .mapTo(mutableSetOf(), ReadableItem::id)
    }

    override suspend fun deleteStoredDocument(item: ReadableItem): Boolean = false
}
