package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import javax.inject.Inject

class FlushPendingProgressOnStartupUseCase @Inject constructor(
    private val progressSyncStore: ProgressSyncStore,
    private val flushPendingSourceProgressUseCase: FlushPendingSourceProgressUseCase,
) {
    suspend operator fun invoke(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        val pendingItemIds = progressSyncStore.currentProgressByItemId().values
            .filter(ProgressSyncSnapshot::isPendingSync)
            .mapTo(mutableSetOf(), ProgressSyncSnapshot::itemId)
        if (pendingItemIds.isEmpty()) return emptyList()
        val pendingItems = items.filter { item -> item.id in pendingItemIds }
        if (pendingItems.isEmpty()) return emptyList()
        return flushPendingSourceProgressUseCase(pendingItems)
    }
}

private fun ProgressSyncSnapshot.isPendingSync(): Boolean =
    syncState == SyncState.DIRTY || syncState == SyncState.FAILED
