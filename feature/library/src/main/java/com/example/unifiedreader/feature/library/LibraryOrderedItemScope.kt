package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem

data class LibraryOrderedItemScope(
    val key: String,
    val itemIds: List<String>,
)

class LibraryOrderedItemContinuationResolver @javax.inject.Inject constructor() {
    fun nextUnreadAfter(
        currentItem: ReadableItem,
        orderedItems: List<ReadableItem>,
        progressByItemId: Map<String, ItemProgressSummary>,
    ): ReadableItem? {
        val currentIndex = orderedItems.indexOfFirst { item -> item.id == currentItem.id }
        if (currentIndex < 0) return null
        return orderedItems
            .asSequence()
            .drop(currentIndex + 1)
            .firstOrNull { item -> progressByItemId[item.id]?.isComplete != true }
    }
}
