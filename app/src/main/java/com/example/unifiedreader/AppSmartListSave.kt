package com.example.unifiedreader

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.lists.NewListServer
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
 * source) as a smart list's rules, the source among them, and offers to open it. In Grimmory, while
 * it keeps magic shelves and the filters all make rules it can hold, Keep it also offers On Grimmory.
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
    val onFailure = rememberShelfFailureToast()
    val spec = smartSpecFromFilters(filters, uiState)
    val shelfRules = spec.rules.forMagicShelf()
    val servers = listOfNotNull(
        NewListServer(SourceType.GRIMMORY, MagicShelfLine).takeIf {
            uiState.selectedSource == SourceType.GRIMMORY &&
                listsViewModel.keepsRuledLists(SourceType.GRIMMORY) &&
                shelfRules.fitsMagicShelf
        },
    )
    NewListDialog(
        subtitle = "It keeps these filters, so it changes as your library does.",
        servers = servers,
        title = "Save as list",
        onDeviceLine = "Books from this library; Change filters can change it later.",
        onCreate = { name, server ->
            onDismiss()
            // A magic shelf keeps the rules on Grimmory, not the picks: later changes go through its rules.
            val saved = if (server != null) SmartListSpec(rules = shelfRules) else spec
            listsViewModel.createSmartList(name, saved, UserListOrder.DateAdded, onServer = server?.sourceType, onFailure = onFailure, onCreated = { listId ->
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

private const val MagicShelfLine = "A magic shelf, also in Grimmory's web app. Grimmory books only."

/** The rules a magic shelf keeps: the same, without Source (a magic shelf holds its server's books only). */
internal fun ListRules.forMagicShelf(): ListRules = copy(rules = rules.filterNot { rule -> rule.field == ListRuleField.Source })

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
