package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPageSortTest {
    @Test
    fun `a length falls in the section its range starts from`() {
        assertEquals("0", libraryPageSection(4))
        assertEquals("20", libraryPageSection(24))
        assertEquals("150", libraryPageSection(150))
        assertEquals("150", libraryPageSection(199))
        assertEquals("750", libraryPageSection(999))
        assertEquals("1k", libraryPageSection(2_400))
        assertEquals("?", libraryPageSection(null))
    }

    @Test
    fun `page sections keep the sorted grid's order and letters stay alphabetical`() {
        assertEquals(
            listOf("20", "150", "1k", "?"),
            librarySortRailSections(listOf("20", "20", "150", "1k", "?", "?"), LibrarySortMode.PAGES),
        )
        assertEquals(listOf("#", "A", "b"), librarySortRailSections(listOf("b", "A", "#"), LibrarySortMode.TITLE))
    }

    @Test
    fun `an item's section follows the sort`() {
        val item = ReadableItem(id = "x", sourceType = SourceType.LOCAL, libraryId = "l", title = "Quiet Engines", format = FormatType.CBZ)

        assertEquals("200", item.librarySortSection(LibrarySortMode.PAGES, pageCount = 212))
        assertEquals("Q", item.librarySortSection(LibrarySortMode.TITLE, pageCount = 212))
    }

    @Test
    fun `the caption gives the length and a series its issues`() {
        assertEquals("212 pages", libraryLengthLabel(212, seriesIssueCount = null))
        assertEquals("1,060 pages · 41 issues", libraryLengthLabel(1_060, seriesIssueCount = 41))
        assertEquals("1 page", libraryLengthLabel(1, seriesIssueCount = null))
        assertNull(libraryLengthLabel(null, seriesIssueCount = 8))
    }

    @Test
    fun `a derived length wins over the item's own`() {
        val series = ReadableItem(id = "s", sourceType = SourceType.KAVITA, libraryId = "l", title = "S", format = FormatType.CBZ)
        val issue = series.copy(id = "i", pageCount = 24)

        assertEquals(244, mapOf("s" to 244).pageCountOf(series))
        assertEquals(24, emptyMap<String, Int>().pageCountOf(issue))
        assertNull(emptyMap<String, Int>().pageCountOf(series))
    }
}
