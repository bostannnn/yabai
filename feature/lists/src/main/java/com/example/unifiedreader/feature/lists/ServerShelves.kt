package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.database.dao.ServerListDao
import com.example.unifiedreader.core.database.entity.ServerListEntity
import com.example.unifiedreader.core.database.entity.ServerListItemEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.decodeListRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A list as its server keeps it: a shelf with no order of its own (Grimmory), or a reading list in order (Kavita). */
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
    /** A magic shelf's rules in the app's own text ([ListRules.encode]); null when it has none the app can read. */
    val rules: String? = null,
)

/** A list a refresh read, with its books as the app's item ids; null when they weren't read with it. */
data class RemoteShelfContents(
    val shelf: RemoteShelf,
    val itemIds: List<String>?,
)

/**
 * One source's shelves on its server, behind that source's own session (the app implements it
 * for Grimmory). Every call throws when the server refuses or can't be reached.
 */
interface ServerShelfClient {
    val sourceType: SourceType

    /** Whether the server keeps its lists in an order (Kavita's reading lists), which the app shows and can change. */
    val isOrdered: Boolean
        get() = false

    /** What its lists are: shelves, reading lists, magic shelves or collections. */
    val kind: UserListKind
        get() = if (isOrdered) UserListKind.ServerReadingList else UserListKind.ServerShelf

    /** Every shelf this user can see, with its books; null while the source is not connected. */
    suspend fun fetchShelves(): List<RemoteShelfContents>?

    suspend fun createShelf(name: String): RemoteShelf

    suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf

    suspend fun deleteShelf(remoteId: String)

    /** Puts this source's books ([itemIds], the app's ids for them) on the shelf, or takes them off it. */
    suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean)

    /** One list's books in its order, for a list whose books [fetchShelves] left out or that is being opened. */
    suspend fun fetchItems(remoteId: String): List<String>

    /** Moves a book of an ordered list from [fromPosition] to [toPosition] (0 first). */
    suspend fun move(remoteId: String, itemId: String, fromPosition: Int, toPosition: Int): Unit =
        error("This server keeps no order for its lists.")

    /** Makes a list whose books the server picks by [rules] (a magic shelf). */
    suspend fun createRuled(name: String, rules: ListRules): RemoteShelf =
        error("This server keeps no lists built from rules.")

    /** Renames a list built from rules and gives it [rules]. */
    suspend fun updateRuled(shelf: RemoteShelf, name: String, rules: ListRules): RemoteShelf =
        error("This server keeps no lists built from rules.")
}

/** A server's shelf as this device keeps it: the server's copy plus the order and Hide finished chosen here. */
data class ServerShelf(
    val id: String,
    val sourceType: SourceType,
    val remote: RemoteShelf,
    val order: UserListOrder,
    val hideFinished: Boolean,
    val itemIds: List<String>,
    /** A reading list in the server's order (Kavita's), not a shelf. */
    val isOrdered: Boolean = false,
    val kind: UserListKind = if (isOrdered) UserListKind.ServerReadingList else UserListKind.ServerShelf,
)

/**
 * A server's shelves (Grimmory's), kept in Room so they show offline. Changes go to the server
 * first and are kept here only once it took them; the order and Hide finished stay on this device.
 */
interface ServerShelfStore {
    val sourceType: SourceType

    /** The kind of list it keeps: one store per server and kind. */
    val kind: UserListKind

    val shelves: Flow<List<ServerShelf>>

    /**
     * Reads every shelf from the server, unless the last try was under the cooldown ago ([force]
     * skips that). Nothing happens while the source is not connected; a failure throws and keeps
     * what was there.
     */
    suspend fun refresh(force: Boolean = false)

    /** Makes a shelf on the server with [items] on it and returns its id here. */
    suspend fun createShelf(name: String, items: List<ReadableItem> = emptyList()): String

    /** Makes a list the server fills by [rules] (a magic shelf) and returns its id here. */
    suspend fun createRuled(name: String, rules: ListRules): String

    /** Renames a list built from rules and gives it [rules], then reads its books again. */
    suspend fun updateRuled(listId: String, name: String, rules: ListRules)

    suspend fun rename(listId: String, name: String)

    suspend fun delete(listId: String)

    /** Puts [items] on the shelf or takes them off; books from other sources are left out. */
    suspend fun setInShelf(listId: String, items: List<ReadableItem>, inShelf: Boolean)

    /** Takes books off the shelf by their item ids (the editor's removals). */
    suspend fun removeFromShelf(listId: String, itemIds: Collection<String>)

    /** Puts an ordered list's books in [orderedItemIds]' order on the server; books not named keep their places. */
    suspend fun reorder(listId: String, orderedItemIds: List<String>)

