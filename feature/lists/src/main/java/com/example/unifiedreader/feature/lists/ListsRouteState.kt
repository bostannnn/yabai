package com.example.unifiedreader.feature.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Where the Lists screen is, shared by its content and its bar: the open list (null: all lists),
 * the search and the filter row's pick, the editor's draft while a list is being edited (or the
 * smart list editor's), and whether anything has scrolled under the bar.
 */
@Stable
class ListsRouteState(
    openListId: String? = null,
    searchQuery: String = "",
    filter: ListsFilter = ListsFilter.All,
) {
    var openListId: String? by mutableStateOf(openListId)
    var searchQuery: String by mutableStateOf(searchQuery)
    var filter: ListsFilter by mutableStateOf(filter)
    var editDraft: UserListDraft? by mutableStateOf(null)

    /** The smart list editor's draft, for a new smart list or one being changed. */
    var smartDraft: SmartListDraft? by mutableStateOf(null)
    var scrolled: Boolean by mutableStateOf(false)
    var isNewListOpen: Boolean by mutableStateOf(false)

    /** New › Import a reading order was picked; the route opens the file picker and clears it. */
    var isImportRequested: Boolean by mutableStateOf(false)

    val isEditing: Boolean
        get() = editDraft != null || smartDraft != null

    fun openList(listId: String) {
        openListId = listId
        editDraft = null
        smartDraft = null
        scrolled = false
    }

    /** Back: out of the editor, then out of the list. Returns false when there was nowhere to go back to. */
    fun back(): Boolean = when {
        smartDraft != null -> {
            smartDraft = null
            true
        }
        editDraft != null -> {
            editDraft = null
            true
        }
        openListId != null -> {
            openListId = null
            scrolled = false
            true
        }
        else -> false
    }

    companion object {
        val Saver = listSaver<ListsRouteState, String?>(
            save = { state -> listOf(state.openListId, state.searchQuery, state.filter.key) },
            restore = { saved ->
                ListsRouteState(openListId = saved[0], searchQuery = saved[1].orEmpty(), filter = ListsFilter.fromKey(saved.getOrNull(2)))
            },
        )
    }
}

@Composable
fun rememberListsRouteState(): ListsRouteState = rememberSaveable(saver = ListsRouteState.Saver) { ListsRouteState() }
