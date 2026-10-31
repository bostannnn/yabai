package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.database.dao.ServerListDao
import com.example.unifiedreader.core.database.entity.ServerListEntity
import com.example.unifiedreader.core.database.entity.ServerListItemEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A shelf as its server keeps it: a collection with no order of its own. */
data class RemoteShelf(
    val remoteId: String,
    val name: String,
    val icon: String? = null,
    val iconType: String? = null,
    val isPublic: Boolean = false,
    /** False for someone else's public shelf. */
    val isOwned: Boolean = true,
    /** The server's own sort for it ("title", "addedOn"), which a new shelf's order starts from. */
    val sortField: String? = null,
    val itemCount: Int = 0,
)

/** A shelf a refresh read, with its books as the app's item ids. */
data class RemoteShelfContents(
    val shelf: RemoteShelf,
    val itemIds: List<String>,
)

/**
 * One source's shelves on its server, behind that source's own session (the app implements it
 * for Grimmory). Every call throws when the server refuses or can't be reached.
 */
interface ServerShelfClient {
    val sourceType: SourceType

    /** Every shelf this user can see, with its books; null while the source is not connected. */
    suspend fun fetchShelves(): List<RemoteShelfContents>?

    suspend fun createShelf(name: String): RemoteShelf

    suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf

    suspend fun deleteShelf(remoteId: String)

    /** Puts this source's books ([itemIds], the app's ids for them) on the shelf, or takes them off it. */
    suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean)
}

/** A server's shelf as this device keeps it: the server's copy plus the order and Hide finished chosen here. */
data class ServerShelf(
    val id: String,
    val sourceType: SourceType,
    val remote: RemoteShelf,
    val order: UserListOrder,
    val hideFinished: Boolean,
    val itemIds: List<String>,
)

/**
 * A server's shelves (Grimmory's), kept in Room so they show offline. Changes go to the server
 * first and are kept here only once it took them; the order and Hide finished stay on this device.
 */
interface ServerShelfStore {
    val sourceType: SourceType

    val shelves: Flow<List<ServerShelf>>

    /**
     * Reads every shelf from the server, unless the last try was under the cooldown ago ([force]
     * skips that). Nothing happens while the source is not connected; a failure throws and keeps
     * what was there.
     */
    suspend fun refresh(force: Boolean = false)

    /** Makes a shelf on the server with [items] on it and returns its id here. */
    suspend fun createShelf(name: String, items: List<ReadableItem> = emptyList()): String

    suspend fun rename(listId: String, name: String)

    suspend fun delete(listId: String)

    /** Puts [items] on the shelf or takes them off; books from other sources are left out. */
    suspend fun setInShelf(listId: String, items: List<ReadableItem>, inShelf: Boolean)

    /** Takes books off the shelf by their item ids (the editor's removals). */
    suspend fun removeFromShelf(listId: String, itemIds: Collection<String>)

    suspend fun setOrder(listId: String, order: UserListOrder)

    suspend fun setHideFinished(listId: String, hideFinished: Boolean)
}

/** How long a refresh of the shelves stands: opening Lists again within it reads nothing. */
const val ShelfRefreshCooldownMillis: Long = 5L * 60 * 1000

class RoomServerShelfStore(
    private val dao: ServerListDao,
    private val client: ServerShelfClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val refreshCooldownMillis: Long = ShelfRefreshCooldownMillis,
) : ServerShelfStore {
    override val sourceType: SourceType = client.sourceType

    private val refreshLock = Mutex()
    private var lastRefreshAttemptAt: Long? = null

    override val shelves: Flow<List<ServerShelf>> =
        combine(dao.observeLists(), dao.observeItems()) { lists, items ->
            val itemsByList = items.groupBy(ServerListItemEntity::listId)
            lists
                .filter { it.sourceType == sourceType.name }
                .map { list -> list.toModel(itemsByList[list.id].orEmpty()) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.remote.name })
        }.flowOn(ioDispatcher)

    override suspend fun refresh(force: Boolean) = refreshLock.withLock {
        val at = now()
        val last = lastRefreshAttemptAt
        if (!force && last != null && at - last < refreshCooldownMillis) return@withLock
        // A failed try waits out the cooldown too, so an offline server isn't asked on every visit.
        lastRefreshAttemptAt = at
        val contents = client.fetchShelves() ?: return@withLock
        io {
            val kept = dao.getLists(sourceType.name).associateBy(ServerListEntity::id)
            val lists = contents.map { (shelf, _) ->
                val id = serverShelfId(sourceType, shelf.remoteId)
                shelf.toEntity(id, sourceType, kept[id], at)
            }
            val items = contents.flatMap { (shelf, itemIds) ->
                val id = serverShelfId(sourceType, shelf.remoteId)
                itemIds.distinct().mapIndexed { index, itemId -> ServerListItemEntity(id, itemId, index) }
            }
            dao.replaceSource(sourceType.name, lists, items)
        }
    }

    override suspend fun createShelf(name: String, items: List<ReadableItem>): String {
        val shelf = client.createShelf(name.trim())
        val id = serverShelfId(sourceType, shelf.remoteId)
        io { dao.upsertList(shelf.toEntity(id, sourceType, kept = null, atEpochMillis = now())) }
        // The shelf is kept before its books go on it, so a failure there still leaves it listed.
        setInShelf(id, items, inShelf = true)
        return id
    }

    override suspend fun rename(listId: String, name: String) {
        val list = io { dao.getList(listId) } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == list.name) return
        val renamed = client.renameShelf(list.toRemote(), trimmed)
        io { dao.getList(listId)?.let { dao.upsertList(it.copy(name = renamed.name)) } }
    }

    override suspend fun delete(listId: String) {
        val list = io { dao.getList(listId) } ?: return
        client.deleteShelf(list.remoteId)
        io { dao.deleteList(listId) }
    }

    override suspend fun setInShelf(listId: String, items: List<ReadableItem>, inShelf: Boolean) =
        changeShelf(listId, items.filter { item -> item.sourceType == sourceType }.map(ReadableItem::id), inShelf)

    override suspend fun removeFromShelf(listId: String, itemIds: Collection<String>) =
        changeShelf(listId, itemIds.toList(), onShelf = false)

    private suspend fun changeShelf(listId: String, itemIds: List<String>, onShelf: Boolean) {
        val ids = itemIds.distinct()
        if (ids.isEmpty()) return
        val list = io { dao.getList(listId) } ?: return
        client.setOnShelf(list.remoteId, ids, onShelf = onShelf)
        io { if (onShelf) dao.addItems(listId, ids) else dao.deleteItems(listId, ids) }
    }

    override suspend fun setOrder(listId: String, order: UserListOrder) = updateLocal(listId) {
        it.copy(orderMode = order.name)
    }

    override suspend fun setHideFinished(listId: String, hideFinished: Boolean) = updateLocal(listId) {
        it.copy(hideFinished = hideFinished)
    }

    private suspend fun updateLocal(listId: String, change: (ServerListEntity) -> ServerListEntity) = io {
        dao.getList(listId)?.let { dao.upsertList(change(it)) }
        Unit
    }

    private suspend fun <T> io(block: () -> T): T = withContext(ioDispatcher) { block() }
}