    /** Reads one list's books again, unless that was done under the cooldown ago ([force] skips that). */
    suspend fun refreshList(listId: String, force: Boolean = false)

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
    override val kind: UserListKind = client.kind

    private val refreshLock = Mutex()
    private var lastRefreshAttemptAt: Long? = null
    private val listRefreshedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override val shelves: Flow<List<ServerShelf>> =
        combine(dao.observeLists(), dao.observeItems()) { lists, items ->
            val itemsByList = items.groupBy(ServerListItemEntity::listId)
            lists
                .filter { it.sourceType == sourceType.name && it.kind == kind.name }
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
        val kept = io { dao.getLists(sourceType.name, kind.name).associateBy(ServerListEntity::id) }
        // A list read without its books keeps the ones held here, unless its count says they changed.
        val itemsByList = contents.associate { (shelf, itemIds) ->
            val id = serverListId(sourceType, kind, shelf.remoteId)
            id to (itemIds ?: io { dao.getItems(id) }.map(ServerListItemEntity::itemId).takeIf { held ->
                kept[id] != null && held.size == shelf.itemCount
            } ?: client.fetchItems(shelf.remoteId).also { listRefreshedAt[id] = at })
        }
        io {
            val lists = contents.map { (shelf, _) ->
                val id = serverListId(sourceType, kind, shelf.remoteId)
                shelf.toEntity(id, sourceType, kind, kept[id], at)
            }
            val items = itemsByList.flatMap { (id, itemIds) ->
                itemIds.distinct().mapIndexed { index, itemId -> ServerListItemEntity(id, itemId, index) }
            }
            dao.replaceKind(sourceType.name, kind.name, lists, items)
        }
    }

    override suspend fun refreshList(listId: String, force: Boolean) {
        val at = now()
        val last = listRefreshedAt[listId]
        if (!force && last != null && at - last < refreshCooldownMillis) return
        listRefreshedAt[listId] = at
        val list = io { dao.getList(listId) } ?: return
        val itemIds = client.fetchItems(list.remoteId)
        io { dao.replaceItems(listId, itemIds.distinct()) }
    }

    override suspend fun reorder(listId: String, orderedItemIds: List<String>) {
        if (!client.isOrdered) return
        val list = io { dao.getList(listId) } ?: return
        val current = io { dao.getItems(listId) }.map(ServerListItemEntity::itemId).toMutableList()
        val target = fullOrder(current, orderedItemIds)
        target.forEachIndexed { index, itemId ->
            if (current[index] == itemId) return@forEachIndexed
            val from = current.indexOf(itemId)
            client.move(list.remoteId, itemId, fromPosition = from, toPosition = index)
            current.removeAt(from)
            current.add(index, itemId)
            // Kept as each move lands, so a failure part way leaves what the server holds.
            io { dao.replaceItems(listId, current.toList()) }
        }
    }

    override suspend fun createShelf(name: String, items: List<ReadableItem>): String {
        val shelf = client.createShelf(name.trim())
        val id = serverListId(sourceType, kind, shelf.remoteId)
        io { dao.upsertList(shelf.toEntity(id, sourceType, kind, kept = null, atEpochMillis = now())) }
        // The shelf is kept before its books go on it, so a failure there still leaves it listed.
        setInShelf(id, items, inShelf = true)
        return id
    }

    override suspend fun createRuled(name: String, rules: ListRules): String {
        val shelf = client.createRuled(name.trim(), rules)
        val id = serverListId(sourceType, kind, shelf.remoteId)
        io { dao.upsertList(shelf.toEntity(id, sourceType, kind, kept = null, atEpochMillis = now())) }
        // The server picks the books; a failure reading them leaves the list, which a refresh fills.
        runCatching { refreshList(id, force = true) }
        return id
    }

