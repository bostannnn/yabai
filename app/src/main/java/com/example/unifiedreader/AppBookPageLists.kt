package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryItemListMembership
import com.example.unifiedreader.feature.lists.bookListMemberships
import com.example.unifiedreader.feature.lists.toUserList

/** The book page's In your lists: which of the user's lists and the connected servers' lists hold a book. */
@Composable
internal fun rememberBookListMemberships(
    listsViewModel: AppListsViewModel,
    uiState: AppUiState,
): (ReadableItem) -> List<LibraryItemListMembership> {
    val stored by listsViewModel.lists.collectAsStateWithLifecycle()
    val storedShelves by listsViewModel.shelves.collectAsStateWithLifecycle()
    val connected = connectedListServers(uiState)
    val itemsById = remember(uiState.items) { uiState.items.associateBy(ReadableItem::id) }
    val lists = remember(stored, storedShelves, itemsById, connected) {
        stored + storedShelves.orEmpty().filter { shelf -> shelf.sourceType in connected }.map { shelf -> shelf.toUserList(itemsById) }
    }
    return remember(lists) { { item -> bookListMemberships(item.id, lists) } }
}
