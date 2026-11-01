package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTagSectionsTest {
    private val sorting = LibraryTagSorting().with(
        listOf(
            tag("China", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
            tag("England", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            tag("London", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            tag("Berlin", LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE),
            tag("World War II", LibraryTagKind.ERA),
            tag("19th century", LibraryTagKind.ERA),
            tag("1850s", LibraryTagKind.ERA),
            tag("1930s", LibraryTagKind.ERA),
        ),
    )
    private val items = listOf(
        book("1", tags = listOf("London", "19th century", "Espionage"), moods = listOf("Dark")),
        book("2", tags = listOf("England", "London", "1850s"), moods = listOf("Funny")),
        book("3", tags = listOf("Berlin", "1930s", "World War II", "Espionage"), moods = listOf("Dark", "Tense")),
        book("4", tags = listOf("China", "World War II", "Espionage"), moods = listOf("Dark")),
    )

    @Test
    fun `the app guesses places, eras and nothing else`() {
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA), libraryTagGuess("Japan"))
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.RUSSIA_AND_AROUND), libraryTagGuess("saint petersburg"))
        assertEquals(LibraryTagChoice(LibraryTagKind.ERA), libraryTagGuess("1930s"))
        assertEquals(LibraryTagChoice(LibraryTagKind.ERA), libraryTagGuess("World War II"))
        assertNull(libraryTagGuess("Espionage"))
        assertNull(libraryTagGuess("Kosovo"))
        assertEquals(LibraryPlaceRegion.ASIA, libraryPlaceRegionGuess("Tokyo"))
    }

    @Test
    fun `eras fall into piles in time order, and a pile's label names it back`() {
        assertEquals("1800s", libraryEraSlot("19th century")?.label)
        assertEquals("Middle Ages", libraryEraSlot("14th century")?.label)
        assertNull(libraryEraSlot("20th century"))
        assertNull(libraryEraSlot("World War II"))
        listOf("1800s", "1930s", "Ancient", "Middle Ages", "Prehistory", "Renaissance", "21st c.", "Future").forEach { label ->
            assertEquals(label, libraryEraPile(label)?.label)
        }
        assertNull(libraryEraPile("World War II"))
    }

    @Test
    fun `a tag nobody sorted counts under About, however much it looks like a place`() {
        val facets = buildLibraryTagFilterFacets(items, LibraryFilterState(), tagKindOf = LibraryTagSorting()::kindOf)

        assertTrue(facets.places.isEmpty())
        assertTrue(facets.times.isEmpty())
        assertTrue("China" in facets.tags)
        assertEquals(3, facets.counts.tags["espionage"])
    }

    @Test
    fun `cities fold into their country, and a book counts once in it`() {
        val facets = buildLibraryTagFilterFacets(items, LibraryFilterState(), tagKindOf = sorting::kindOf)

        assertEquals(2, facets.counts.places["england"])
        assertEquals(2, facets.counts.places["london"])
        assertEquals(1, facets.counts.places["germany"])
        assertEquals(listOf("Espionage"), facets.tags)
    }

    @Test
    fun `a picked country takes its cities' books, and a pile every era in it`() {
        val england = applyLibraryFilters(items, LibraryFilterState(places = setOf("England")))
        val germany = applyLibraryFilters(items, LibraryFilterState(places = setOf("Germany")))
        val nineteenth = applyLibraryFilters(items, LibraryFilterState(times = setOf("1800s")))
        val war = applyLibraryFilters(items, LibraryFilterState(times = setOf("World War II")))

        assertEquals(listOf("1", "2"), england.map(ReadableItem::id))
        assertEquals(listOf("3"), germany.map(ReadableItem::id))
        assertEquals(listOf("1", "2"), nineteenth.map(ReadableItem::id))
        assertEquals(listOf("3", "4"), war.map(ReadableItem::id))
    }

    @Test
    fun `two picks in a section mean either, sections narrow, and each counts what the others leave`() {
        val either = applyLibraryFilters(items, LibraryFilterState(places = setOf("China", "Germany")))
        val narrowed = LibraryFilterState(places = setOf("China", "Germany"), moods = setOf("Tense"))
        val facets = buildLibraryTagFilterFacets(items, narrowed, tagKindOf = sorting::kindOf)

        assertEquals(listOf("3", "4"), either.map(ReadableItem::id))
        assertEquals(listOf("3"), applyLibraryFilters(items, narrowed).map(ReadableItem::id))
        // Set in counts under the mood alone, so the other places keep their numbers.
        assertEquals(1, facets.counts.places["germany"])
        assertNull(facets.counts.places["china"])
        // Mood counts under the places alone.
        assertEquals(2, facets.counts.moods["dark"])
        assertEquals(listOf("1930s", "World War II"), facets.times.sorted())
    }

    @Test
    fun `a Kavita Location is a place at once`() {
        val comic = book("5", tags = listOf("Noir")).copy(comicMetadata = ComicMetadata(locations = listOf("Gotham City")))

        assertEquals(listOf("Gotham City"), comic.libraryBrowseValues(LibraryBrowseRow.SET_IN, LibraryTagSorting()))
        assertEquals(listOf("5"), applyLibraryFilters(listOf(comic), LibraryFilterState(places = setOf("Gotham City"))).map(ReadableItem::id))
    }

    @Test
    fun `a tag on the book page narrows its section to it and keeps the others`() {
        val state = LibraryFilterState(places = setOf("China"), moods = setOf("Dark"))

        assertEquals(
            LibraryFilterState(places = setOf("England"), moods = setOf("Dark")),
            state.filteringBy(LibraryBrowseChip(LibraryBrowseRow.SET_IN, "England")),
        )
        assertEquals(setOf("1800s"), state.filteringBy(LibraryBrowseChip(LibraryBrowseRow.WHEN, "1800s")).times)
        assertEquals(LibraryFilterPanel.Tag, LibraryBrowseChip(LibraryBrowseRow.ABOUT, "Espionage").filterPanel)
        assertNull(LibraryBrowseChip(LibraryBrowseRow.SHELF, "Spy Fiction").filterPanel)
    }

    @Test
    fun `Save as list keeps places and times as tag rules of their own`() {
        val rules = LibraryFilterState(places = setOf("England"), times = setOf("1800s"), moods = setOf("Dark"))
            .toListRules(
                sourceType = SourceType.GRIMMORY,
                placeTags = { place -> libraryPlaceTags(place, items) },
                timeTags = { time -> libraryTimeTags(time, items) },
            )
            .rules

        assertTrue(ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("England", "London")) in rules)
        assertTrue(ListRule(ListRuleField.Tag, ListRuleOperator.AnyOf, listOf("1800s", "1850s", "19th century")) in rules)
        assertTrue(ListRule(ListRuleField.Mood, ListRuleOperator.AnyOf, listOf("Dark")) in rules)
    }

    private fun tag(name: String, kind: LibraryTagKind, region: LibraryPlaceRegion? = null) =
        LibraryTagSortChange(LibraryTagField.TAG, name, LibraryTagChoice(kind, region))

    private fun book(id: String, tags: List<String>, moods: List<String> = emptyList()): ReadableItem = ReadableItem(
        id = id,
        sourceType = SourceType.GRIMMORY,
        libraryId = "1",
        title = "Book $id",
        format = FormatType.EPUB,
        bookMetadata = BookMetadata(tags = tags, moods = moods),
    )
}
