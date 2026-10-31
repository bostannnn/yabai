package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.feature.lists.AddToListSheet
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.feature.lists.toUserList
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.LocalAppToastState

/**
 * Add to list for [items] (a book's page, or a selection in the library), with New list, which
 * puts them into the list it makes. Grimmory's shelves, and making one, show only while Grimmory
 * is connected and every one of [items] is a Grimmory book; [libraryItems] says which books are
 * on each shelf.
 */
@Composable
internal fun AddToListHost(
    items: List<ReadableItem>,
    libraryItems: List<ReadableItem>,
    grimmoryConnected: Boolean,
    kavitaConnected: Boolean,
    listsViewModel: AppListsViewModel,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val lists by listsViewModel.lists.collectAsStateWithLifecycle()
    val storedShelves by listsViewModel.shelves.collectAsStateWithLifecycle()
    // A server's lists show when every book going in is that server's (Kavita's: issues, not whole series).
    val server = when {
        items.isEmpty() -> null
        grimmoryConnected && items.all { item -> item.sourceType == SourceType.GRIMMORY } -> SourceType.GRIMMORY
        kavitaConnected && items.all { item -> item.sourceType == SourceType.KAVITA && !item.isKavitaSeriesContainer() } -> SourceType.KAVITA
        else -> null
    }
    LaunchedEffect(server) { if (server != null) listsViewModel.refreshShelves() }
    val itemsById = remember(libraryItems) { libraryItems.associateBy(ReadableItem::id) }
    SideEffect { listsViewModel.catalog = itemsById }
    val shelves = remember(storedShelves, itemsById, server) {
        storedShelves.orEmpty().filter { shelf -> shelf.sourceType == server }.map { shelf -> shelf.toUserList(itemsById) }
    }
    val onShelfFailure = rememberShelfFailureToast()
    var isNewListOpen by rememberSaveable { mutableStateOf(false) }
    AddToListSheet(
        items = items,
        lists = lists,
        serverLists = shelves,
        wantToReadNote = "Also on Kavita's Want to read".takeIf {
            kavitaConnected && items.any { item -> item.sourceType == SourceType.KAVITA }
        },
        onSetInList = { list, inList -> listsViewModel.setInList(list, items, inList, onFailure = onShelfFailure) },
        onNewList = { isNewListOpen = true },
        onDismiss = onDismiss,
        onBack = onBack,
    )
    if (isNewListOpen) {
        val goesIn = items.singleOrNull()?.let { item -> "${item.libraryBookTitle()} goes into it" } ?: "${items.size} books go into it"
        NewListDialog(
            subtitle = if (server != null) "$goesIn." else "$goesIn, in your order.",
            onCreate = { name, chosen ->
                isNewListOpen = false
                if (chosen != null) {
                    listsViewModel.createServerList(chosen.sourceType, name, items, onFailure = onShelfFailure)
                } else {
                    listsViewModel.createList(name, items)
                }
            },
            onDismiss = { isNewListOpen = false },
            servers = newListServers(setOfNotNull(server)),
        )
    }
}

/** Shows what a shelf's server said when it didn't take a change, as an error toast. */
@Composable
internal fun rememberShelfFailureToast(): (String) -> Unit {
    val toasts = LocalAppToastState.current
    return remember(toasts) { { message -> toasts?.show(AppToastMessage(text = message, icon = AppToastIcon.Error)) } }
}
