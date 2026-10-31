package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.database.dao.UserListDao
import com.example.unifiedreader.core.database.entity.UserListEntity
import com.example.unifiedreader.core.database.entity.UserListEntryEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** The user's lists on this device (Want to read and the ones they made); Stalled is not stored. */
interface UserListStore {
    /** Every stored list with its books; Want to read is created the first time this is collected. */
    val lists: Flow<List<UserList>>

    /** Makes a list in Your order, with [items] in it, and returns its id. */
    suspend fun createList(name: String, items: List<ReadableItem> = emptyList()): String

    suspend fun updateDetails(listId: String, name: String, description: String?)

    suspend fun setOrder(listId: String, order: UserListOrder)

    suspend fun setHideFinished(listId: String, hideFinished: Boolean)

    /** Adds [items] at the end; a book already in the list stays where it is. */
    suspend fun addItems(listId: String, items: List<ReadableItem>)

    suspend fun removeItems(listId: String, itemIds: List<String>)

    /** The list's own order, first to last. */
    suspend fun reorder(listId: String, orderedItemIds: List<String>)

    suspend fun deleteList(listId: String)

    /** A book was opened from the list: it becomes the reading order under way. */
    suspend fun markOpened(listId: String)
}

class RoomUserListStore(
    private val dao: UserListDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : UserListStore {
    override val lists: Flow<List<UserList>> =
        combine(dao.observeLists(), dao.observeEntries()) { lists, entries ->
            val entriesByList = entries.groupBy(UserListEntryEntity::listId)
            lists.map { list -> list.toModel(entriesByList[list.id].orEmpty()) }
        }
            .onStart { dao.insertListIfMissing(wantToReadList(now())) }
            .flowOn(ioDispatcher)

    override suspend fun createList(name: String, items: List<ReadableItem>): String = io {
        val at = now()
        val id = UserListIds.newId()
        dao.upsertList(
            UserListEntity(
                id = id,
                name = name.trim(),
                description = null,
                kind = UserListKind.User.name,
                orderMode = UserListOrder.Custom.name,
                hideFinished = false,
                createdAtEpochMillis = at,
                updatedAtEpochMillis = at,
                lastOpenedAtEpochMillis = 0L,
            ),
        )
        if (items.isNotEmpty()) dao.addEntries(id, items.map { it.toEntity(at) }, at)
        id
    }

    override suspend fun updateDetails(listId: String, name: String, description: String?) = updateList(listId) {
        it.copy(name = name.trim().ifEmpty { it.name }, description = description?.trim()?.takeIf(String::isNotEmpty))
    }

    override suspend fun setOrder(listId: String, order: UserListOrder) = updateList(listId) {
        it.copy(orderMode = order.name)
    }

    override suspend fun setHideFinished(listId: String, hideFinished: Boolean) = updateList(listId) {
        it.copy(hideFinished = hideFinished)
    }

    override suspend fun addItems(listId: String, items: List<ReadableItem>) = io {
        val at = now()
        dao.addEntries(listId, items.map { it.toEntity(at) }, at)
    }

    override suspend fun removeItems(listId: String, itemIds: List<String>) = io {
        dao.deleteEntries(listId, itemIds)
    }

    override suspend fun reorder(listId: String, orderedItemIds: List<String>) = io {
        dao.reorder(listId, orderedItemIds, now())
    }

    override suspend fun deleteList(listId: String) = io {
        // Want to read is always there; deleting it only empties it.
        if (listId == UserListIds.WantToRead) dao.deleteAllEntries(listId) else dao.deleteList(listId)
    }

    override suspend fun markOpened(listId: String) = io {
        dao.markOpened(listId, now())
    }

    private suspend fun updateList(listId: String, change: (UserListEntity) -> UserListEntity) = io {
        val list = dao.getList(listId) ?: return@io
        dao.upsertList(change(list).copy(updatedAtEpochMillis = now()))
    }

    private suspend fun <T> io(block: () -> T): T = withContext(ioDispatcher) { block() }
}

internal fun wantToReadList(atEpochMillis: Long): UserListEntity = UserListEntity(
    id = UserListIds.WantToRead,
    name = "Want to read",
    description = null,
    kind = UserListKind.WantToRead.name,
    orderMode = UserListOrder.DateAdded.name,
    hideFinished = false,
    createdAtEpochMillis = atEpochMillis,
    updatedAtEpochMillis = atEpochMillis,
    lastOpenedAtEpochMillis = 0L,
)

private fun ReadableItem.toEntity(atEpochMillis: Long): UserListEntryEntity {
    val entry = toUserListEntry(position = 0, addedAtEpochMillis = atEpochMillis)
    return UserListEntryEntity(
        listId = "",
        itemId = entry.itemId,
        position = entry.position,
        addedAtEpochMillis = entry.addedAtEpochMillis,
        sourceType = entry.sourceType.name,
        libraryId = entry.libraryId,
        itemTitle = entry.title,
        itemPrimaryText = entry.primaryText,
        itemSecondaryText = entry.secondaryText,
        coverUrl = entry.coverUrl,
        itemKind = entry.kind.name,
        format = entry.format.name,
    )
}

internal fun UserListEntity.toModel(entries: List<UserListEntryEntity>): UserList = UserList(
    id = id,
    name = name,
    description = description,
    kind = enumOrDefault(kind, UserListKind.User),
    order = enumOrDefault(orderMode, UserListOrder.Custom),
    hideFinished = hideFinished,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
    entries = entries.sortedBy(UserListEntryEntity::position).mapNotNull { it.toModel() },
)

private fun UserListEntryEntity.toModel(): UserListEntry? {
    val source = SourceType.entries.firstOrNull { it.name == sourceType } ?: return null
    val format = FormatType.entries.firstOrNull { it.name == format } ?: return null
    return UserListEntry(
        itemId = itemId,
        position = position,
        addedAtEpochMillis = addedAtEpochMillis,
        sourceType = source,
        libraryId = libraryId,
        title = itemTitle,
        primaryText = itemPrimaryText,
        secondaryText = itemSecondaryText,
        coverUrl = coverUrl,
        kind = enumOrDefault(itemKind, ReadableItemKind.BOOK),
        format = format,
    )
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
