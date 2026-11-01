package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.lists.ServerShelf
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.listItemsById
import com.example.unifiedreader.feature.lists.stalledList
import com.example.unifiedreader.feature.lists.toUserList
import com.example.unifiedreader.feature.lists.withSmartBooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The part of the lists that takes the whole catalog: the books by id (with the series Kavita's
 * collections hold), the user's lists with their smart lists' books worked out, Stalled, and the
 * connected servers' lists. The Lists screen, a book page's In your lists and the Journal's finished
 * lists draw from it.
 */
internal class ListsCatalogData(
    val itemsById: Map<String, ReadableItem>,
    /** The user's lists, each smart list holding the books its rules pick now. */
    val stored: List<UserList>,
    val stalled: UserList,
    /** The connected servers' lists. */
    val shelves: List<UserList>,
    private val inputs: ListsCatalogInputs,
) {
    fun isFor(other: ListsCatalogInputs): Boolean = inputs.isSameAs(other)
}

/**
 * [ListsCatalogData] for the current lists and catalog. Each part is a pass over the whole catalog,
 * so it is worked out off the main thread whenever its inputs change, and the last result is kept in
 * [AppListsViewModel.catalogData] across screens: opening Lists shows it at once while a fresh one is
 * worked out. With nothing kept yet (Lists opened before the library worked it out), [computeNowWhenMissing]
 * works the first one out at once rather than showing empty lists; without it the value is null until
 * the first one is ready.
 */
@Composable
internal fun rememberListsCatalogData(
    listsViewModel: AppListsViewModel,
    uiState: AppUiState,
    computeNowWhenMissing: Boolean,
): State<ListsCatalogData?> {
    val stored by listsViewModel.lists.collectAsStateWithLifecycle()
    val storedShelves by listsViewModel.shelves.collectAsStateWithLifecycle()
    val inputs = ListsCatalogInputs(
        stored = stored,
        storedShelves = storedShelves,
        items = uiState.items,
        progress = uiState.progressByItemId,
        onDevice = uiState.locallyAvailableRemoteItemIds,
        connected = connectedListServers(uiState),
    )
    val initial = remember {
        listsViewModel.catalogData
            ?: if (computeNowWhenMissing) inputs.catalogData().also { data -> listsViewModel.catalogData = data } else null
    }
    return produceState(initial, inputs.stored, inputs.storedShelves, inputs.items, inputs.progress, inputs.onDevice, inputs.connected) {
        if (value?.isFor(inputs) == true) return@produceState
        value = withContext(Dispatchers.Default) { inputs.catalogData() }
            .also { data -> listsViewModel.catalogData = data }
    }
}

/** What [ListsCatalogData] is worked out from. */
internal class ListsCatalogInputs(
    val stored: List<UserList>,
    val storedShelves: List<ServerShelf>?,
    val items: List<ReadableItem>,
    val progress: Map<String, ItemProgressSummary>,
    val onDevice: Set<String>,
    val connected: Set<SourceType>,
) {
    // The same instances, not equal contents: comparing the whole catalog would cost what it saves.
    fun isSameAs(other: ListsCatalogInputs): Boolean =
        stored === other.stored &&
            storedShelves === other.storedShelves &&
            items === other.items &&
            progress === other.progress &&
            onDevice === other.onDevice &&
            connected == other.connected

    fun catalogData(): ListsCatalogData {
        val shelves = storedShelves.orEmpty()
        val itemsById = listItemsById(items, shelves)
        return ListsCatalogData(
            itemsById = itemsById,
            stored = stored.withSmartBooks(items, progress, onDevice),
            stalled = stalledList(items, progress, nowEpochMillis = System.currentTimeMillis()),
            shelves = shelves.filter { shelf -> shelf.sourceType in connected }.map { shelf -> shelf.toUserList(itemsById) },
            inputs = this,
        )
    }
}