/** "grimmory:shelf:3": a shelf's id here, from its source and its id on the server. */
fun serverShelfId(sourceType: SourceType, remoteId: String): String = "${sourceType.name.lowercase()}:shelf:$remoteId"

/** The shelf as a list: its books are the library's items on it, so a book that left the library leaves it too. */
fun ServerShelf.toUserList(itemsById: Map<String, ReadableItem>): UserList = UserList(
    id = id,
    name = remote.name,
    description = null,
    kind = UserListKind.ServerShelf,
    order = order,
    hideFinished = hideFinished,
    createdAtEpochMillis = 0L,
    updatedAtEpochMillis = 0L,
    lastOpenedAtEpochMillis = 0L,
    entries = itemIds.mapNotNull(itemsById::get).mapIndexed { index, item ->
        item.toUserListEntry(position = index, addedAtEpochMillis = item.addedAtEpochMillis ?: 0L)
    },
    server = ServerListRef(sourceType = sourceType, remoteId = remote.remoteId, isOwned = remote.isOwned),
)

/**
 * What the toast says when [sourceName]'s server didn't take a change to a shelf: its refusal
 * ("Shelf already exists"), else that it couldn't be reached.
 */
fun shelfChangeFailedMessage(sourceName: String, error: Throwable): String =
    error.message?.takeIf { error is IllegalStateException && it.isNotBlank() }?.let { reason -> "$sourceName didn't save that: $reason" }
        ?: "Couldn't reach $sourceName, so nothing changed."

/** A new shelf starts in the order its server sorts it by: by date added when it says so, else by title. */
internal fun initialShelfOrder(sortField: String?): UserListOrder =
    if (sortField?.contains("added", ignoreCase = true) == true) UserListOrder.DateAdded else UserListOrder.Title

private fun RemoteShelf.toEntity(
    id: String,
    sourceType: SourceType,
    kept: ServerListEntity?,
    atEpochMillis: Long,
): ServerListEntity =
    ServerListEntity(
        id = id,
        sourceType = sourceType.name,
        kind = UserListKind.ServerShelf.name,
        remoteId = remoteId,
        name = name,
        icon = icon,
        iconType = iconType,
        isPublic = isPublic,
        isOwned = isOwned,
        remoteSortField = sortField,
        remoteItemCount = itemCount,
        orderMode = kept?.orderMode ?: initialShelfOrder(sortField).name,
        hideFinished = kept?.hideFinished ?: false,
        syncedAtEpochMillis = atEpochMillis,
    )

private fun ServerListEntity.toRemote(): RemoteShelf = RemoteShelf(
    remoteId = remoteId,
    name = name,
    icon = icon,
    iconType = iconType,
    isPublic = isPublic,
    isOwned = isOwned,
    sortField = remoteSortField,
    itemCount = remoteItemCount,
)

private fun ServerListEntity.toModel(items: List<ServerListItemEntity>): ServerShelf = ServerShelf(
    id = id,
    sourceType = SourceType.entries.firstOrNull { it.name == sourceType } ?: SourceType.GRIMMORY,
    remote = toRemote(),
    order = UserListOrder.entries.firstOrNull { it.name == orderMode }?.takeIf { it != UserListOrder.Custom }
        ?: UserListOrder.Title,
    hideFinished = hideFinished,
    itemIds = items.sortedBy(ServerListItemEntity::position).map(ServerListItemEntity::itemId),
)
