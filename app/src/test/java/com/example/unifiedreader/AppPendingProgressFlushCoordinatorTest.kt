package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.RemoteJournalPusher
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.data.repository.SourceRemoteJournalPusher
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
        val coordinator = testPendingProgressFlushCoordinator(
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
    fun `dirty progress for unknown source attempts once`() = runTest {
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
        val coordinator = testPendingProgressFlushCoordinator(
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

        assertEquals(listOf(listOf(item.id)), sync.invocations)
        assertEquals(listOf(emptyList<SourceProgressSyncFailure>()), reportedFailures)
        assertEquals(RemoteSourceReachability.Reachable, remoteSourceConnectivityPolicy.reachabilityFor(SourceType.KAVITA))
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
        val coordinator = testPendingProgressFlushCoordinator(
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
        val coordinator = testPendingProgressFlushCoordinator(
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
        val coordinator = testPendingProgressFlushCoordinator(
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
        val coordinator = testPendingProgressFlushCoordinator(
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
    fun `journal push is skipped for source with pending progress failure`() = runTest {
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
        val remoteJournalPusher = RecordingRemoteJournalPusher()
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val coordinator = testPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(
                    listOf(
                        RecordingPendingSourceProgressSync(
                            sourceType = SourceType.KAVITA,
                            failure = IllegalStateException("Progress rejected"),
                            blocksDependentArtifacts = true,
                        ),
                    ),
                ),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            remoteJournalPusher = remoteJournalPusher,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertTrue(remoteJournalPusher.invocations.isEmpty())
        assertEquals("Progress rejected", reportedFailures.single().single().throwable.message)
    }

    @Test
    fun `journal push failure is reported so pending auto flush can retry`() = runTest {
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
        val remoteJournalPusher = RecordingRemoteJournalPusher(
            failuresBySource = mapOf(SourceType.KAVITA to listOf(IllegalStateException("Journal rejected"))),
        )
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val coordinator = testPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(
                    listOf(RecordingPendingSourceProgressSync(sourceType = SourceType.KAVITA)),
                ),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            remoteJournalPusher = remoteJournalPusher,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertEquals(listOf(SourceType.KAVITA to listOf(item.id)), remoteJournalPusher.invocations)
        assertEquals("Journal rejected", reportedFailures.single().single().throwable.message)
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
        val coordinator = testPendingProgressFlushCoordinator(
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

private fun testPendingProgressFlushCoordinator(
    readerSessionStore: FakeReaderSessionStore,
    readableItemRepository: ReadableItemRepository,
    flushPendingSourceProgressUseCase: FlushPendingSourceProgressUseCase,
    remoteItemLocalAvailability: RemoteItemLocalAvailability,
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    remoteJournalPusher: RemoteJournalPusher = NoOpRemoteJournalPusher,
): AppPendingProgressFlushCoordinator = AppPendingProgressFlushCoordinator(
    readerSessionStore = readerSessionStore,
    readableItemRepository = readableItemRepository,
    journalRepository = EmptyJournalRepository,
    flushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
        flushPendingSourceProgressUseCase = flushPendingSourceProgressUseCase,
        remoteJournalPusher = remoteJournalPusher,
        sourceJournalPushers = testSourceJournalPushers(remoteJournalPusher),
    ),
    remoteItemLocalAvailability = remoteItemLocalAvailability,
    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
)

private object EmptyJournalRepository : JournalRepository {
    override fun observeSnapshot() = flowOf(JournalSnapshot())

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(
        item: ReadableItem,
        rating: Int?,
        reviewText: String,
    ) = Unit

    override suspend fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    ) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit
}

private object NoOpRemoteJournalPusher : RemoteJournalPusher {
    override suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable> = emptyList()
}

private fun testSourceJournalPushers(
    remoteJournalPusher: RemoteJournalPusher,
): List<SourceRemoteJournalPusher> =
    SourceType.entries
        .filter { sourceType -> sourceType != SourceType.LOCAL }
        .map { sourceType ->
            object : SourceRemoteJournalPusher {
                override val sourceType: SourceType = sourceType
                override val supportedArtifacts: Set<RemoteSyncArtifact> = setOf(
                    RemoteSyncArtifact.JOURNAL_ENTRY,
                    RemoteSyncArtifact.READING_SESSION,
                )

                override suspend fun pushPendingJournalEntries(
                    items: List<ReadableItem>,
                    artifacts: Set<RemoteSyncArtifact>,
                ): List<Throwable> = remoteJournalPusher.pushPendingJournalEntries(sourceType, items, artifacts)
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
    private val blocksDependentArtifacts: Boolean = false,
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
                    blocksDependentArtifacts = blocksDependentArtifacts,
                ),
            )
        }
        return emptyList()
    }
}

private class RecordingRemoteJournalPusher(
    private val failuresBySource: Map<SourceType, List<Throwable>> = emptyMap(),
) : RemoteJournalPusher {
    val invocations = mutableListOf<Pair<SourceType, List<String>>>()

    override suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable> {
        invocations += sourceType to items.map(ReadableItem::id)
        return failuresBySource[sourceType].orEmpty()
    }
}
