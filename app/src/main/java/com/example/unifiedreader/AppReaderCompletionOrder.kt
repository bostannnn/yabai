package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem

/**
 * The order Read next follows for [currentItemId]: the user's list the book was opened from while
 * it holds the book, else a picked Kavita reading list; empty means the book's series.
 */
internal fun AppUiState.readerCompletionOrder(currentItemId: String): List<String> =
    readerListContext?.itemIds?.takeIf { ids -> currentItemId in ids }
        ?: selectedKavitaReadingListItemIds

/**
 * A list's candidates for Read next: what the catalog tables [found], plus the list's books from
 * [fallbackItems] they don't hold (imported files live in their own table).
 */
internal fun listCompletionCandidates(
    found: List<ReadableItem>,
    fallbackItems: List<ReadableItem>,
    orderedItemIds: List<String>,
): List<ReadableItem> {
    val foundIds = found.mapTo(HashSet(), ReadableItem::id)
    val wanted = orderedItemIds.toHashSet()
    return found + fallbackItems.filter { item -> item.id in wanted && item.id !in foundIds }
}
