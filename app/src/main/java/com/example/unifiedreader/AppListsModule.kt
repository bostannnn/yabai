package com.example.unifiedreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.RoomServerShelfStore
import com.example.unifiedreader.feature.lists.RoomUserListStore
import com.example.unifiedreader.feature.lists.ServerShelf
import com.example.unifiedreader.feature.lists.ServerShelfStore
import com.example.unifiedreader.feature.lists.ServerWantToRead
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListDraft
import com.example.unifiedreader.feature.lists.UserListKind
import com.example.unifiedreader.feature.lists.UserListOrder
import com.example.unifiedreader.feature.lists.UserListStore
import com.example.unifiedreader.feature.lists.shelfChangeFailedMessage
import com.example.unifiedreader.feature.lists.wantToReadSeriesChange
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The user's lists and Grimmory's shelves; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object AppListsModule {
    @Provides
    @Singleton
    fun provideUserListStore(
        database: UnifiedReaderDatabase,
    ): UserListStore = RoomUserListStore(dao = database.userListDao())

    @Provides
    @Singleton
    fun provideServerShelfStore(
        database: UnifiedReaderDatabase,
        client: GrimmoryShelfClientAdapter,
    ): ServerShelfStore = RoomServerShelfStore(dao = database.serverListDao(), client = client)

    @Provides
    fun provideServerWantToRead(adapter: KavitaWantToReadAdapter): ServerWantToRead = adapter
}

/**
 * Hands the lists and shelves to the composition and runs their changes. They are read only while
 * a screen or sheet that shows them is on screen (and five seconds after). A change to a shelf goes
 * to its server; when that fails, [onFailure] gets what the toast should say.
 */
@HiltViewModel
class AppListsViewModel @Inject constructor(
    private val store: UserListStore,
    private val shelfStore: ServerShelfStore,
    private val serverWantToRead: ServerWantToRead,
) : ViewModel() {
    /** Where the Lists screen is; kept here so it is still there after a book from a list closes. */
    val routeState = ListsRouteState()

    /**
     * The library's books as the screen showing the lists last saw them, so a book taken out of
     * Want to read can tell whether another book of its series is still there.
     */
    var catalog: Map<String, ReadableItem> = emptyMap()

    /** Books Keep the next 3 downloaded has queued in this run of the app, so each is asked for once. */
    val keepDownloadedAsked: MutableSet<String> = HashSet()

    val lists: StateFlow<List<UserList>> = store.lists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList(),
    )

    /** The shelves as last read from the server; null until the database has answered. */
    val shelves: StateFlow<List<ServerShelf>?> = shelfStore.shelves.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    /** Reads the shelves again unless that was done a few minutes ago; a failure keeps the last copy. */
    fun refreshShelves() {
        viewModelScope.launch {
            try {
                shelfStore.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Offline or refused: the shelves kept from the last refresh still show.
            }
        }
    }

    fun setInList(list: UserList, items: List<ReadableItem>, inList: Boolean, onFailure: (String) -> Unit = {}) {
        if (list.kind == UserListKind.ServerShelf) {
            onShelf(onFailure) { shelfStore.setInShelf(list.id, items, inList) }
            return
        }
        viewModelScope.launch {
            if (inList) store.addItems(list.id, items) else store.removeItems(list.id, items.map(ReadableItem::id))
        }
        if (list.kind == UserListKind.WantToRead) mirrorWantToRead(list, items, inList, onFailure)
    }

    /** Kavita's Want to read follows the app's for Kavita books; the app's list has changed either way. */
    private fun mirrorWantToRead(
        list: UserList,
        changed: List<ReadableItem>,
        added: Boolean,
        onFailure: (String) -> Unit,
    ) {
        val changedIds = changed.mapTo(HashSet(), ReadableItem::id)
        val remaining = list.entries.filter { it.itemId !in changedIds }.mapNotNull { entry -> catalog[entry.itemId] }
        val seriesIds = wantToReadSeriesChange(changed, added, remaining, serverWantToRead::seriesIdOf)
        if (seriesIds.isEmpty()) return
        viewModelScope.launch {
            try {
                if (added) serverWantToRead.add(seriesIds) else serverWantToRead.remove(seriesIds)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val source = serverWantToRead.sourceType.displayName()
                onFailure("$source's Want to read didn't change: ${error.message ?: "$source couldn't be reached"}")
            }
        }
    }

    fun createList(name: String, items: List<ReadableItem>, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { onCreated(store.createList(name, items)) }
    }

    fun createShelf(
        name: String,
        items: List<ReadableItem>,
        onCreated: (String) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) = onShelf(onFailure) { onCreated(shelfStore.createShelf(name, items)) }

    fun setOrder(list: UserList, order: UserListOrder) {
        viewModelScope.launch {
            if (list.kind == UserListKind.ServerShelf) shelfStore.setOrder(list.id, order) else store.setOrder(list.id, order)
        }
    }

    fun setHideFinished(list: UserList, hideFinished: Boolean) {
        viewModelScope.launch {
            if (list.kind == UserListKind.ServerShelf) {
                shelfStore.setHideFinished(list.id, hideFinished)
            } else {
                store.setHideFinished(list.id, hideFinished)
            }
        }
    }

    fun setKeepNextDownloaded(list: UserList, keep: Boolean) {
        viewModelScope.launch { store.setKeepNextDownloaded(list.id, keep) }
    }

    fun markOpened(listId: String) {
        viewModelScope.launch { store.markOpened(listId) }
    }

    /**
     * The editor's Done: details, order, removals and the new order, in that order. A shelf saves
     * its name and removals on the server, and its order here.
     */
    fun applyDraft(draft: UserListDraft, onFailure: (String) -> Unit = {}) {
        if (draft.isServerShelf) {
            onShelf(onFailure) {
                shelfStore.setOrder(draft.listId, draft.order)
                shelfStore.rename(draft.listId, draft.name)
                shelfStore.removeFromShelf(draft.listId, draft.removedItemIds)
            }
            return
        }
        viewModelScope.launch {
            store.updateDetails(draft.listId, draft.name, draft.description)
            store.setOrder(draft.listId, draft.order)
            if (draft.removedItemIds.isNotEmpty()) store.removeItems(draft.listId, draft.removedItemIds.toList())
            store.reorder(draft.listId, draft.itemIds)
        }
        lists.value.firstOrNull { it.id == draft.listId && it.kind == UserListKind.WantToRead }?.let { wantToRead ->
            val removed = draft.removedItemIds.mapNotNull(catalog::get)
            if (removed.isNotEmpty()) mirrorWantToRead(wantToRead, removed, added = false, onFailure = onFailure)
        }
    }

    fun deleteList(list: UserList, onFailure: (String) -> Unit = {}) {
        if (list.kind == UserListKind.ServerShelf) {
            onShelf(onFailure) { shelfStore.delete(list.id) }
            return
        }
        viewModelScope.launch { store.deleteList(list.id) }
    }

    private fun onShelf(onFailure: (String) -> Unit, change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onFailure(shelfChangeFailedMessage(shelfStore.sourceType.displayName(), error))
            }
        }
    }
}
