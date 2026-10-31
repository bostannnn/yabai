package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTagSortingTest {
    private val items = listOf(
        book("1", "Hollywood", "United States", "1970s"),
        book("2", "hollywood", "Kosovo"),
        book("3", "Espionage").copy(comicMetadata = ComicMetadata(locations = listOf("Tokyo", "Gotham City"))),
    )

    @Test
    fun `entries count books, carry the app's guess and wait as new until sorted`() {
        val entries = libraryTagEntries(items, LibraryTagSorting())

        val hollywood = entries.first()
        assertEquals("Hollywood", hollywood.name)
        assertEquals(2, hollywood.count)
        assertTrue(hollywood.isNew)
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.AMERICAS), hollywood.guess)
        assertEquals(LibraryTagChoice(LibraryTagKind.THEME), hollywood.shownAs)
        assertNull(entries.single { it.name == "Kosovo" }.guess)
        assertEquals(LibraryTagChoice(LibraryTagKind.ERA), entries.single { it.name == "1970s" }.guess)

        val tokyo = entries.single { it.field == LibraryTagField.LOCATION && it.name == "Tokyo" }
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA), tokyo.guess)
        val gotham = entries.single { it.name == "Gotham City" }
        assertNull(gotham.guess)
        assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ELSEWHERE), gotham.shownAs)
    }

    @Test
    fun `accepting takes the guess, and a tag without one stays in About`() {
        val entries = libraryTagEntries(items, LibraryTagSorting())
        val sorting = LibraryTagSorting().with(entries.map(LibraryTagEntry::acceptChange))

        assertEquals(LibraryTagKind.PLACE, sorting.kindOf("hollywood"))
        assertEquals(LibraryPlaceRegion.AMERICAS, sorting.regionOfTag("Hollywood"))
        assertEquals(LibraryTagKind.ERA, sorting.kindOf("1970s"))
        assertEquals(LibraryTagKind.THEME, sorting.kindOf("Kosovo"))
        assertEquals(LibraryPlaceRegion.ASIA, sorting.regionOfLocation("Tokyo"))
        assertEquals(LibraryPlaceRegion.ELSEWHERE, sorting.regionOfLocation("Gotham City"))
        assertTrue(libraryTagEntries(items, sorting).none(LibraryTagEntry::isNew))
        assertTrue(sorting.hasPlacesOrTimes)
    }

    @Test
    fun `your choice replaces the guess, and only a place keeps a region`() {
        val sorting = LibraryTagSorting().with(
            listOf(
                LibraryTagSortChange(LibraryTagField.TAG, "Hollywood", LibraryTagChoice(LibraryTagKind.THEME, LibraryPlaceRegion.AMERICAS)),
                LibraryTagSortChange(LibraryTagField.TAG, "Kosovo", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.EUROPE)),
            ),
        )

        assertEquals(LibraryTagChoice(LibraryTagKind.THEME), sorting.choiceOf(LibraryTagField.TAG, "HOLLYWOOD"))
        assertEquals(LibraryPlaceRegion.EUROPE, sorting.regionOfTag("kosovo"))
        assertEquals(LibraryTagChoice(LibraryTagKind.THEME), libraryTagEntries(items, sorting).first().choice)
        assertNull(sorting.choiceOf(LibraryTagField.LOCATION, "Tokyo"))
    }

    @Test
    fun `nothing sorted as a place or a time yet`() {
        assertFalse(LibraryTagSorting().hasPlacesOrTimes)
        val aboutOnly = LibraryTagSorting().with(
            listOf(LibraryTagSortChange(LibraryTagField.TAG, "Hollywood", LibraryTagChoice(LibraryTagKind.THEME))),
        )
        assertFalse(aboutOnly.hasPlacesOrTimes)
    }

    private fun book(id: String, vararg tags: String): ReadableItem = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "1",
        title = "Book $id",
        format = FormatType.EPUB,
        bookMetadata = BookMetadata(tags = tags.toList()),
    )
}
