package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ListsSectionsTest {
    private val wantToRead = list("list:want-to-read", "Want to read", UserListKind.WantToRead)
    private val emptyStalled = list(UserListIds.Stalled, "Stalled", UserListKind.Stalled)
    private val mine = list("list:a", "Night Run", UserListKind.User, entries = 2)
    private val smart = list("list:s", "Short reads", UserListKind.Smart)
    private val readingList = server("kavita:shelf:1", "Eclipse War", UserListKind.ServerReadingList, SourceType.KAVITA)
    private val collection = server("kavita:collection:2", "Eisner winners", UserListKind.ServerCollection, SourceType.KAVITA)
    private val shelf = server("grimmory:shelf:3", "Biographies", UserListKind.ServerShelf, SourceType.GRIMMORY)
    private val magic = server("grimmory:magic:4", "Short Russian classics", UserListKind.ServerMagicShelf, SourceType.GRIMMORY)

    @Test
    fun `a section per place, Stalled only while it holds a book, servers' lists by kind then name`() {
        val sections = listsSections(
            lists = listOf(wantToRead, emptyStalled, mine, smart),
            serverLists = listOf(shelf, collection, magic, readingList),
            filter = ListsFilter.All,
            query = "",
        )

        assertEquals(listOf("Your lists", "Kavita", "Grimmory"), sections.map(ListsSection::title))
        assertEquals(listOf("Want to read", "Night Run", "Short reads"), sections[0].lists.map(UserList::name))
        assertEquals(listOf("Eclipse War", "Eisner winners"), sections[1].lists.map(UserList::name))
        assertEquals(listOf("Short Russian classics", "Biographies"), sections[2].lists.map(UserList::name))
    }

    @Test
    fun `Smart keeps the lists that update themselves, wherever they live`() {
        val sections = listsSections(listOf(wantToRead, mine, smart), listOf(shelf, magic, readingList), ListsFilter.Smart, query = "")

        assertEquals(listOf("Your lists" to listOf("Short reads"), "Grimmory" to listOf("Short Russian classics")), sections.map { it.title to it.lists.map(UserList::name) })
    }

    @Test
    fun `a server's chip shows only that server's lists, and the row counts each`() {
        val sections = listsSections(listOf(wantToRead, mine), listOf(shelf, readingList), ListsFilter.Server(SourceType.KAVITA), query = "")
        assertEquals(listOf("Kavita"), sections.map(ListsSection::title))

        val filters = listsFilters(listOf(wantToRead, emptyStalled, mine, smart), listOf(shelf, magic, readingList))
        assertEquals(
            listOf(ListsFilter.All to null, ListsFilter.Yours to 3, ListsFilter.Smart to 2, ListsFilter.Server(SourceType.KAVITA) to 1, ListsFilter.Server(SourceType.GRIMMORY) to 2),
            filters,
        )
        assertEquals(ListsFilter.Server(SourceType.GRIMMORY), ListsFilter.fromKey(ListsFilter.Server(SourceType.GRIMMORY).key))
    }

    @Test
    fun `tiles say what a server's list is`() {
        assertEquals("Collection · 0 series", collection.tileCaption(emptyMap()))
        assertEquals("Magic shelf · no books yet", magic.tileCaption(emptyMap()))
        assertEquals("Reading list · 0 issues", readingList.tileCaption(emptyMap()))
    }

    private fun list(id: String, name: String, kind: UserListKind, entries: Int = 0) = UserList(
        id = id,
        name = name,
        description = null,
        kind = kind,
        order = UserListOrder.Custom,
        hideFinished = false,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = 0,
        entries = List(entries) { index ->
            UserListEntry(
                itemId = "$id:$index",
                position = index,
                addedAtEpochMillis = 1,
                sourceType = SourceType.LOCAL,
                libraryId = "lib",
                title = "Book $index",
                primaryText = null,
                secondaryText = null,
                coverUrl = null,
                kind = ReadableItemKind.BOOK,
                format = FormatType.EPUB,
            )
        },
    )

    private fun server(id: String, name: String, kind: UserListKind, source: SourceType) =
        list(id, name, kind).copy(server = ServerListRef(source, remoteId = id.substringAfterLast(':'), isOwned = true), order = UserListOrder.Title)
}
