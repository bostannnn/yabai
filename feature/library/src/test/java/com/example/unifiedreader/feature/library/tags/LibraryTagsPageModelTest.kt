package com.example.unifiedreader.feature.library.tags

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagEntry
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTagsPageModelTest {
    @Test
    fun `a few new tags are rows, many are grouped by the guess`() {
        val few = libraryTagsPageModel(listOf(book("1", "Japan", "Espionage")), SourceType.GRIMMORY, LibraryTagSorting())
        val many = libraryTagsPageModel(
            listOf(book("1", *(1..11).map { "Tag $it" }.toTypedArray())),
            SourceType.GRIMMORY,
            LibraryTagSorting(),
        )

        assertFalse(few.reviewsGuesses)
        assertEquals(2, few.new.size)
        assertTrue(many.reviewsGuesses)
    }

    @Test
    fun `only the source's own books count`() {
        val model = libraryTagsPageModel(
            listOf(book("1", "Japan"), book("2", "Russia").copy(sourceType = SourceType.KAVITA)),
            SourceType.GRIMMORY,
            LibraryTagSorting(),
        )

        assertEquals(listOf("Japan"), model.entries.map(LibraryTagEntry::name))
    }

    @Test
    fun `places group in the regions' order and times by the timeline, then periods`() {
        val entries = libraryTagsPageModel(
            listOf(book("1", "Paris", "Japan", "United States", "1930s", "World War II", "Prehistory")),
            SourceType.GRIMMORY,
            LibraryTagSorting(),
        ).entries

        val regions = libraryTagsByRegion(entries) { entry -> entry.proposed }.map { (region, _) -> region }
        val (onTimeline, periods) = libraryTagsByTime(entries.filter { it.proposed.kind == LibraryTagKind.ERA })

        assertEquals(listOf(LibraryPlaceRegion.AMERICAS, LibraryPlaceRegion.EUROPE, LibraryPlaceRegion.ASIA), regions)
        assertEquals(listOf("Prehistory", "1930s"), onTimeline.map(LibraryTagEntry::name))
        assertEquals(listOf("World War II"), periods.map(LibraryTagEntry::name))
    }

    @Test
    fun `a new row says the guess and offers one thing to do`() {
        val entries = libraryTagsPageModel(
            listOf(
                book("1", "Iceland", "Kosovo", "1930s").copy(
                    sourceType = SourceType.KAVITA,
                    comicMetadata = ComicMetadata(locations = listOf("Gotham City", "Tokyo")),
                ),
            ),
            SourceType.KAVITA,
            LibraryTagSorting(),
        ).entries.associateBy(LibraryTagEntry::name)

        assertEquals("1 book · Looks like a place · Europe", libraryTagNewRowLine(entries.getValue("Iceland")))
        assertEquals("Accept", libraryTagNewRowAction(entries.getValue("Iceland")))
        assertEquals("1 book · No guess, in About for now", libraryTagNewRowLine(entries.getValue("Kosovo")))
        assertEquals("Keep in About", libraryTagNewRowAction(entries.getValue("Kosovo")))
        assertEquals("1 book · Looks like a time", libraryTagNewRowLine(entries.getValue("1930s")))
        assertEquals("Location · 1 book · Looks like Asia", libraryTagNewRowLine(entries.getValue("Tokyo")))
        assertEquals("Location · 1 book · No region guess", libraryTagNewRowLine(entries.getValue("Gotham City")))
        assertEquals("Pick a region", libraryTagNewRowAction(entries.getValue("Gotham City")))
    }

    @Test
    fun `the hub row says what waits`() {
        val items = listOf(book("1", "Japan", "Espionage"), book("2", "Russia").copy(sourceType = SourceType.KAVITA))

        assertEquals("3 tags to check", libraryTagsSummary(items, emptyMap()))

        val grimmorySorted = mapOf(
            SourceType.GRIMMORY to LibraryTagSorting().with(
                listOf(
                    LibraryTagSortChange(LibraryTagField.TAG, "Japan", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA)),
                    LibraryTagSortChange(LibraryTagField.TAG, "Espionage", LibraryTagChoice(LibraryTagKind.THEME)),
                ),
            ),
        )
        assertEquals("1 new tag", libraryTagsSummary(items, grimmorySorted))
        assertEquals("Where Filters shows each tag", libraryTagsSummary(items.take(1), grimmorySorted))
    }

    private fun book(id: String, vararg tags: String): ReadableItem = ReadableItem(
        id = id,
        sourceType = SourceType.GRIMMORY,
        libraryId = "1",
        title = "Book $id",
        format = FormatType.EPUB,
        bookMetadata = BookMetadata(tags = tags.toList()),
    )
}
