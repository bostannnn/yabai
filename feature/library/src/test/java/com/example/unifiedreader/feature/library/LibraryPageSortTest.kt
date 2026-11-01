package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPageSortTest {
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
