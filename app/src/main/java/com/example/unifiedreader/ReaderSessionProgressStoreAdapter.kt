package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.feature.reader.comics.clearComicReaderPosition
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderSessionProgressStoreAdapter(
    private val readerSessionStore: ReaderSessionStore,
) : ProgressSyncStore {
    override val isHydratedFlow: Flow<Boolean> = readerSessionStore.isHydratedFlow

    override val progressByItemIdFlow: Flow<Map<String, ProgressSyncSnapshot>> =
        readerSessionStore.progressByItemIdFlow.map { progressByItemId ->
            progressByItemId.mapValues { (_, snapshot) -> snapshot.toProgressSyncSnapshot() }
        }

    override fun currentProgressByItemId(): Map<String, ProgressSyncSnapshot> =
        readerSessionStore.progressByItemIdFlow.value
            .mapValues { (_, snapshot) -> snapshot.toProgressSyncSnapshot() }

    override fun getProgress(
        item: ReadableItem,
    ): ProgressSyncSnapshot? = readerSessionStore.getProgress(item)?.toProgressSyncSnapshot()

    override fun saveProgress(
        item: ReadableItem,
        locator: ProgressSyncLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        readerSessionStore.saveProgress(
            item = item,
            locator = locator.toReaderLocator(),
            isFinished = isFinished,
            isExplicitUnreadReset = isExplicitUnreadReset,
            syncState = syncState,
            pageCount = pageCount,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    override suspend fun replaceProgressIfCurrent(
        expected: ProgressSyncSnapshot,
        replacement: ProgressSyncSnapshot?,
    ): Boolean = readerSessionStore.replaceProgressIfCurrent(
        expected = expected.toReaderProgressSnapshot(),
        replacement = replacement?.toReaderProgressSnapshot(),
    )

    override fun clearProgress(
        itemId: String,
    ) {
        readerSessionStore.clearProgress(itemId)
    }

    override fun clearLocalReaderPosition(
        item: ReadableItem,
    ) {
        val replacement = when (item.kind) {
            ReadableItemKind.COMIC -> clearComicReaderPosition(readerSessionStore.getSession(item))
            ReadableItemKind.BOOK -> readerSessionStore.getSession(item)
        }
        if (item.kind == ReadableItemKind.COMIC) {
            readerSessionStore.replaceSession(item.id, replacement)
        }
    }

    override fun clearItem(
        itemId: String,
    ) {
        readerSessionStore.clearItem(itemId)
    }
}

private fun ReaderProgressSnapshot.toProgressSyncSnapshot(): ProgressSyncSnapshot = ProgressSyncSnapshot(
    itemId = itemId,
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
    isFinished = isFinished,
    isExplicitUnreadReset = isExplicitUnreadReset,
    syncState = syncState,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ProgressSyncSnapshot.toReaderProgressSnapshot(): ReaderProgressSnapshot = ReaderProgressSnapshot(
    itemId = itemId,
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
    isFinished = isFinished,
    isExplicitUnreadReset = isExplicitUnreadReset,
    syncState = syncState,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ProgressSyncLocator.toReaderLocator(): ReaderLocator = ReaderLocator(
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
)
