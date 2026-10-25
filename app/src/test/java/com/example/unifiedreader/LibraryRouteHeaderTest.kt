package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRouteHeaderTest {
    private val books = listOf(
        sampleItem(id = "book-1", title = "First", format = FormatType.EPUB),
        sampleItem(id = "book-2", title = "Second", format = FormatType.EPUB),
    )

    @Test
    fun `header leaves the counts out until the source's catalog has loaded`() {
        // At a cold start the items are the grid's first rows, not the whole catalog.
        val state = AppUiState(selectedSource = SourceType.KAVITA, sourceItems = books)

        val header = state.libraryHeader()

        assertEquals("Kavita", header?.title)
        assertNull(header?.summary)
    }

    @Test
    fun `header counts the catalog once the source has loaded`() {
        val state = AppUiState(
            selectedSource = SourceType.KAVITA,
            sourceItems = books,
            hydratedSources = setOf(SourceType.KAVITA),
        )

        assertEquals("2 books", state.libraryHeader()?.summary)
    }
}
