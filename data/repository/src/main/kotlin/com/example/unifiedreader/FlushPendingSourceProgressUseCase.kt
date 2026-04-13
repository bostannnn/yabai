package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import javax.inject.Inject

class FlushPendingSourceProgressUseCase @Inject constructor(
    private val sourceProgressSyncRegistry: SourceProgressSyncRegistry,
) {
    suspend operator fun invoke(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> = sourceProgressSyncRegistry.pushPendingProgress(items)
}
