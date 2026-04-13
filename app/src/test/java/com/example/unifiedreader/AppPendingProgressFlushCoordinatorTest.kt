package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
        val coordinator = AppPendingProgressFlushCoordinator(
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
            flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                sourceProgressSyncRegistry = SourceProgressSyncRegistry(listOf(sync)),
            ),
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

        readableItemRepository.emitItems(listOf(item))

        advanceTimeBy(1600)
        runCurrent()

        assertEquals(listOf(listOf(item.id)), sync.invocations)
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

private class RecordingPendingSourceProgressSync : SourceProgressSync {
    override val sourceType: SourceType = SourceType.GRIMMORY
    val invocations = mutableListOf<List<String>>()

    override suspend fun syncItem(
        item: ReadableItem,
    ) = Unit

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        invocations += items.map(ReadableItem::id)
        return emptyList()
    }
}
