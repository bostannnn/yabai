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
    fun `places group by the regions you gave them and a book counts once in each`() {
        val places = sorting.with(
            listOf(
                tag("Japan", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
                tag("Tokyo", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
                tag("Paris", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            ),
        )
        val items = listOf(book("1", "A", "Japan", "Tokyo"), book("2", "B", "China"), book("3", "C", "Paris"))

        val groups = libraryPlaceGroups(items, places)

        assertEquals(LibraryPlaceRegion.ASIA, groups.first().region)
        assertEquals(2, groups.first().count)
        assertEquals(listOf("China", "Japan", "Tokyo"), groups.first().places.map(LibraryBrowseFacet::value))
        assertEquals(LibraryPlaceRegion.EUROPE, groups[1].region)
    }

    @Test
    fun `a Kavita Location is in Set in at once, under Elsewhere until it has a region`() {
        val comic = book("1", "Issue 1", "Noir").copy(comicMetadata = ComicMetadata(locations = listOf("Gotham City")))

        assertEquals(listOf("Gotham City"), comic.libraryBrowseValues(LibraryBrowseRow.SET_IN, LibraryTagSorting()))
        assertEquals(LibraryPlaceRegion.ELSEWHERE, libraryPlaceGroups(listOf(comic), LibraryTagSorting()).single().region)

        val madeUp = LibraryTagSorting().with(
            listOf(LibraryTagSortChange(LibraryTagField.LOCATION, "gotham city", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.MADE_UP))),
        )
        assertEquals(LibraryPlaceRegion.MADE_UP, libraryPlaceGroups(listOf(comic), madeUp).single().region)
    }

    @Test
    fun `your reading and shelves come from the reading status and the server shelves`() {
        val items = listOf(
            book("1", "A", "Espionage").copy(bookMetadata = BookMetadata(tags = listOf("Espionage"), shelves = listOf("Spy Fiction"))),
            book("2", "B", "Espionage"),
        )
        val status = mapOf("1" to LibraryReadingStatus.FINISHED, "2" to LibraryReadingStatus.NOT_STARTED)

        val result = libraryBrowse(items, emptyList(), sorting, readingStatusProvider = { item -> status[item.id] })

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
