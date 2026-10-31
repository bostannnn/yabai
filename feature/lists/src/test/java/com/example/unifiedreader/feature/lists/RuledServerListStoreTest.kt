package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.encode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RuledServerListStoreTest {
    private val dao = FakeServerListDao()
    private val russian = ListRules(rules = listOf(ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("Russia"))))

    @Test
    fun `a server's shelves and magic shelves refresh without wiping each other`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val shelves = RoomServerShelfStore(dao, PlainClient(), ioDispatcher = dispatcher)
        val magic = RoomServerShelfStore(dao, MagicClient(russian), ioDispatcher = dispatcher)

        shelves.refresh()
        magic.refresh()
        shelves.refresh(force = true)

        assertEquals(listOf("Biographies"), shelves.shelves.first().map { it.remote.name })
        val magicShelf = magic.shelves.first().single()
        assertEquals(UserListKind.ServerMagicShelf, magicShelf.kind)
        assertEquals("grimmory:magic:5", magicShelf.id)
        val list = magicShelf.toUserList(mapOf("grimmory:item:1" to book("grimmory:item:1")))
        assertEquals(russian, list.smart?.rules)
        assertEquals(listOf("grimmory:item:1"), list.entries.map(UserListEntry::itemId))
    }

    @Test
    fun `a magic shelf made here is kept with its rules and the books the server picked`() = runTest {
        val client = MagicClient(russian)
        val magic = RoomServerShelfStore(dao, client, ioDispatcher = StandardTestDispatcher(testScheduler))

        val id = magic.createRuled("Short Russian classics", russian)

        val made = magic.shelves.first().single { it.id == id }
        assertEquals("Short Russian classics", made.remote.name)
        assertEquals(russian.encode(), made.remote.rules)
        assertEquals(listOf("grimmory:item:1"), made.itemIds)
        assertEquals(russian, client.created)
    }

    private fun book(id: String) = ReadableItem(id = id, sourceType = SourceType.GRIMMORY, libraryId = "lib", title = id, format = FormatType.EPUB)

    private class PlainClient : ServerShelfClient {
        override val sourceType = SourceType.GRIMMORY
        override suspend fun fetchShelves() = listOf(RemoteShelfContents(RemoteShelf(remoteId = "3", name = "Biographies", itemCount = 0), emptyList()))
        override suspend fun createShelf(name: String) = error("not used")
        override suspend fun renameShelf(shelf: RemoteShelf, name: String) = error("not used")
        override suspend fun deleteShelf(remoteId: String) = Unit
        override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) = Unit
        override suspend fun fetchItems(remoteId: String) = emptyList<String>()
    }

    private class MagicClient(private val rules: ListRules) : ServerShelfClient {
        var created: ListRules? = null
        override val sourceType = SourceType.GRIMMORY
        override val kind = UserListKind.ServerMagicShelf
        override suspend fun fetchShelves() = listOf(
            RemoteShelfContents(RemoteShelf(remoteId = "5", name = "Russian", rules = rules.encode(), itemCount = 1), listOf("grimmory:item:1")),
        )
        override suspend fun createShelf(name: String) = error("not used")
        override suspend fun renameShelf(shelf: RemoteShelf, name: String) = error("not used")
        override suspend fun deleteShelf(remoteId: String) = Unit
        override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) = Unit
        override suspend fun fetchItems(remoteId: String) = listOf("grimmory:item:1")
        override suspend fun createRuled(name: String, rules: ListRules): RemoteShelf {
            created = rules
            return RemoteShelf(remoteId = "6", name = name, rules = rules.encode())
        }
    }
}
