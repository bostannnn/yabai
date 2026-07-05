package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.RemoteJournalPusher
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.journal.JournalSnapshot
import kotlinx.coroutines.CompletableDeferred
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
class AppProgressSyncStartupRetryCoordinatorTest {
    @Test
    fun `startup retry pre-record suppresses dirty observer duplicate while flush is in flight`() = runTest {
        val item = startupRemoteItem()
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
        val journalRepository = FakeJournalRepository()
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }
        val autoFlushPolicy = AppRemoteArtifactAutoFlushPolicy { 1_000L }
        val sync = BlockingStartupSourceProgressSync(sourceType = SourceType.KAVITA)
        val flushUseCase = testFlushPendingRemoteArtifactsUseCase(sync)

        testStartupRetryCoordinator(
            readableItemRepository = readableItemRepository,
            readerSessionStore = readerSessionStore,
            journalRepository = journalRepository,
            flushPendingRemoteArtifactsUseCase = flushUseCase,
            remoteSourceConnectivityPolicy = connectivityPolicy,
            autoFlushPolicy = autoFlushPolicy,
        ).retryAfterHydration(
            scope = backgroundScope,
            onSyncFailures = {},
        )
        runCurrent()
        assertEquals(listOf(listOf(item.id)), sync.invocations)

        AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            journalRepository = journalRepository,
            flushPendingRemoteArtifactsUseCase = flushUseCase,
            remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
            remoteSourceConnectivityPolicy = connectivityPolicy,
            autoFlushPolicy = autoFlushPolicy,
        ).observeDirtyProgress(
            scope = backgroundScope,
            catalogItemsProvider = { listOf(item) },
            stateProvider = { AppUiState() },
            onSyncFailures = {},
        )

        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)

        sync.release()
        runCurrent()
    }

    @Test
    fun `startup retry skips pending source already recorded by automatic policy`() = runTest {
        val item = startupRemoteItem()
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
        val journalRepository = FakeJournalRepository()
        val autoFlushPolicy = AppRemoteArtifactAutoFlushPolicy { 1_000L }
        autoFlushPolicy.recordAutomaticFlushAttempt(
            pendingKeys = pendingRemoteArtifactAutoFlushKeys(
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                journalSnapshot = JournalSnapshot(),
            ),
            attemptedItems = listOf(item),
            failures = emptyList(),
        )
        val sync = BlockingStartupSourceProgressSync(sourceType = SourceType.KAVITA)

        testStartupRetryCoordinator(
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            readerSessionStore = readerSessionStore,
            journalRepository = journalRepository,
            flushPendingRemoteArtifactsUseCase = testFlushPendingRemoteArtifactsUseCase(sync),
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
                markReachable(SourceType.KAVITA)
            },
            autoFlushPolicy = autoFlushPolicy,
        ).retryAfterHydration(
            scope = backgroundScope,
            onSyncFailures = {},
        )
        runCurrent()

        assertTrue(sync.invocations.isEmpty())
    }
}

private fun testStartupRetryCoordinator(
    readableItemRepository: FakeReadableItemRepository,
    readerSessionStore: FakeReaderSessionStore,
    journalRepository: FakeJournalRepository,
    flushPendingRemoteArtifactsUseCase: FlushPendingRemoteArtifactsUseCase,
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    autoFlushPolicy: AppRemoteArtifactAutoFlushPolicy,
): AppProgressSyncStartupRetryCoordinator = AppProgressSyncStartupRetryCoordinator(
    readableItemRepository = readableItemRepository,
    readerSessionStore = readerSessionStore,
    journalRepository = journalRepository,
    remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
    flushPendingRemoteArtifactsUseCase = flushPendingRemoteArtifactsUseCase,
    remoteItemLocalAvailability = RemoteItemLocalAvailability(emptyList()),
    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    autoFlushPolicy = autoFlushPolicy,
)

private fun testFlushPendingRemoteArtifactsUseCase(
    sync: SourceProgressSync,
): FlushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
    flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
        sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
    ),
    remoteJournalPusher = NoOpStartupRemoteJournalPusher,
    sourceJournalPushers = emptyList(),
)

private object NoOpStartupRemoteJournalPusher : RemoteJournalPusher {
    override suspend fun pushPendingJournalEntries(
        sourceType: SourceType,
        items: List<ReadableItem>,
        artifacts: Set<RemoteSyncArtifact>,
    ): List<Throwable> = emptyList()
}

private class BlockingStartupSourceProgressSync(
    override val sourceType: SourceType,
) : SourceProgressSync {
    private val continuePush = CompletableDeferred<Unit>()
    val invocations = mutableListOf<List<String>>()

    override suspend fun syncItem(
        item: ReadableItem,
    ) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        val sourceItems = items.filter(::supports)
        if (sourceItems.isEmpty()) return emptyList()
        invocations += sourceItems.map(ReadableItem::id)
        continuePush.await()
        return emptyList()
    }

    fun release() {
        continuePush.complete(Unit)
    }
}

private fun startupRemoteItem(): ReadableItem = ReadableItem(
    id = "kavita:item:startup-pending",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:1",
    remoteId = "startup-pending",
    title = "Startup Pending Comic",
    format = FormatType.CBZ,
    pageCount = 12,
)
