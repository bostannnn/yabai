package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryBrowseTest {
    private val sorting = LibraryTagSorting().with(
        listOf(
            tag("China", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
            tag("London", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            tag("Berlin", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            tag("World War II", LibraryTagKind.ERA),
            tag("21st century", LibraryTagKind.ERA),
            tag("Medieval", LibraryTagKind.ERA),
            tag("14th century", LibraryTagKind.ERA),
            tag("1930s", LibraryTagKind.ERA),
        ),
    )

    @Test
    fun `the app guesses places, eras and nothing else`() {
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA), libraryTagGuess("Japan"))
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.RUSSIA_AND_AROUND), libraryTagGuess("saint petersburg"))
        assertEquals(LibraryTagChoice(LibraryTagKind.ERA), libraryTagGuess("1930s"))
        assertEquals(LibraryTagChoice(LibraryTagKind.ERA), libraryTagGuess("19th century"))
        assertEquals(LibraryTagChoice(LibraryTagKind.ERA), libraryTagGuess("World War II"))
        assertNull(libraryTagGuess("Espionage"))
        assertNull(libraryTagGuess("Kosovo"))
        assertEquals(LibraryPlaceRegion.ASIA, libraryPlaceRegionGuess("Tokyo"))
        assertNull(libraryPlaceRegionGuess("Gotham City"))
    }

    @Test
    fun `a tag nobody sorted is About, however much it looks like a place`() {
        val items = listOf(book("1", "Lust, Caution", "Espionage", "China", "World War II"))

        val result = libraryBrowse(items, emptyList(), LibraryTagSorting())

        assertNull(result.facets[LibraryBrowseRow.SET_IN])
        assertNull(result.facets[LibraryBrowseRow.WHEN])
        assertEquals(listOf("China", "Espionage", "World War II"), result.facets[LibraryBrowseRow.ABOUT]?.map(LibraryBrowseFacet::value))
    }

    @Test
    fun `eras fall into timeline bars in time order, and wide periods have none`() {
        assertEquals("1930s", libraryEraSlot("1930s")?.label)
        assertEquals("1800s", libraryEraSlot("19th century")?.label)
        assertEquals("1800s", libraryEraSlot("1800s")?.label)
        assertEquals("Middle Ages", libraryEraSlot("14th century")?.label)
        assertEquals("21st c.", libraryEraSlot("21st century")?.label)
        assertNull(libraryEraSlot("20th century"))
        assertNull(libraryEraSlot("World War II"))
        val ordered = listOf("21st century", "1930s", "Prehistory", "1800s", "Renaissance", "2000s")
            .mapNotNull(::libraryEraSlot).sortedBy(LibraryEraSlot::order).map(LibraryEraSlot::label)
        assertEquals(listOf("Prehistory", "Renaissance", "1800s", "1930s", "2000s", "21st c."), ordered)
    }

    @Test
    fun `every chip narrows the books and the counts are what is left`() {
        val items = listOf(
            book("1", "Lust, Caution", "Espionage", "China", "World War II"),
            book("2", "Slow Horses", "Espionage", "London", "21st century"),
            book("3", "Alone in Berlin", "Berlin", "World War II", "Resistance"),
        )

        val espionage = libraryBrowse(items, listOf(LibraryBrowseChip(LibraryBrowseRow.ABOUT, "espionage")), sorting)
        assertEquals(listOf("1", "2"), espionage.items.map(ReadableItem::id))
        assertEquals(
            listOf(LibraryBrowseFacet("21st century", 1), LibraryBrowseFacet("World War II", 1)),
            espionage.facets[LibraryBrowseRow.WHEN],
        )

        val both = libraryBrowse(
            items,
            listOf(LibraryBrowseChip(LibraryBrowseRow.ABOUT, "Espionage"), LibraryBrowseChip(LibraryBrowseRow.WHEN, "World War II")),
            sorting,
        )
        assertEquals(listOf("1"), both.items.map(ReadableItem::id))
        assertEquals(listOf(LibraryBrowseFacet("China", 1)), both.facets[LibraryBrowseRow.SET_IN])
        assertNull(both.facets[LibraryBrowseRow.MOOD])
    }

    @Test
    fun `a timeline bar picks every tag it holds`() {
        val items = listOf(book("1", "A", "Medieval"), book("2", "B", "14th century"), book("3", "C", "1930s"))

        val middleAges = libraryBrowse(items, listOf(LibraryBrowseChip(LibraryBrowseRow.WHEN, "Middle Ages")), sorting)

        assertEquals(listOf("1", "2"), middleAges.items.map(ReadableItem::id))
        assertEquals(
            listOf(LibraryEraBar(LibraryEraSlot("Middle Ages", 200), 2), LibraryEraBar(LibraryEraSlot("1930s", 3930), 1)),
            libraryEraTimeline(items, sorting),
        )
    }

    @Test
    fun `a pile keeps its first books for its spines`() {
        val items = listOf(book("1", "A", "1930s"), book("2", "B", "1930s"), book("3", "C", "1930s"))

        val pile = libraryEraTimeline(items, sorting, bookCount = 2).single()

        assertEquals(3, pile.count)
        assertEquals(listOf("1", "2"), pile.books.map(ReadableItem::id))
    }

    @Test
    fun `cities fold into their country and a book counts once in it`() {
        val places = sorting.with(
            listOf(
                tag("Japan", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
                tag("Tokyo", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
                tag("Kyoto", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
            ),
        )
        val items = listOf(
            book("1", "A", "Japan", "Tokyo"),
            book("2", "B", "Kyoto"),
            book("3", "C", "Tokyo", "Kyoto"),
            book("4", "D", "China"),
        )

        val countries = libraryPlaceCountries(items, places, bookCount = 2)

        assertEquals(listOf("Japan", "China"), countries.map(LibraryPlaceCountry::name))
        assertEquals(3, countries.first().count)
        assertEquals(listOf(LibraryBrowseFacet("Kyoto", 2), LibraryBrowseFacet("Tokyo", 2)), countries.first().cities)
        assertEquals(listOf("1", "2"), countries.first().books.map(ReadableItem::id))
        assertEquals(LibraryPlaceRegion.ASIA, countries.first().region)
    }

    @Test
    fun `a country tagged only through its cities takes the app's name for it`() {
        val items = listOf(book("1", "A", "London"), book("2", "B", "Berlin"))

        val countries = libraryPlaceCountries(items, sorting)

        assertEquals(listOf("England", "Germany"), countries.map(LibraryPlaceCountry::name))
        assertEquals(LibraryPlaceRegion.EUROPE, countries.first().region)
        assertEquals(listOf(LibraryBrowseFacet("London", 1)), countries.first().cities)
    }

    @Test
    fun `a country picks the books set in its cities`() {
        val items = listOf(book("1", "A", "London"), book("2", "B", "Berlin"), book("3", "C", "China"))

        val england = libraryBrowse(items, listOf(LibraryBrowseChip(LibraryBrowseRow.SET_IN, "England")), sorting)
        val london = libraryBrowse(items, listOf(LibraryBrowseChip(LibraryBrowseRow.SET_IN, "London")), sorting)

        assertEquals(listOf("1"), england.items.map(ReadableItem::id))
        assertEquals(listOf("1"), london.items.map(ReadableItem::id))
        assertEquals("United States", libraryPlaceCountry("new york"))
        assertNull(libraryPlaceCountry("Jerusalem"))
    }

    @Test
    fun `a value's books are the first ones that have it`() {
        val items = listOf(
            book("1", "A").copy(bookMetadata = BookMetadata(genres = listOf("Fantasy", "Horror"))),
            book("2", "B").copy(bookMetadata = BookMetadata(genres = listOf("fantasy"))),
            book("3", "C").copy(bookMetadata = BookMetadata(genres = listOf("Fantasy"))),
        )

        val books = libraryBrowseBooks(items, LibraryBrowseRow.GENRE, sorting, perValue = 2)

        assertEquals(listOf("1", "2"), books["fantasy"]?.map(ReadableItem::id))
        assertEquals(listOf("1"), books["horror"]?.map(ReadableItem::id))
    }

    @Test
    fun `a Kavita Location is in Set in at once, under Elsewhere until it has a region`() {
        val comic = book("1", "Issue 1", "Noir").copy(comicMetadata = ComicMetadata(locations = listOf("Gotham City")))

        assertEquals(listOf("Gotham City"), comic.libraryBrowseValues(LibraryBrowseRow.SET_IN, LibraryTagSorting()))
        assertEquals(LibraryPlaceRegion.ELSEWHERE, libraryPlaceCountries(listOf(comic), LibraryTagSorting()).single().region)

        val madeUp = LibraryTagSorting().with(
            listOf(LibraryTagSortChange(LibraryTagField.LOCATION, "gotham city", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.MADE_UP))),
        )
        assertEquals(LibraryPlaceRegion.MADE_UP, libraryPlaceCountries(listOf(comic), madeUp).single().region)
    }

    @Test
    fun `your reading and shelves come from the reading status and the server shelves`() {
        val items = listOf(
            book("1", "A", "Espionage").copy(bookMetadata = BookMetadata(tags = listOf("Espionage"), shelves = listOf("Spy Fiction"))),
            book("2", "B", "Espionage"),
        )
        val status = mapOf("1" to LibraryReadingStatus.FINISHED, "2" to LibraryReadingStatus.NOT_STARTED)

        // Your shelves is off until you switch it on.
        val result = libraryBrowse(
            items,
            emptyList(),
            sorting,
            rows = DefaultLibraryBrowseRows + LibraryBrowseRow.SHELF,
            readingStatusProvider = { item -> status[item.id] },
        )

        assertNull(libraryBrowse(items, emptyList(), sorting).facets[LibraryBrowseRow.SHELF])
        assertEquals(listOf(LibraryBrowseFacet("Spy Fiction", 1)), result.facets[LibraryBrowseRow.SHELF])
        assertEquals(
            listOf(LibraryBrowseFacet("Finished", 1), LibraryBrowseFacet("Not started", 1)),
            result.facets[LibraryBrowseRow.READING],
        )
    }

    private fun tag(name: String, kind: LibraryTagKind, region: LibraryPlaceRegion? = null) =
        LibraryTagSortChange(LibraryTagField.TAG, name, LibraryTagChoice(kind, region))

    private fun book(id: String, title: String, vararg tags: String): ReadableItem = ReadableItem(
        id = id,
        sourceType = SourceType.GRIMMORY,
        libraryId = "1",
        title = title,
        format = FormatType.EPUB,
        bookMetadata = BookMetadata(tags = tags.toList()),
    )
}
