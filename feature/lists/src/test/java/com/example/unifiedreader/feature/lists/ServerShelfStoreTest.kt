package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.database.dao.ServerListDao
import com.example.unifiedreader.core.database.entity.ServerListEntity
import com.example.unifiedreader.core.database.entity.ServerListItemEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ServerShelfStoreTest {
    private val client = FakeShelfClient()
    private val dao = FakeServerListDao()
    private var clock = 1_000L

    private fun store(dispatcher: CoroutineDispatcher) = RoomServerShelfStore(
        dao = dao,
        client = client,
        ioDispatcher = dispatcher,
        now = { clock },
    )

    @Test
    fun `opening Lists again within the cooldown reads nothing, and force reads at once`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.shelves = listOf(contents("3", "Book club", "12"))

        store.refresh()
        clock += ShelfRefreshCooldownMillis - 1
        store.refresh()
        assertEquals(1, client.fetchCount)

        store.refresh(force = true)
        assertEquals(2, client.fetchCount)
        clock += ShelfRefreshCooldownMillis
        store.refresh()
        assertEquals(3, client.fetchCount)
    }

    @Test
    fun `a failed refresh keeps what was there and waits out the cooldown too`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.shelves = listOf(contents("3", "Book club", "12"))
        store.refresh()
        clock += ShelfRefreshCooldownMillis
        client.failFetch = true

        runCatching { store.refresh() }
        clock += 1
        store.refresh()

        assertEquals(2, client.fetchCount)
        assertEquals(listOf("Book club"), store.shelves.first().map { it.remote.name })
    }

    @Test
    fun `nothing is read or dropped while the source is not connected`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.shelves = listOf(contents("3", "Book club", "12"))
        store.refresh()
        client.connected = false

        store.refresh(force = true)

        assertEquals(listOf("Book club"), store.shelves.first().map { it.remote.name })
    }

    @Test
    fun `a refresh keeps the order and Hide finished chosen here and drops shelves that went`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.shelves = listOf(
            contents("3", "Book club", "12", "40", sortField = "title"),
            contents("4", "Next summer", sortField = "addedOn"),
        )
        store.refresh()
        val clubId = serverShelfId(SourceType.GRIMMORY, "3")
        assertEquals(
            listOf(UserListOrder.Title, UserListOrder.DateAdded),
            store.shelves.first().map(ServerShelf::order),
        )
        store.setOrder(clubId, UserListOrder.DateAdded)
        store.setHideFinished(clubId, true)

        client.shelves = listOf(contents("3", "Reading club", "40"))
        store.refresh(force = true)

        val shelf = store.shelves.first().single()
        assertEquals("Reading club", shelf.remote.name)
        assertEquals(UserListOrder.DateAdded, shelf.order)
        assertTrue(shelf.hideFinished)
        assertEquals(listOf("grimmory:item:40"), shelf.itemIds)
    }

    @Test
    fun `a book goes on the shelf only once the server took it`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.shelves = listOf(contents("3", "Book club"))
        store.refresh()
        val id = serverShelfId(SourceType.GRIMMORY, "3")

        store.setInShelf(id, listOf(book("12"), localBook("x")), inShelf = true)
        assertEquals(listOf("grimmory:item:12"), store.shelves.first().single().itemIds)
        assertEquals(listOf("3" to listOf("grimmory:item:12") to true), client.membershipCalls)

        client.failWrites = true
        try {
            store.removeFromShelf(id, listOf("grimmory:item:12"))
            fail("Expected the server's refusal.")
        } catch (_: IllegalStateException) {
        }
        assertEquals(listOf("grimmory:item:12"), store.shelves.first().single().itemIds)
    }

    @Test
    fun `a new shelf is kept even when its books could not go on it`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.failMembership = true

        val id = runCatching { store.createShelf("  Book club ", listOf(book("12"))) }
        assertTrue(id.isFailure)

        val shelf = store.shelves.first().single()
        assertEquals("Book club", shelf.remote.name)
        assertEquals(emptyList<String>(), shelf.itemIds)
    }

    @Test
    fun `renaming and deleting go to the server first`() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        client.shelves = listOf(contents("3", "Book club", icon = "book", isPublic = true))
        store.refresh()
        val id = serverShelfId(SourceType.GRIMMORY, "3")

        store.rename(id, "Reading club")
        assertEquals("book", client.renamed.single().icon)
        assertEquals("Reading club", store.shelves.first().single().remote.name)

        store.delete(id)
        assertEquals(listOf("3"), client.deleted)
        assertEquals(emptyList<ServerShelf>(), store.shelves.first())
    }

    @Test
    fun `a shelf as a list holds the library's books on it and has no Your order`() {
        val shelf = ServerShelf(
            id = "grimmory:shelf:3",
            sourceType = SourceType.GRIMMORY,
            remote = RemoteShelf(remoteId = "3", name = "Book club"),
            order = UserListOrder.Title,
            hideFinished = false,
            itemIds = listOf("grimmory:item:12", "grimmory:item:gone"),
        )
        val list = shelf.toUserList(mapOf("grimmory:item:12" to book("12")))

        assertEquals(listOf("grimmory:item:12"), list.entries.map { it.itemId })
        assertEquals(listOf(UserListOrder.DateAdded, UserListOrder.Title), list.orders)
        assertFalse(list.isReadingOrder)
        assertTrue(list.isEditable)
        assertEquals("1 book · by title", list.tileCaption(emptyMap()))
        val theirs = shelf.copy(remote = shelf.remote.copy(isOwned = false, isPublic = true)).toUserList(emptyMap())
        assertFalse(theirs.isEditable)
    }

    @Test
    fun `a shelf's editor starts from the books as the page shows them`() {
        val shelf = ServerShelf(
            id = "grimmory:shelf:3",
            sourceType = SourceType.GRIMMORY,
            remote = RemoteShelf(remoteId = "3", name = "Book club"),
            order = UserListOrder.Title,
            hideFinished = false,
            itemIds = listOf("grimmory:item:2", "grimmory:item:1"),
        )
        val items = mapOf(
            "grimmory:item:1" to book("1").copy(title = "Alpha"),
            "grimmory:item:2" to book("2").copy(title = "Beta"),
        )

        val draft = shelf.toUserList(items).toDraft()

        assertTrue(draft.isServerShelf)
        assertEquals(listOf("grimmory:item:1", "grimmory:item:2"), draft.itemIds)
    }

    @Test
    fun `a new shelf's order follows the server's sort when it says date added`() {
        assertEquals(UserListOrder.DateAdded, initialShelfOrder("addedOn"))
        assertEquals(UserListOrder.Title, initialShelfOrder("title"))
        assertEquals(UserListOrder.Title, initialShelfOrder(null))
    }

    private fun contents(
        remoteId: String,
        name: String,
        vararg bookIds: String,
        sortField: String? = null,
        icon: String? = null,
        isPublic: Boolean = false,
    ) = RemoteShelfContents(
        shelf = RemoteShelf(remoteId = remoteId, name = name, sortField = sortField, icon = icon, isPublic = isPublic),
        itemIds = bookIds.map { "grimmory:item:$it" },
    )

    private fun book(remoteId: String) = ReadableItem(
        id = "grimmory:item:$remoteId",
        sourceType = SourceType.GRIMMORY,
        libraryId = "grimmory:library:1",
        remoteId = remoteId,
        title = "Book $remoteId",
        format = FormatType.EPUB,
    )

    private fun localBook(id: String) = ReadableItem(
        id = "local:$id",
        sourceType = SourceType.LOCAL,
        libraryId = "local",
        title = "Local $id",
        format = FormatType.EPUB,
    )
}

