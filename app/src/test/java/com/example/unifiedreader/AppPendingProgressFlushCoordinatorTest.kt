package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppPendingProgressFlushCoordinatorTest {
    @Test
    fun `catalog availability retries pending progress without new progress mutation`() = runTest {
        val item = remotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val readableItemRepository = FakeReadableItemRepository()
        val sync = RecordingPendingSourceProgressSync()
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy()
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { emptyList() },
            stateProvider = { AppUiState() },
            onSyncFailures = reportedFailures::add,
        )

        advanceTimeBy(1600)
        runCurrent()
        assertTrue(sync.invocations.isEmpty())

        remoteSourceConnectivityPolicy.markReachable(SourceType.GRIMMORY)
        readableItemRepository.emitItems(listOf(item))

        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)
        assertEquals(listOf(emptyList<SourceProgressSyncFailure>()), reportedFailures)
    }

    @Test
    fun `dirty progress for downloaded item waits until source is online`() = runTest {
        val item = downloadedRemotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val readableItemRepository = FakeReadableItemRepository(items = listOf(item))
        val sync = RecordingPendingSourceProgressSync(sourceType = SourceType.KAVITA)
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy()
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()
        advanceTimeBy(1600)
        runCurrent()

        assertTrue(sync.invocations.isEmpty())
        assertTrue(reportedFailures.isEmpty())

        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 5,
            ),
            syncState = SyncState.DIRTY,
            pageCount = item.pageCount,
            updatedAtEpochMillis = 456L,
        )
        assertEquals(456L, readerSessionStore.progressByItemIdFlow.value[item.id]?.updatedAtEpochMillis)
        readableItemRepository.emitItems(listOf(item))

        runCurrent()
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)
        assertEquals(listOf(emptyList<SourceProgressSyncFailure>()), reportedFailures)
    }

    @Test
    fun `reader close flush skips downloaded item when source is unavailable`() = runTest {
        val item = downloadedRemotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val sync = RecordingPendingSourceProgressSync(sourceType = SourceType.KAVITA)
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertTrue(sync.invocations.isEmpty())
        assertTrue(reportedFailures.isEmpty())
    }

    @Test
    fun `reader close flush skips streamed item when source is unavailable`() = runTest {
        val item = remotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val sync = RecordingPendingSourceProgressSync(sourceType = SourceType.GRIMMORY)
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.GRIMMORY)
        }
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertTrue(sync.invocations.isEmpty())
        assertTrue(reportedFailures.isEmpty())
    }

    @Test
    fun `dirty progress for unavailable source waits until source is online`() = runTest {
        val item = downloadedRemotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val readableItemRepository = FakeReadableItemRepository(items = listOf(item))
        val sync = RecordingPendingSourceProgressSync(sourceType = SourceType.KAVITA)
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
        }
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()
        advanceTimeBy(1600)
        runCurrent()

        assertTrue(sync.invocations.isEmpty())
        assertTrue(reportedFailures.isEmpty())

        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)
        assertEquals(listOf(emptyList<SourceProgressSyncFailure>()), reportedFailures)
    }

    @Test
    fun `non-network pending progress failure does not mark source unavailable`() = runTest {
        val item = downloadedRemotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy()
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(
                    listOf(
                        RecordingPendingSourceProgressSync(
                            sourceType = SourceType.KAVITA,
                            failure = IllegalStateException("Progress rejected"),
                        ),
                    ),
                ),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertEquals(RemoteSourceReachability.Reachable, remoteSourceConnectivityPolicy.reachabilityFor(SourceType.KAVITA))
        assertEquals("Progress rejected", reportedFailures.single().single().throwable.message)
    }

    @Test
    fun `downloaded item reachability failure is queued without reporting sync notice`() = runTest {
        val item = downloadedRemotePendingItem()
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = item,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = item.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy()
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(
                    listOf(
                        RecordingPendingSourceProgressSync(
                            sourceType = SourceType.KAVITA,
                            failure = IOException("Connect timeout has expired"),
                        ),
                    ),
                ),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(
                listOf(
                    FakeRemoteDownloadDataSource(
                        sourceType = SourceType.KAVITA,
                        storedItemIds = mutableSetOf(item.id),
                    ),
                ),
            ),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertEquals(RemoteSourceReachability.Unavailable, remoteSourceConnectivityPolicy.reachabilityFor(SourceType.KAVITA))
        assertEquals(listOf(emptyList<SourceProgressSyncFailure>()), reportedFailures)
    }
}

private fun remotePendingItem(): ReadableItem = ReadableItem(
    id = "grimmory:item:pending",
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    remoteId = "pending",
    title = "Pending Book",
    format = FormatType.EPUB,
    pageCount = 12,
)

private fun downloadedRemotePendingItem(): ReadableItem = ReadableItem(
    id = "kavita:item:pending",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:1",
    remoteId = "pending",
    title = "Pending Comic",
    format = FormatType.CBZ,
    pageCount = 12,
)

private class RecordingPendingSourceProgressSync(
    override val sourceType: SourceType = SourceType.GRIMMORY,
    private val failure: Throwable? = null,
) : SourceProgressSync {
    val invocations = mutableListOf<List<String>>()

    override suspend fun syncItem(
        item: ReadableItem,
    ) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        invocations += items.map(ReadableItem::id)
        failure?.let { throwable ->
            return listOf(
                SourceProgressSyncFailure(
                    sourceType = sourceType,
                    throwable = throwable,
                ),
            )
        }
        return emptyList()
    }
}
