package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryListScopeTop
import com.example.unifiedreader.feature.library.libraryCollectionTop
import com.example.unifiedreader.feature.library.libraryReadingListTop

/**
 * The top of the open Kavita reading list or collection: its description and counts, and for a
 * list the one to read next. Until the list's items arrive it counts from the list's summary and
 * offers no card, so the card commits together with the grid. Null outside a list, and inside a
 * series opened from one (the series has its own top).
 */
internal fun AppUiState.kavitaListScopeTop(): LibraryListScopeTop? {
    if (isSeriesBrowseActive) return null
    val readingLists = kavitaReadingLists
    if (isKavitaReadingListActive) {
        val summary = readingLists.summaries.firstOrNull { it.id == readingLists.selectedReadingListId }
        if (visibleItems.isEmpty()) {
            val count = summary?.itemCount ?: 0
            return LibraryListScopeTop(
                description = libraryReadingListTop(summary?.summary, emptyList(), emptyMap()).description,
                countLabel = if (count == 1) "1 item" else "$count items",
            )
        }
        return libraryReadingListTop(summary?.summary, visibleItems, displayProgressByItemId)
    }
    if (isKavitaCollectionActive) {
        val collection = readingLists.collections.firstOrNull { it.id == readingLists.selectedCollectionId }
        return libraryCollectionTop(
            description = collection?.summary,
            seriesCount = visibleItems.size.takeIf { it > 0 } ?: collection?.itemCount ?: 0,
        )
    }
    return null
}