    override suspend fun updateRuled(listId: String, name: String, rules: ListRules) {
        val list = io { dao.getList(listId) } ?: return
        val updated = client.updateRuled(list.toRemote(), name.trim().ifEmpty { list.name }, rules)
        io { dao.getList(listId)?.let { dao.upsertList(it.copy(name = updated.name, rules = updated.rules)) } }
        refreshList(listId, force = true)
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

/** "grimmory:shelf:3", "kavita:shelf:4": a server list's id here, from its source and its id on the server. */
fun serverShelfId(sourceType: SourceType, remoteId: String): String = "${sourceType.name.lowercase()}:shelf:$remoteId"

/**
 * A server list's id here: shelves and reading lists as [serverShelfId] made them (so lists kept
 * before keep their ids), "grimmory:magic:5" and "kavita:collection:2" for the kinds that came later.
 */
fun serverListId(sourceType: SourceType, kind: UserListKind, remoteId: String): String = when (kind) {
    UserListKind.ServerMagicShelf -> "${sourceType.name.lowercase()}:magic:$remoteId"
    UserListKind.ServerCollection -> "${sourceType.name.lowercase()}:collection:$remoteId"
    else -> serverShelfId(sourceType, remoteId)
}

/** The shelf as a list: its books are the library's items on it, so a book that left the library leaves it too. */
fun ServerShelf.toUserList(itemsById: Map<String, ReadableItem>): UserList = UserList(
    id = id,
    name = remote.name,
    description = null,
    kind = kind,
    order = order,
    hideFinished = hideFinished,
    createdAtEpochMillis = 0L,
    updatedAtEpochMillis = 0L,
    lastOpenedAtEpochMillis = 0L,
    entries = itemIds.mapNotNull(itemsById::get).mapIndexed { index, item ->
        item.toUserListEntry(position = index, addedAtEpochMillis = item.addedAtEpochMillis ?: 0L)
    },
    server = ServerListRef(sourceType = sourceType, remoteId = remote.remoteId, isOwned = remote.isOwned),
    smart = remote.rules?.let(::decodeListRules)?.let { rules -> SmartListSpec(rules) },
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
    kind: UserListKind,
    kept: ServerListEntity?,
    atEpochMillis: Long,
): ServerListEntity {
    val isOrdered = kind == UserListKind.ServerReadingList
    return ServerListEntity(
        id = id,
        sourceType = sourceType.name,
        kind = kind.name,
        remoteId = remoteId,
        name = name,
        icon = icon,
        iconType = iconType,
        isPublic = isPublic,
        isOwned = isOwned,
        remoteSortField = sortField,
        remoteItemCount = itemCount,
        orderMode = if (isOrdered) UserListOrder.Custom.name else kept?.orderMode ?: initialShelfOrder(sortField).name,
        hideFinished = kept?.hideFinished ?: false,
        syncedAtEpochMillis = atEpochMillis,
        rules = rules,
    )
}

private fun ServerListEntity.toRemote(): RemoteShelf = RemoteShelf(
    remoteId = remoteId,
    name = name,
    icon = icon,
    iconType = iconType,
    isPublic = isPublic,
    isOwned = isOwned,
    sortField = remoteSortField,
    itemCount = remoteItemCount,
    rules = rules,
)

private fun ServerListEntity.toModel(items: List<ServerListItemEntity>): ServerShelf {
    val listKind = UserListKind.entries.firstOrNull { it.name == kind } ?: UserListKind.ServerShelf
    val ordered = listKind == UserListKind.ServerReadingList
    return ServerShelf(
        id = id,
        sourceType = SourceType.entries.firstOrNull { it.name == sourceType } ?: SourceType.GRIMMORY,
        remote = toRemote(),
        order = if (ordered) {
            UserListOrder.Custom
        } else {
            UserListOrder.entries.firstOrNull { it.name == orderMode }?.takeIf { it != UserListOrder.Custom } ?: UserListOrder.Title
        },
        hideFinished = hideFinished,
        itemIds = items.sortedBy(ServerListItemEntity::position).map(ServerListItemEntity::itemId),
        isOrdered = ordered,
        kind = listKind,
    )
}

/**
 * The whole list in the order wanted: the books named in [ordered] fill the places books named
 * there held, in that order; a book not named (one the library doesn't hold) keeps its place.
 */
internal fun fullOrder(current: List<String>, ordered: List<String>): List<String> {
    val named = ordered.filter { it in current }.distinct()
    val namedSet = named.toHashSet()
    val queue = ArrayDeque(named)
    return current.map { itemId -> if (itemId in namedSet) queue.removeFirst() else itemId }
}

/** One store per server that keeps lists: Kavita's reading lists, Grimmory's shelves. */
class ServerListStores(val all: List<ServerShelfStore>) {
    /** The store that keeps [list] (its server and kind), or null for a list on this device. */
    fun forList(list: UserList): ServerShelfStore? = forKind(list.server?.sourceType, list.kind)

    fun forKind(sourceType: SourceType?, kind: UserListKind): ServerShelfStore? =
        all.firstOrNull { store -> store.sourceType == sourceType && store.kind == kind }

    /** The store a new list made on [sourceType] by hand goes to: its shelves or its reading lists. */
    fun forNewList(sourceType: SourceType?): ServerShelfStore? = all.firstOrNull { store ->
        store.sourceType == sourceType && (store.kind == UserListKind.ServerShelf || store.kind == UserListKind.ServerReadingList)
    }

    /** Every server's lists together. */
    val lists: Flow<List<ServerShelf>> = combine(all.map(ServerShelfStore::shelves)) { perServer -> perServer.flatMap { it } }
}
