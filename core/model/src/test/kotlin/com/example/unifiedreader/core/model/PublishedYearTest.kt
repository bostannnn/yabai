package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedYearTest {
    @Test
    fun `a server date gives its year`() {
        assertEquals(2021, publishedYearOf("2021-05-04"))
        assertEquals(2012, publishedYearOf("2012-03-14T00:00:00Z"))
    }

    @Test
    fun `a missing or placeholder date gives no year`() {
        assertNull(publishedYearOf(null))
        assertNull(publishedYearOf(""))
        // Kavita's chapter with no release date.
        assertNull(publishedYearOf("0001-01-01T00:00:00"))
        assertNull(publishedYearOf("n/a"))
    }

    @Test
    fun `only a Grimmory EPUB's page count is a printed length`() {
        val grimmory = ReadableItem(
            id = "grimmory:item:1",
            sourceType = SourceType.GRIMMORY,
            libraryId = "grimmory:library:1",
            title = "Book",
            format = FormatType.EPUB,
            pageCount = 352,
        )

        assertEquals(352, grimmory.printedPageCount())
        assertNull(grimmory.copy(sourceType = SourceType.KAVITA).printedPageCount())
        assertNull(grimmory.copy(format = FormatType.PDF).printedPageCount())
        assertNull(grimmory.copy(pageCount = 0).printedPageCount())
    }
}
