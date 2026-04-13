package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import javax.inject.Inject

class SyncItemProgressUseCase @Inject constructor(
    private val sourceProgressSyncRegistry: SourceProgressSyncRegistry,
) {
    fun hasSyncFor(
        item: ReadableItem,
    ): Boolean = sourceProgressSyncRegistry.syncFor(item) != null

    suspend operator fun invoke(
        item: ReadableItem,
    ): Result<Unit> = sourceProgressSyncRegistry.syncItem(item)
}
