package com.example.unifiedreader

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.feature.lists.SmartListFilter
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.LocalAppToastState

/**
 * Save as list from Filters: asks for a name, then keeps [filters] (this library's, [uiState]'s
 * source) as a smart list, and offers to open it.
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
            listsViewModel.createSmartList(name, SmartListFilter(uiState.selectedSource, filters)) { listId ->
                toasts?.show(
                    AppToastMessage(
                        text = "Saved to Lists",
                        icon = AppToastIcon.Added,
                        actionLabel = "Open",
                        onAction = { onOpenList(listId) },
                    ),
                )
            }
        },
        onDismiss = onDismiss,
    )
}
