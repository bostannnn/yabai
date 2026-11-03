package com.example.unifiedreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.LibraryFilterListMode
import com.example.unifiedreader.feature.lists.ListLibraryMode
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListKind
import com.example.unifiedreader.feature.lists.toDraft
import com.example.unifiedreader.feature.lists.toSmartDraft
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

// Lists made and changed in the library: the library opened on a list's behalf (a new smart list,
// a smart list's filters, books to add), and the small dialogs a list's page asks.

/**
 * Opens [mode]'s library for a list: the library of its source, with [filters] in place of the
 * library's own (kept in the mode, they come back when it ends) for a smart list's modes.
 */
internal fun startListLibraryMode(
    mode: ListLibraryMode,
    filters: LibraryFilterState?,
    state: ListsRouteState,
    viewModel: AppViewModel,
) {
    state.libraryMode = mode
    // The filters go in before the library opens: opening recomputes the grid once with them, where
    // a change right after the open left the grid on the opened view's retained presentation.
    if (filters != null) viewModel.onLibraryFiltersChanged(filters, mode.sourceType, persist = false)
    viewModel.openLibrarySource(mode.sourceType)
}

/**
 * Ends the list's mode: the library's own filters come back, and [openListId] given, Lists opens
 * on that list.
 */
internal fun endListLibraryMode(
    state: ListsRouteState,
    viewModel: AppViewModel,
    openListId: String?,
) {
    val mode = state.libraryMode ?: return
    state.libraryMode = null
    when (mode) {
        is ListLibraryMode.NewSmart -> viewModel.onLibraryFiltersChanged(mode.previousFilters, mode.sourceType)
        is ListLibraryMode.ChangeFilters -> viewModel.onLibraryFiltersChanged(mode.previousFilters, mode.sourceType)
        is ListLibraryMode.AddBooks -> Unit
    }
    if (openListId != null) {
        state.openList(openListId)
        viewModel.onTopLevelSelected(TopLevelDestination.Lists)
    }
}

/** The list whose mode this is: the page it was opened from, or what Lists keeps on this device. */
private fun AppListsViewModel.listFor(listId: String, state: ListsRouteState): UserList? =
    state.openListSnapshot?.takeIf { it.id == listId } ?: lists.value.firstOrNull { it.id == listId }

/**
 * Filters on a smart list's behalf: "New smart list" with Save as list (which asks a name), or
 * the list's name with Discard and Save changes, saving the picks now in the library.
 */
internal fun listFilterMode(
    mode: ListLibraryMode?,
    uiState: AppUiState,
    state: ListsRouteState,
    viewModel: AppViewModel,
    listsViewModel: AppListsViewModel,
    onSaveAsList: (LibraryFilterState) -> Unit,
    onFailure: (String) -> Unit,
): LibraryFilterListMode? = when (mode) {
    is ListLibraryMode.NewSmart -> LibraryFilterListMode(
        title = "New smart list",
        primaryLabel = "Save as list",
        primaryEnabled = !uiState.selectedLibraryFilters.isEmpty,
        onPrimary = { onSaveAsList(uiState.selectedLibraryFilters) },
        onCancel = { endListLibraryMode(state, viewModel, openListId = null).also { viewModel.onTopLevelSelected(TopLevelDestination.Lists) } },
    )
    is ListLibraryMode.ChangeFilters -> LibraryFilterListMode(
        title = mode.listName,
        primaryLabel = "Save changes",
        cancelLabel = "Discard",
        primaryEnabled = !uiState.selectedLibraryFilters.isEmpty,
        onPrimary = {
            listsViewModel.listFor(mode.listId, state)?.toSmartDraft()?.let { draft ->
                val spec = smartSpecFromFilters(uiState.selectedLibraryFilters, uiState)
                listsViewModel.saveSmartDraft(draft.copy(rules = spec.rules, filter = spec.filter), onFailure = onFailure)
            }
            endListLibraryMode(state, viewModel, openListId = mode.listId)
        },
        onCancel = { endListLibraryMode(state, viewModel, openListId = mode.listId) },
    )
    else -> null
}

