package com.example.unifiedreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.RoomUserListStore
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListDraft
import com.example.unifiedreader.feature.lists.UserListOrder
import com.example.unifiedreader.feature.lists.UserListStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The user's lists; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object AppListsModule {
    @Provides
    @Singleton
    fun provideUserListStore(
        database: UnifiedReaderDatabase,
    ): UserListStore = RoomUserListStore(dao = database.userListDao())
}

/**
 * Hands the lists to the composition and runs their changes. The lists are read only while a
 * screen or sheet that shows them is on screen (and five seconds after).
 */
@HiltViewModel
class AppListsViewModel @Inject constructor(
    private val store: UserListStore,
) : ViewModel() {
    /** Where the Lists screen is; kept here so it is still there after a book from a list closes. */
    val routeState = ListsRouteState()

    val lists: StateFlow<List<UserList>> = store.lists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList(),
    )

    fun setInList(list: UserList, items: List<ReadableItem>, inList: Boolean) {
        viewModelScope.launch {
            if (inList) store.addItems(list.id, items) else store.removeItems(list.id, items.map(ReadableItem::id))
        }
    }

    fun createList(name: String, items: List<ReadableItem>, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch { onCreated(store.createList(name, items)) }
    }

    fun setOrder(listId: String, order: UserListOrder) {
        viewModelScope.launch { store.setOrder(listId, order) }
    }

    fun setHideFinished(listId: String, hideFinished: Boolean) {
        viewModelScope.launch { store.setHideFinished(listId, hideFinished) }
    }

    fun markOpened(listId: String) {
        viewModelScope.launch { store.markOpened(listId) }
    }

    /** The editor's Done: details, order, removals and the new order, in that order. */
    fun applyDraft(draft: UserListDraft) {
        viewModelScope.launch {
            store.updateDetails(draft.listId, draft.name, draft.description)
            store.setOrder(draft.listId, draft.order)
            if (draft.removedItemIds.isNotEmpty()) store.removeItems(draft.listId, draft.removedItemIds.toList())
            store.reorder(draft.listId, draft.itemIds)
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch { store.deleteList(listId) }
    }
}
