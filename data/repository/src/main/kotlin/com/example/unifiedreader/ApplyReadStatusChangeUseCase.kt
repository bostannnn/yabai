package com.example.unifiedreader

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.supportsManualReadStatusAction
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import javax.inject.Inject

data class ReadStatusChangeResult(
    val applied: Boolean,
    val syncFailures: List<SourceProgressSyncFailure> = emptyList(),
)

class ApplyReadStatusChangeUseCase @Inject constructor(
    private val progressSyncStore: ProgressSyncStore,
    private val flushPendingSourceProgressUseCase: FlushPendingSourceProgressUseCase,
) {
    suspend operator fun invoke(
        item: ReadableItem,
        markAsRead: Boolean,
    ): ReadStatusChangeResult {
        if (!item.supportsManualReadStatusAction()) {
            return ReadStatusChangeResult(applied = false)
        }
        applyLocalReadStatus(
            item = item,
            markAsRead = markAsRead,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        return ReadStatusChangeResult(
            applied = true,
            syncFailures = flushPendingSourceProgressUseCase(listOf(item)),
        )
    }

    private fun applyLocalReadStatus(
        item: ReadableItem,
        markAsRead: Boolean,
        updatedAtEpochMillis: Long,
    ) {
        progressSyncStore.saveProgress(
            item = item,
            locator = ProgressSyncLocator(
                locatorType = LocatorType.PERCENT,
                percent = if (markAsRead) 1.0 else 0.0,
            ),
            isFinished = markAsRead,
            isExplicitUnreadReset = !markAsRead,
            syncState = SyncState.DIRTY,
            pageCount = item.pageCount,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}
