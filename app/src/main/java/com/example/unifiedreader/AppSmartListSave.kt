package com.example.unifiedreader

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.core.model.toListRules
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
) {
    val toasts = LocalAppToastState.current
    NewListDialog(
        subtitle = "It keeps these filters, so it changes as your library does.",
        onCreate = { name, _ ->
            onDismiss()
            val spec = SmartListSpec(rules = filters.toListRules(uiState.selectedSource))
            listsViewModel.createSmartList(name, spec, UserListOrder.DateAdded, onCreated = { listId ->
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
