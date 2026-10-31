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
 * the search, the editor's draft while a list is being edited, and whether anything has scrolled
 * under the bar.
 */
@Stable
class ListsRouteState(
    openListId: String? = null,
    searchQuery: String = "",
) {
    var openListId: String? by mutableStateOf(openListId)
    var searchQuery: String by mutableStateOf(searchQuery)
    var editDraft: UserListDraft? by mutableStateOf(null)
    var scrolled: Boolean by mutableStateOf(false)
    var isNewListOpen: Boolean by mutableStateOf(false)

    val isEditing: Boolean
        get() = editDraft != null

    fun openList(listId: String) {
        openListId = listId
        editDraft = null
        scrolled = false
    }

    /** Back: out of the editor, then out of the list. Returns false when there was nowhere to go back to. */
    fun back(): Boolean = when {
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
            save = { state -> listOf(state.openListId, state.searchQuery) },
            restore = { saved -> ListsRouteState(openListId = saved[0], searchQuery = saved[1].orEmpty()) },
        )
    }
}

@Composable
fun rememberListsRouteState(): ListsRouteState = rememberSaveable(saver = ListsRouteState.Saver) { ListsRouteState() }
