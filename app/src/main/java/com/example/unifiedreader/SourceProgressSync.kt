package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

interface SourceProgressSync {
    val sourceType: SourceType

    fun supports(
        item: ReadableItem,
    ): Boolean = item.sourceType == sourceType

    suspend fun syncItem(
        item: ReadableItem,
    )

    suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    )
}
