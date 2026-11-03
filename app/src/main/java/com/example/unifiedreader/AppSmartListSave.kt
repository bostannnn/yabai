package com.example.unifiedreader

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.core.model.libraryPlaceTags
import com.example.unifiedreader.core.model.libraryTimeTags
import com.example.unifiedreader.core.model.toListRules
import com.example.unifiedreader.feature.lists.SmartListFilter
import com.example.unifiedreader.feature.lists.SmartListSpec
import com.example.unifiedreader.feature.lists.UserListOrder
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.LocalAppToastState

/**
 * Save as list from Filters: asks for a name, then keeps [filters] (this library's, [uiState]'s
 * source) as a smart list's rules, the source among them, and offers to open it.
 */
@Composable
internal fun SaveFiltersAsListDialog(
    filters: LibraryFilterState,
    uiState: AppUiState,
    listsViewModel: AppListsViewModel,
    onOpenList: (String) -> Unit,
    onDismiss: () -> Unit,
    // Made from Lists' New smart list: the new list opens at once instead of a toast offering it.
    onCreated: ((String) -> Unit)? = null,
) {
    val toasts = LocalAppToastState.current
    NewListDialog(
        subtitle = "It keeps these filters, so it changes as your library does.",
        onCreate = { name, _ ->
            onDismiss()
            val spec = smartSpecFromFilters(filters, uiState)
            listsViewModel.createSmartList(name, spec, UserListOrder.DateAdded, onCreated = { listId ->
                if (onCreated != null) {
                    onCreated(listId)
                    return@createSmartList
                }
                toasts?.show(
                    AppToastMessage(
                        text = "Saved to Lists",
                        icon = AppToastIcon.Added,
                        actionLabel = "Open",
                        onAction = { onOpenList(listId) },
                    ),
                )
            })
        },
        onDismiss = onDismiss,
    )
}

/**
 * A smart list from [filters] in [uiState]'s library: the rules they make (the source among them,
 * places and times as their tags) and the picks themselves, so Filters can change them later.
 */
internal fun smartSpecFromFilters(filters: LibraryFilterState, uiState: AppUiState): SmartListSpec = SmartListSpec(
    rules = filters.toListRules(
        sourceType = uiState.selectedSource,
        placeTags = { place -> libraryPlaceTags(place, uiState.sourceItems) },
        timeTags = { time -> libraryTimeTags(time, uiState.sourceItems) },
    ),
    filter = SmartListFilter(uiState.selectedSource, filters),
)