private class FakeShelfClient : ServerShelfClient {
    override val sourceType = SourceType.GRIMMORY
    var shelves: List<RemoteShelfContents> = emptyList()
    var connected = true
    var failFetch = false
    var failWrites = false
    var failMembership = false
    var fetchCount = 0
    val membershipCalls = mutableListOf<Pair<Pair<String, List<String>>, Boolean>>()
    val renamed = mutableListOf<RemoteShelf>()
    val deleted = mutableListOf<String>()
    private var nextId = 100

    override suspend fun fetchShelves(): List<RemoteShelfContents>? {
        fetchCount++
        if (failFetch) error("Grimmory is offline.")
        return shelves.takeIf { connected }
    }

    override suspend fun createShelf(name: String): RemoteShelf {
        if (failWrites) error("Refused.")
        return RemoteShelf(remoteId = (nextId++).toString(), name = name)
    }

    override suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf {
        if (failWrites) error("Refused.")
        renamed += shelf
        return shelf.copy(name = name)
    }

    override suspend fun deleteShelf(remoteId: String) {
        if (failWrites) error("Refused.")
        deleted += remoteId
    }

    override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) {
        if (failWrites || failMembership) error("Refused.")
        membershipCalls += (remoteId to itemIds) to onShelf
    }
}

/** The DAO over plain maps, so the store's tests need no database. */
private class FakeServerListDao : ServerListDao() {
    private val lists = MutableStateFlow<Map<String, ServerListEntity>>(emptyMap())
    private val items = MutableStateFlow<List<ServerListItemEntity>>(emptyList())

    override fun observeLists(): Flow<List<ServerListEntity>> = lists.map { it.values.toList() }

    override fun observeItems(): Flow<List<ServerListItemEntity>> = items

    override fun getList(listId: String): ServerListEntity? = lists.value[listId]

    override fun getLists(sourceType: String): List<ServerListEntity> = lists.value.values.filter { it.sourceType == sourceType }

    override fun getItems(listId: String): List<ServerListItemEntity> =
        items.value.filter { it.listId == listId }.sortedBy(ServerListItemEntity::position)

    override fun upsertList(list: ServerListEntity) {
        lists.value = lists.value + (list.id to list)
    }

    override fun upsertLists(lists: List<ServerListEntity>) = lists.forEach(::upsertList)

    override fun upsertItems(items: List<ServerListItemEntity>) {
        val keys = items.map { it.listId to it.itemId }.toSet()
        this.items.value = this.items.value.filterNot { (it.listId to it.itemId) in keys } + items
    }

    override fun deleteItems(listId: String, itemIds: List<String>) {
        items.value = items.value.filterNot { it.listId == listId && it.itemId in itemIds }
    }

    override fun deleteAllItems(listId: String) {
        items.value = items.value.filterNot { it.listId == listId }
    }

    override fun deleteItemsOfSource(sourceType: String) {
        val ids = getLists(sourceType).map(ServerListEntity::id).toSet()
        items.value = items.value.filterNot { it.listId in ids }
    }

    override fun deleteListsOfSource(sourceType: String) {
        lists.value = lists.value.filterValues { it.sourceType != sourceType }
    }

    override fun deleteListRow(listId: String) {
        lists.value = lists.value - listId
    }
}
