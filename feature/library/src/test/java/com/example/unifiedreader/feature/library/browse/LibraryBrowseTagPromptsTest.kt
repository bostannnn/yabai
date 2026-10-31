package com.example.unifiedreader.feature.library.browse

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryBrowseTagPromptsTest {
    private val items = listOf(book("1", "Japan", "1930s", "Espionage"), book("2", "Kosovo"))

    @Test
    fun `before any place or time is sorted, Browse offers to check the guesses`() {
        assertEquals(
            LibraryBrowseTagPrompt.CheckGuesses(places = 1, times = 1),
            libraryBrowseTagPrompt(items, LibraryTagSorting()),
        )
        assertEquals(
            "The app guessed that 1 of your tags are places and 1 are times. Check its guesses once, and Browse shows " +
                "your books by country under Set in and on a shelf under When.",
            libraryBrowseGuessesLine(LibraryBrowseTagPrompt.CheckGuesses(places = 1, times = 1)),
        )
    }

    @Test
    fun `once some are sorted, Browse counts the new ones, and says nothing when none wait`() {
        val sorted = LibraryTagSorting().with(
            listOf(
                tag("Japan", LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA),
                tag("1930s", LibraryTagKind.ERA),
                tag("Espionage", LibraryTagKind.THEME),
            ),
        )
        assertEquals(LibraryBrowseTagPrompt.NewTags(1), libraryBrowseTagPrompt(items, sorted))
        assertNull(libraryBrowseTagPrompt(items, sorted.with(listOf(tag("Kosovo", LibraryTagKind.THEME)))))
        assertNull(libraryBrowseTagPrompt(listOf(book("3", "Espionage")), LibraryTagSorting()))
    }

    @Test
    fun `a held Set in chip that is only a Location sorts the Location`() {
        val comic = book("3").copy(comicMetadata = ComicMetadata(locations = listOf("Gotham City")))

        val location = libraryBrowseSortTarget(SourceType.KAVITA, listOf(comic), LibraryBrowseChip(LibraryBrowseRow.SET_IN, "Gotham City"), LibraryTagSorting())
        val tag = libraryBrowseSortTarget(SourceType.KAVITA, items, LibraryBrowseChip(LibraryBrowseRow.ABOUT, "Japan"), LibraryTagSorting())

        assertEquals(LibraryTagField.LOCATION, location?.field)
        assertEquals(LibraryTagField.TAG, tag?.field)
        assertNull(libraryBrowseSortTarget(SourceType.KAVITA, items, LibraryBrowseChip(LibraryBrowseRow.GENRE, "Fiction"), LibraryTagSorting()))
    }

    private fun tag(name: String, kind: LibraryTagKind, region: LibraryPlaceRegion? = null) =
        LibraryTagSortChange(LibraryTagField.TAG, name, LibraryTagChoice(kind, region))

    private fun book(id: String, vararg tags: String): ReadableItem = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "1",
        title = "Book $id",
        format = FormatType.CBZ,
        bookMetadata = BookMetadata(tags = tags.toList()),
    )
}
