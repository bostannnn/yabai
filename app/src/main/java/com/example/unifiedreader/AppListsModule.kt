package com.example.unifiedreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.lists.FinishedList
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.RoomServerShelfStore
import com.example.unifiedreader.feature.lists.RoomUserListStore
import com.example.unifiedreader.feature.lists.ServerListStores
import com.example.unifiedreader.feature.lists.ServerShelf
import com.example.unifiedreader.feature.lists.ServerShelfStore
import com.example.unifiedreader.feature.lists.SmartListDraft
import com.example.unifiedreader.feature.lists.SmartListSpec
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

/** The user's lists and the servers' (Kavita's reading lists and collections, Grimmory's magic shelves and shelves); kept out of [AppModule], which should not grow. */
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
    fun provideServerListStores(
        database: UnifiedReaderDatabase,
        grimmoryShelves: GrimmoryShelfClientAdapter,
        grimmoryMagicShelves: GrimmoryMagicShelfClientAdapter,
        kavitaReadingLists: KavitaReadingListClientAdapter,
        kavitaCollections: KavitaCollectionClientAdapter,
    ): ServerListStores = ServerListStores(
        listOf(
            RoomServerShelfStore(dao = database.serverListDao(), client = kavitaReadingLists),
            RoomServerShelfStore(dao = database.serverListDao(), client = kavitaCollections),
            RoomServerShelfStore(dao = database.serverListDao(), client = grimmoryMagicShelves),
            RoomServerShelfStore(dao = database.serverListDao(), client = grimmoryShelves),
        ),
    )

    @Provides
    fun provideServerWantToRead(adapter: KavitaWantToReadAdapter): ServerWantToRead = adapter
}

/**
 * Hands the lists and the servers' lists to the composition and runs their changes. They are read
 * only while a screen or sheet that shows them is on screen (and five seconds after). A change to a
 * server's list goes to its server; when that fails, [onFailure] gets what the toast should say.
 */
@HiltViewModel
class AppListsViewModel @Inject constructor(
    private val store: UserListStore,
    private val serverLists: ServerListStores,
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

    /** The servers' lists as last read (Kavita's reading lists, Grimmory's shelves); null until the database has answered. */
    val shelves: StateFlow<List<ServerShelf>?> = serverLists.lists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    /** The lists read to the end, as the journal keeps them; null until the database has answered. */
    val finishedLists: StateFlow<List<FinishedList>?> = store.finishedLists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    fun recordFinished(finished: FinishedList) {
        viewModelScope.launch { store.recordFinished(finished) }
    }

    /** Reads every server's lists again unless that was done a few minutes ago; a failure keeps the last copy. */
    fun refreshShelves() {
        serverLists.all.forEach { store -> quietly { store.refresh() } }
    }

    /** Reads one server list's books again as its page opens (at most every few minutes). */
    fun refreshList(list: UserList) {
        val store = serverLists.forList(list) ?: return
        quietly { store.refreshList(list.id) }
    }

    private fun quietly(read: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                read()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Offline or refused: what was kept from the last read still shows.
            }
        }
    }

    fun setInList(list: UserList, items: List<ReadableItem>, inList: Boolean, onFailure: (String) -> Unit = {}) {
        serverLists.forList(list)?.let { store ->
            onServer(store, onFailure) { store.setInShelf(list.id, items, inList) }
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

    /**
     * Makes a smart list from [spec] in [order]: on this device, or, [onServer] given, as a list its
     * server fills by the rules (a Grimmory magic shelf), whose order is kept here.
     */
    fun createSmartList(
        name: String,
        spec: SmartListSpec,
        order: UserListOrder,
        onServer: SourceType? = null,
        onCreated: (String) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        if (onServer == null) {
            viewModelScope.launch { onCreated(store.createSmartList(name, spec, order)) }
            return
        }
        val serverStore = serverLists.forKind(onServer, UserListKind.ServerMagicShelf) ?: return
        onServer(serverStore, onFailure) {
            val id = serverStore.createRuled(name, spec.rules)
            serverStore.setOrder(id, order)
            onCreated(id)
        }
    }

    /**
     * The smart list editor's Create or Done: a new list goes to [createSmartList]; a magic shelf
     * saves its name and rules on its server first (its order here), a smart list here.
     */
    fun saveSmartDraft(draft: SmartListDraft, onCreated: (String) -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val listId = draft.listId
        if (listId == null) {
            createSmartList(draft.name, draft.spec, draft.order, onServer = draft.onServer, onCreated = onCreated, onFailure = onFailure)
            return
        }
        val shelf = shelves.value?.firstOrNull { it.id == listId }
        if (shelf == null) {
            viewModelScope.launch { store.updateSmartList(listId, draft.name, draft.spec, draft.order) }
            return
        }
        val serverStore = serverLists.forKind(shelf.sourceType, shelf.kind) ?: return
        onServer(serverStore, onFailure) {
            serverStore.setOrder(listId, draft.order)
            serverStore.updateRuled(listId, draft.name, draft.savedRules)
        }
    }

    /** Whether [sourceType]'s server keeps lists built from rules (Grimmory's magic shelves). */
    fun keepsRuledLists(sourceType: SourceType): Boolean = serverLists.forKind(sourceType, UserListKind.ServerMagicShelf) != null

    /** Makes a list on [sourceType]'s server (a Kavita reading list, a Grimmory shelf) with [items] in it. */
    fun createServerList(
        sourceType: SourceType,
        name: String,
        items: List<ReadableItem>,
        onCreated: (String) -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        val store = serverLists.forNewList(sourceType) ?: return
        onServer(store, onFailure) { onCreated(store.createShelf(name, items)) }
    }

    fun setOrder(list: UserList, order: UserListOrder) {
        viewModelScope.launch {
            serverLists.forList(list)?.setOrder(list.id, order) ?: store.setOrder(list.id, order)
        }
    }

    fun setHideFinished(list: UserList, hideFinished: Boolean) {
        viewModelScope.launch {
            serverLists.forList(list)?.setHideFinished(list.id, hideFinished)
                ?: store.setHideFinished(list.id, hideFinished)
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
        if (draft.isOnServer) {
            val shelf = shelves.value?.firstOrNull { it.id == draft.listId } ?: return
            val serverStore = serverLists.forKind(shelf.sourceType, shelf.kind) ?: return
            onServer(serverStore, onFailure) {
                serverStore.rename(draft.listId, draft.name)
                serverStore.removeFromShelf(draft.listId, draft.removedItemIds)
                if (draft.isServerShelf) serverStore.setOrder(draft.listId, draft.order) else serverStore.reorder(draft.listId, draft.itemIds)
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
        serverLists.forList(list)?.let { serverStore ->
            onServer(serverStore, onFailure) { serverStore.delete(list.id) }
            return
        }
        viewModelScope.launch { store.deleteList(list.id) }
    }

    private fun onServer(serverStore: ServerShelfStore, onFailure: (String) -> Unit, change: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                change()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onFailure(shelfChangeFailedMessage(serverStore.sourceType.displayName(), error))
            }
        }
    }
}