/** Adds the books picked in the library to the list being added to, then goes back to it. */
internal fun finishAddingBooks(
    picked: List<com.example.unifiedreader.core.model.ReadableItem>,
    state: ListsRouteState,
    viewModel: AppViewModel,
    listsViewModel: AppListsViewModel,
    onFailure: (String) -> Unit,
) {
    val mode = state.libraryMode as? ListLibraryMode.AddBooks ?: return
    val list = listsViewModel.listFor(mode.listId, state)
    if (list != null && picked.isNotEmpty()) listsViewModel.setInList(list, picked, inList = true, onFailure = onFailure)
    endListLibraryMode(state, viewModel, openListId = mode.listId)
}

/** The library Add books opens for [list]: its server's for a server's list, else the one last open. */
internal fun addBooksSource(list: UserList, uiState: AppUiState): SourceType = list.server?.sourceType ?: uiState.selectedSource

/** Whether books are added to [list] by hand, so its page offers Add books and selecting. */
internal val UserList.takesBooksByHand: Boolean
    get() = isEditable && (
        kind == UserListKind.User ||
            kind == UserListKind.WantToRead ||
            kind == UserListKind.ServerShelf ||
            kind == UserListKind.ServerReadingList
        )

/** Whether [list]'s smart list keeps the Filters picks it was saved from, so Filters can change it. */
internal val UserList.changesInFilters: Boolean
    get() = smart?.filter != null && server == null

/**
 * New › Smart list: which library its books are from. The library then opens with Filters, and
 * the picks there become the list.
 */
@Composable
internal fun SmartListSourceDialog(
    sources: List<SourceType>,
    onPick: (SourceType) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(sources.firstOrNull()) }
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = "New smart list",
        subtitle = "Which library are its books from? You pick the filters next, in that library.",
        actions = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
            AppDialogConfirmButton(text = "Next", enabled = picked != null, onClick = { picked?.let(onPick) })
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap / 2)) {
            sources.forEach { source ->
                val selected = source == picked
                AppSettingsRow(
                    title = source.displayName(),
                    onClick = { picked = source },
                    selected = selected,
                    showChevron = false,
                    leading = { AppRoundCheck(checked = selected) },
                    horizontalInset = AppLayout.inset + AppLayout.headingGap / 2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius))
                        .semantics { this.selected = selected },
                )
            }
        }
    }
}

/** Rename: the list's name and, for a list on this device you fill by hand, its description. */
@Composable
internal fun RenameListDialog(
    list: UserList,
    onSave: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(list.id) { mutableStateOf(list.name) }
    var description by rememberSaveable(list.id) { mutableStateOf(list.description.orEmpty()) }
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = "Rename",
        actions = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
            AppDialogConfirmButton(text = "Save", enabled = name.isNotBlank(), onClick = { onSave(name.trim(), description.trim()) })
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (list.server == null && list.smart == null) {
                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Renames [list] where it lives: a smart list's draft, any other list's details. */
internal fun renameList(list: UserList, name: String, description: String, listsViewModel: AppListsViewModel, onFailure: (String) -> Unit) {
    val smartDraft = list.toSmartDraft()
    if (smartDraft != null) {
        listsViewModel.saveSmartDraft(smartDraft.copy(name = name), onFailure = onFailure)
    } else {
        listsViewModel.applyDraft(list.toDraft().copy(name = name, description = description), onFailure = onFailure)
    }
}

/** Delete list: asks first; its books stay in the library. */
@Composable
internal fun DeleteListDialog(list: UserList, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val server = list.server?.sourceType?.displayName()
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = "Delete “${list.name}”?",
        subtitle = if (server != null) "It goes from $server too; its books stay in your library." else "The list goes; its books stay in your library.",
        actions = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
            AppDialogConfirmButton(text = "Delete", destructive = true, onClick = onDelete)
        },
    )
}

/**
 * The open list's selected books: Move to the top (a list with an order of its own) or Remove,
 * saved at once where the list lives.
 */
internal fun changeListBooks(
    list: UserList,
    picked: Set<String>,
    remove: Boolean,
    listsViewModel: AppListsViewModel,
    onFailure: (String) -> Unit,
) {
    val draft = list.toDraft()
    val changed = if (remove) {
        draft.copy(itemIds = draft.itemIds.filterNot { it in picked }, removedItemIds = draft.removedItemIds + picked)
    } else {
        draft.copy(
            itemIds = draft.itemIds.filter { it in picked } + draft.itemIds.filterNot { it in picked },
            order = com.example.unifiedreader.feature.lists.UserListOrder.Custom,
        )
    }
    listsViewModel.applyDraft(changed, onFailure = onFailure)
}
