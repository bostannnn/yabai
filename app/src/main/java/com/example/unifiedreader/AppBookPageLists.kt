package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryItemListMembership
import com.example.unifiedreader.feature.lists.bookListMemberships

/**
 * The book page's In your lists: which of the user's lists and the connected servers' lists hold a book.
 * Smart lists hold what their rules pick now, so they are worked out too, off the main thread (see
 * [rememberListsCatalogData]); the library composes this on every visit, which also keeps the Lists
 * tab's copy fresh.
 */
@Composable
internal fun rememberBookListMemberships(
    listsViewModel: AppListsViewModel,
    uiState: AppUiState,
): (ReadableItem) -> List<LibraryItemListMembership> {
    val catalogData = rememberListsCatalogData(listsViewModel, uiState, computeNowWhenMissing = false)
    // Read inside the callback, so only the book page asking redraws when the lists are worked out.
    return remember(catalogData) {
        { item -> catalogData.value?.let { data -> bookListMemberships(item.id, data.stored + data.shelves) }.orEmpty() }
    }
}
