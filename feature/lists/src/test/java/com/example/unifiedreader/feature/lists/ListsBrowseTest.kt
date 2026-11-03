package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsBrowseTest {
    private val wantToRead = list("list:want-to-read", "Want to read", UserListKind.WantToRead, entries = 2)
    private val emptyStalled = list(UserListIds.Stalled, "Stalled", UserListKind.Stalled)
    private val mine = list("list:a", "Night Run", UserListKind.User, entries = 2)
    private val smart = list("list:s", "Short reads", UserListKind.Smart)
    private val readingList = server("kavita:shelf:1", "Eclipse War", UserListKind.ServerReadingList, SourceType.KAVITA, entries = 2)
        .copy(startYear = 2019, endYear = 2020)
    private val collection = server("kavita:collection:2", "Eisner winners", UserListKind.ServerCollection, SourceType.KAVITA, entries = 1)
    private val shelf = server("grimmory:shelf:3", "Biographies", UserListKind.ServerShelf, SourceType.GRIMMORY, entries = 3)
    private val magic = server("grimmory:magic:4", "Short Russian classics", UserListKind.ServerMagicShelf, SourceType.GRIMMORY, entries = 1)

    private fun catalog(
        own: List<UserList> = listOf(wantToRead, emptyStalled, mine, smart),
        servers: List<UserList> = listOf(readingList, collection, shelf, magic),
        preferences: Map<String, ListPreference> = emptyMap(),
        showing: ListsShowing = ListsShowing(),
        progress: Map<String, ItemProgressSummary> = emptyMap(),
    ) = ListsCatalog(own, servers, preferences, showing, progress)

    @Test
    fun `the place row counts what shows, Stalled only while it holds a book, and the Smart chip is gone`() {
        val places = catalog(preferences = mapOf(shelf.id to ListPreference(hidden = true))).places

        assertEquals(
            listOf(
                ListsFilter.All to null,
                ListsFilter.Yours to 3,
                ListsFilter.Server(SourceType.KAVITA) to 2,
                ListsFilter.Server(SourceType.GRIMMORY) to 1,
            ),
            places,
        )
        assertEquals(ListsFilter.All, ListsFilter.fromKey("smart"))
        assertEquals(ListsFilter.Server(SourceType.GRIMMORY), ListsFilter.fromKey(ListsFilter.Server(SourceType.GRIMMORY).key))
    }

    @Test
    fun `All pins in pin order, keeps pinned lists out of the rows, and opens a server's row with the lists opened lately`() {
        val more = (1..20).map { server("grimmory:shelf:x$it", "Shelf $it", UserListKind.ServerShelf, SourceType.GRIMMORY, entries = 1) }
        val overview = catalog(
            servers = listOf(readingList, collection, shelf, magic) + more,
            preferences = mapOf(
                magic.id to ListPreference(pinnedAtEpochMillis = 20),
                readingList.id to ListPreference(pinnedAtEpochMillis = 10),
                more[14].id to ListPreference(openedAtEpochMillis = 5),
                more[3].id to ListPreference(openedAtEpochMillis = 9),
                collection.id to ListPreference(hidden = true),
            ),
        ).overview()

        assertEquals(listOf("Eclipse War", "Short Russian classics"), overview.pinned.map(UserList::name))
        assertEquals(listOf("Want to read", "Night Run", "Short reads"), overview.yours.map(UserList::name))
        val kavita = overview.servers.first()
        assertEquals(1, kavita.count)
        assertTrue(kavita.lists.isEmpty())
        val grimmory = overview.servers[1]
        assertEquals(22, grimmory.count)
        assertEquals(ServerRowLimit, grimmory.lists.size)
        assertEquals(listOf("Shelf 4", "Shelf 15", "Biographies", "Shelf 1"), grimmory.lists.take(4).map(UserList::name))
        assertEquals(1, overview.hiddenCount)
    }

    @Test
    fun `a place by name groups its lists under letters, with the strip past twenty, and Recent from what was opened`() {
        val many = (listOf("33 1/3", "Art", "art deco", "Русская классика") + (1..20).map { "Zine $it" })
            .mapIndexed { index, name -> server("grimmory:shelf:$index", name, UserListKind.ServerShelf, SourceType.GRIMMORY, entries = 1) }
        val page = catalog(
            servers = many + magic,
            preferences = mapOf(many[1].id to ListPreference(openedAtEpochMillis = 3)),
        ).placePage(ListsFilter.Server(SourceType.GRIMMORY), ListsPlaceChoice(ListsKind.All, ListsSort.Name, ListsView.Rows))

        assertEquals(listOf(ListsKind.All to 25, ListsKind.MagicShelves to 1, ListsKind.Shelves to 24), page.kinds)
        assertEquals(listOf("Art"), page.recent.map(UserList::name))
        assertEquals("A to Z", page.heading)
        assertEquals(listOf('#', 'A', 'S', 'Z', 'Р'), page.groups.map(ListsGroup::letter))
        assertEquals(listOf("Art", "art deco"), page.groups[1].lists.map(UserList::name))
        assertEquals(setOf('#', 'A', 'S', 'Z', 'Р'), page.letters)

        val short = catalog().placePage(ListsFilter.Server(SourceType.GRIMMORY), ListsPlaceChoice(ListsKind.Shelves, ListsSort.Recent, ListsView.Rows))
        assertNull(short.letters)
        assertTrue(short.recent.isEmpty())
        assertEquals(listOf("Biographies"), short.groups.single().lists.map(UserList::name))
    }

    @Test
    fun `Year sorts Kavita's reading lists by their first year, and only where a list has one`() {
        val older = readingList.copy(id = "kavita:shelf:9", name = "Ashfall", startYear = 2017, endYear = 2018)
        val kavita = catalog(servers = listOf(readingList, collection, older))
        val page = kavita.placePage(ListsFilter.Server(SourceType.KAVITA), ListsPlaceChoice(ListsKind.All, ListsSort.Year, ListsView.Rows))

        assertTrue(ListsSort.Year in page.sorts)
        assertEquals(listOf("Ashfall", "Eclipse War", "Eisner winners"), page.groups.single().lists.map(UserList::name))
        assertEquals("By year", page.heading)
        val yours = kavita.placePage(ListsFilter.Yours, ListsPlaceChoice(ListsKind.All, ListsSort.Year, ListsView.Covers))
        assertEquals(ListsSort.Name, yours.choice.sort)
    }

    @Test
    fun `finished lists fold away, leave, or stay with the rest, and hidden ones wait at the end`() {
        val read = ItemProgressSummary(label = "", fraction = 1f, isFinished = true)
        val progress = shelf.entries.associate { it.itemId to read }
        val choice = ListsPlaceChoice(ListsKind.All, ListsSort.Name, ListsView.Rows)
        val grimmory = ListsFilter.Server(SourceType.GRIMMORY)
        val hiddenMagic = mapOf(magic.id to ListPreference(hidden = true))

        val folded = catalog(progress = progress, preferences = hiddenMagic).placePage(grimmory, choice)
        assertEquals(listOf("Biographies"), folded.finished.map(UserList::name))
        assertEquals(listOf("Short Russian classics"), folded.hidden.map(UserList::name))
        assertEquals(0, folded.count)

        val gone = catalog(progress = progress, showing = ListsShowing(finished = ListsFinished.Hide)).placePage(grimmory, choice)
        assertTrue(gone.finished.isEmpty())
        assertEquals(listOf("Short Russian classics"), gone.groups.flatMap(ListsGroup::lists).map(UserList::name))

        val kept = catalog(progress = progress, showing = ListsShowing(finished = ListsFinished.WithTheRest)).placePage(grimmory, choice)
        assertEquals(2, kept.count)
    }

    @Test
    fun `What Lists shows leaves out other people's lists, empty ones and collections, per server`() {
        val theirs = server("kavita:shelf:7", "Promoted", UserListKind.ServerReadingList, SourceType.KAVITA, entries = 1)
            .let { it.copy(server = it.server!!.copy(isOwned = false)) }
        val empty = server("grimmory:shelf:8", "Kobo", UserListKind.ServerShelf, SourceType.GRIMMORY)
        val showing = ListsShowing(hideOthers = setOf(SourceType.KAVITA), hideEmpty = setOf(SourceType.GRIMMORY), hideCollections = true)

        assertTrue(theirs.isLeftOutBy(showing))
        assertTrue(empty.isLeftOutBy(showing))
        assertTrue(collection.isLeftOutBy(showing))
        assertTrue(!readingList.isLeftOutBy(showing))
        assertTrue(!mine.isLeftOutBy(showing))
        assertEquals(3, catalog(servers = listOf(readingList, collection, theirs, empty), showing = showing).overview().hiddenCount)
    }

    @Test
    fun `search finds lists by name and the books in them, saying where each book is`() {
        val night = mine.copy(entries = mine.entries.map { it.copy(title = "Moonfall #${it.position + 1}") })
        val found = catalog(own = listOf(wantToRead, night)).search("moon", ListsFilter.All)

        assertTrue(found.lists.isEmpty())
        assertEquals(listOf("Moonfall #1", "Moonfall #2"), found.books.map { it.entry.title })
        assertEquals("In Night Run, 2 of 2", found.books[1].whereLine())
        assertEquals(listOf("Eclipse War"), catalog().search("eclipse", ListsFilter.Server(SourceType.KAVITA)).lists.map(UserList::name))
        assertTrue(catalog().search("eclipse", ListsFilter.Yours).lists.isEmpty())
    }

    @Test
    fun `a row says what the list is, how far you are and a reading list's years`() {
        val read = ItemProgressSummary(label = "", fraction = 1f, isFinished = true)
        val progress = mapOf(readingList.entries.first().itemId to read)

        assertEquals("Reading list · 1 of 2 read · 2019–2020", readingList.rowLine(progress))
        assertEquals("1 of 2 read · 2019–2020", readingList.rowLine(progress, withKind = false))
        assertEquals(0.5f, readingList.readFraction(progress))
        assertEquals("Collection · 1 series", collection.rowLine(emptyMap()))
        assertEquals("Shelf · 3 books", shelf.rowLine(emptyMap()))
        assertEquals("2021", readingList.copy(startYear = 2021, endYear = 2021).yearsLabel())
        assertEquals("Kavita · reading list · 2 issues", readingList.placeCaption(emptyMap()))
        assertEquals("This device · 2 books", wantToRead.placeCaption(emptyMap()))
    }

    @Test
    fun `the choices read back as written, and a place starts as rows by name or Your lists as covers`() {
        val choices = decodeListsChoices(
            mapOf(
                "place:server:KAVITA:kind" to "ReadingLists",
                "place:server:KAVITA:sort" to "Year",
                "place:server:KAVITA:view" to "Covers",
                "show:hideOthers" to "KAVITA,NOPE",
                "show:finished" to "Hide",
            ),
        )

        assertEquals(ListsPlaceChoice(ListsKind.ReadingLists, ListsSort.Year, ListsView.Covers), choices.placeChoice(ListsFilter.Server(SourceType.KAVITA)))
        // Every place shows covers now, a server's too.
        assertEquals(ListsPlaceChoice(ListsKind.All, ListsSort.Name, ListsView.Covers), choices.placeChoice(ListsFilter.Server(SourceType.GRIMMORY)))
        assertEquals(ListsView.Covers, choices.placeChoice(ListsFilter.Yours).view)
        assertEquals(setOf(SourceType.KAVITA), choices.showing.hideOthers)
        assertEquals(ListsFinished.Hide, choices.showing.finished)
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

    private fun server(id: String, name: String, kind: UserListKind, source: SourceType, entries: Int = 0) =
        list(id, name, kind, entries).copy(
            server = ServerListRef(source, remoteId = id.substringAfterLast(':'), isOwned = true),
            order = if (kind == UserListKind.ServerReadingList) UserListOrder.Custom else UserListOrder.Title,
        )
}
