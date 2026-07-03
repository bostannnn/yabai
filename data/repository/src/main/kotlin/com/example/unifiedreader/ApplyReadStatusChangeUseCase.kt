package com.example.unifiedreader

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.supportsManualReadStatusAction
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject

data class ReadStatusChangeResult(
    val applied: Boolean,
)

class ApplyReadStatusChangeUseCase @Inject constructor(
    private val progressSyncStore: ProgressSyncStore,
    private val journalRepository: JournalRepository? = null,
) {
    suspend operator fun invoke(
        item: ReadableItem,
        markAsRead: Boolean,
    ): ReadStatusChangeResult {
        if (!item.supportsManualReadStatusAction()) {
            return ReadStatusChangeResult(applied = false)
        }
        val updatedAtEpochMillis = System.currentTimeMillis()
        applyLocalReadStatus(
            item = item,
            markAsRead = markAsRead,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        updateLocalJournalReadStatus(
            item = item,
            markAsRead = markAsRead,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        return ReadStatusChangeResult(applied = true)
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
        if (!markAsRead) {
            progressSyncStore.clearLocalReaderPosition(item)
        }
    }

    private suspend fun updateLocalJournalReadStatus(
        item: ReadableItem,
        markAsRead: Boolean,
        updatedAtEpochMillis: Long,
    ) {
        val repository = journalRepository ?: return
        if (markAsRead) {
            repository.recordReaderProgress(
                JournalProgressInput(
                    item = item,
                    page = item.pageCount,
                    percent = 1.0,
                    pageCount = item.pageCount,
                    isFinished = true,
                    occurredAtEpochMillis = updatedAtEpochMillis,
                ),
            )
        } else {
            repository.clearReadingEventsForItem(item.id)
        }
    }
}
