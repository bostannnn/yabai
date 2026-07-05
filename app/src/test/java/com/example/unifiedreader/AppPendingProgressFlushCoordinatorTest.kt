package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
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
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalMediaType
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalReadingSession
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
    fun `dirty progress for unknown source waits until source is online`() = runTest {
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

        assertTrue(sync.invocations.isEmpty())
        assertTrue(reportedFailures.isEmpty())
        assertEquals(RemoteSourceReachability.Unknown, remoteSourceConnectivityPolicy.reachabilityFor(SourceType.KAVITA))

        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
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
    fun `unavailable source exposes queued pending remote sync without reporting failure`() = runTest {
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
            journalRepository = StaticJournalRepository(
                JournalSnapshot(
                    entries = listOf(testJournalEntry(item, SyncState.FAILED)),
                    sessions = listOf(testReadingSession(item, SyncState.DIRTY)),
                ),
            ),
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()
        val summaries = mutableListOf<AppPendingRemoteSyncSummary>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = reportedFailures::add,
            onPendingRemoteSyncSummaryChanged = summaries::add,
        )

        advanceTimeBy(1600)
        runCurrent()

        assertTrue(sync.invocations.isEmpty())
        assertTrue(reportedFailures.isEmpty())
        val group = summaries.last().groups.single()
        assertEquals(SourceType.KAVITA, group.sourceType)
        assertEquals(SourceConnectivityState.Offline, group.connectivityState)
        assertEquals(2, group.queuedCount)
        assertEquals(1, group.failedCount)
        assertEquals(
            setOf(
                RemoteSyncArtifact.PROGRESS,
                RemoteSyncArtifact.JOURNAL_ENTRY,
                RemoteSyncArtifact.READING_SESSION,
            ),
            group.items.mapTo(mutableSetOf(), AppPendingRemoteSyncItem::artifact),
        )
    }

    @Test
    fun `explicit retry probe failure annotates queued pending remote sync`() = runTest {
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
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.KAVITA)
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
        )
        val summaries = mutableListOf<AppPendingRemoteSyncSummary>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = {},
            onPendingRemoteSyncSummaryChanged = summaries::add,
        )

        advanceTimeBy(1600)
        runCurrent()
        coordinator.recordRemoteSyncAttempt(
            attemptedSourceTypes = setOf(SourceType.KAVITA),
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = SourceType.KAVITA,
                    throwable = RemoteSourceUnavailableException(SourceType.KAVITA),
                ),
            ),
        )

        val lastAttempt = summaries.last().groups.single().lastAttempt
        assertEquals(
            "Kavita local network cannot be accessed. Only downloaded or cached items can be opened.",
            lastAttempt?.errorMessage,
        )
        assertTrue(lastAttempt?.attemptedAtEpochMillis != null)
    }

    @Test
    fun `online pending progress failure is reported and annotates sync queue`() = runTest {
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
                        ),
                    ),
                ),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()
        val summaries = mutableListOf<AppPendingRemoteSyncSummary>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = reportedFailures::add,
            onPendingRemoteSyncSummaryChanged = summaries::add,
        )

        advanceTimeBy(1600)
        runCurrent()

        assertEquals("Progress rejected", reportedFailures.single().single().throwable.message)
        val lastAttempt = summaries.last().groups.single().lastAttempt
        assertEquals("Progress rejected", lastAttempt?.errorMessage)
        assertTrue(lastAttempt?.attemptedAtEpochMillis != null)
    }

    @Test
    fun `online pending progress failure is not auto retried when marked failed`() = runTest {
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
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val sync = MarkingFailedPendingSourceProgressSync(
            sourceType = SourceType.KAVITA,
            readerSessionStore = readerSessionStore,
            failure = IllegalStateException("Progress rejected"),
        )
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
        val summaries = mutableListOf<AppPendingRemoteSyncSummary>()

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = reportedFailures::add,
            onPendingRemoteSyncSummaryChanged = summaries::add,
        )

        advanceTimeBy(1600)
        runCurrent()
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)
        assertEquals(1, reportedFailures.size)
        assertEquals(SyncState.FAILED, readerSessionStore.progressByItemIdFlow.value[item.id]?.syncState)
        assertEquals(1, summaries.last().groups.single().failedCount)
    }

    @Test
    fun `changed automatic observation waits for duplicate throttle before retrying source`() = runTest {
        val item = downloadedRemotePendingItem()
        val clock = TestPendingAutoFlushClock()
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
            markReachable(SourceType.KAVITA)
        }
        val coordinator = testPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            autoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(clock::now),
        )

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = {},
        )

        advanceTimeBy(1600)
        runCurrent()
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
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)

        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS)
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 6,
            ),
            syncState = SyncState.DIRTY,
            pageCount = item.pageCount,
            updatedAtEpochMillis = 789L,
        )
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id), listOf(item.id)), sync.invocations)
    }

    @Test
    fun `offline failed automatic retry waits for failure backoff after source returns online`() = runTest {
        val item = downloadedRemotePendingItem()
        val clock = TestPendingAutoFlushClock()
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
        val sync = RecordingPendingSourceProgressSync(
            sourceType = SourceType.KAVITA,
            failure = IOException("Connect timeout has expired"),
        )
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val coordinator = testPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            autoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(clock::now),
        )

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = {},
        )

        advanceTimeBy(1600)
        runCurrent()
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)

        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_FAILURE_BACKOFF_MILLIS)
        remoteSourceConnectivityPolicy.markChecking(SourceType.KAVITA)
        advanceTimeBy(1600)
        runCurrent()
        remoteSourceConnectivityPolicy.markReachable(SourceType.KAVITA)
        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id), listOf(item.id)), sync.invocations)
    }

    @Test
    fun `explicit flush bypasses automatic failure backoff`() = runTest {
        val item = downloadedRemotePendingItem()
        val clock = TestPendingAutoFlushClock()
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
        val sync = RecordingPendingSourceProgressSync(
            sourceType = SourceType.KAVITA,
            failure = IllegalStateException("Progress rejected"),
        )
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val coordinator = testPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            autoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(clock::now),
        )

        coordinator.observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = {},
        )

        advanceTimeBy(1600)
        runCurrent()
        coordinator.flushPendingProgress(
            catalogItems = listOf(item),
            selectedItem = item,
            scope = backgroundScope,
            onSyncFailures = {},
        )
        runCurrent()

        assertEquals(listOf(listOf(item.id), listOf(item.id)), sync.invocations)
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

    @Test
    fun `mixed catalog suppresses downloaded pending reachability failure using pending items only`() = runTest {
        val pendingItem = downloadedRemotePendingItem()
        val cleanStreamedItem = pendingItem.copy(
            id = "kavita:item:clean-streamed",
            remoteId = "clean-streamed",
            title = "Clean Streamed Comic",
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = pendingItem,
                locator = ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 4,
                ),
                syncState = SyncState.DIRTY,
                pageCount = pendingItem.pageCount,
                updatedAtEpochMillis = 123L,
            )
        }
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val sync = RecordingPendingSourceProgressSync(
            sourceType = SourceType.KAVITA,
            failure = IOException("Connect timeout has expired"),
        )
        val coordinator = testPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(pendingItem, cleanStreamedItem)),
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
            remoteItemLocalAvailability = RemoteItemLocalAvailability(
                listOf(
                    FakeRemoteDownloadDataSource(
                        sourceType = SourceType.KAVITA,
                        storedItemIds = mutableSetOf(pendingItem.id),
                    ),
                ),
            ),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val reportedFailures = mutableListOf<List<SourceProgressSyncFailure>>()

        coordinator.flushPendingProgress(
            catalogItems = listOf(pendingItem, cleanStreamedItem),
            selectedItem = pendingItem,
            scope = backgroundScope,
            onSyncFailures = reportedFailures::add,
        )

        runCurrent()

        assertEquals(listOf(listOf(pendingItem.id)), sync.invocations)
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
    journalRepository: JournalRepository = EmptyJournalRepository,
    autoFlushPolicy: AppRemoteArtifactAutoFlushPolicy = AppRemoteArtifactAutoFlushPolicy(),
): AppPendingProgressFlushCoordinator = AppPendingProgressFlushCoordinator(
    readerSessionStore = readerSessionStore,
    readableItemRepository = readableItemRepository,
    journalRepository = journalRepository,
    flushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
        flushPendingSourceProgressUseCase = flushPendingSourceProgressUseCase,
        remoteJournalPusher = remoteJournalPusher,
        sourceJournalPushers = testSourceJournalPushers(remoteJournalPusher),
    ),
    remoteItemLocalAvailability = remoteItemLocalAvailability,
    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    autoFlushPolicy = autoFlushPolicy,
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

private class StaticJournalRepository(
    private val snapshot: JournalSnapshot,
) : JournalRepository {
    override fun observeSnapshot() = flowOf(snapshot)

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

private fun testJournalEntry(
    item: ReadableItem,
    syncState: SyncState,
): ItemJournalEntry = ItemJournalEntry(
    itemId = item.id,
    sourceType = item.sourceType,
    libraryId = item.libraryId,
    itemTitle = item.title,
    coverUrl = item.coverUrl,
    itemKind = item.kind,
    format = item.format,
    rating = 4,
    reviewText = "Worth keeping.",
    reviewedAtEpochMillis = 200L,
    updatedAtEpochMillis = 200L,
    syncState = syncState,
)

private fun testReadingSession(
    item: ReadableItem,
    syncState: SyncState,
): JournalReadingSession = JournalReadingSession(
    id = "${item.id}:session:1",
    itemId = item.id,
    sourceType = item.sourceType,
    libraryId = item.libraryId,
    itemTitle = item.title,
    coverUrl = item.coverUrl,
    itemKind = item.kind,
    format = item.format,
    mediaType = JournalMediaType.COMIC,
    startedAtEpochMillis = 300L,
    endedAtEpochMillis = 400L,
    durationMillis = 100L,
    fromPercent = null,
    toPercent = null,
    fromPage = 1,
    toPage = 4,
    pageCount = item.pageCount,
    syncState = syncState,
)

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

private class TestPendingAutoFlushClock(
    private var nowEpochMillis: Long = 1_000L,
) {
    fun now(): Long = nowEpochMillis

    fun advance(millis: Long) {
        nowEpochMillis += millis
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

private class MarkingFailedPendingSourceProgressSync(
    override val sourceType: SourceType,
    private val readerSessionStore: FakeReaderSessionStore,
    private val failure: Throwable,
) : SourceProgressSync {
    val invocations = mutableListOf<List<String>>()

    override suspend fun syncItem(
        item: ReadableItem,
    ) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        invocations += items.map(ReadableItem::id)
        items.forEach { item ->
            val progress = readerSessionStore.progressByItemIdFlow.value[item.id] ?: return@forEach
            readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = progress.copy(syncState = SyncState.FAILED),
            )
        }
        return listOf(
            SourceProgressSyncFailure(
                sourceType = sourceType,
                throwable = failure,
            ),
        )
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
