package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadingListSummary
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCatalogKind
import com.example.unifiedreader.feature.library.LibraryHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRouteHeaderTest {
    private val books = listOf(
        sampleItem(id = "book-1", title = "First", format = FormatType.EPUB),
        sampleItem(id = "book-2", title = "Second", format = FormatType.EPUB),
    )

    @Test
    fun `a source's home has no header and heads its grid by what the catalog holds`() {
        val state = AppUiState(selectedSource = SourceType.KAVITA, sourceItems = books)

        assertNull(state.libraryHeader())
        assertEquals(LibraryCatalogKind.Books, state.libraryCatalogKind())
    }

    @Test
    fun `a Kavita list is headed by its name, without a summary line`() {
        val state = AppUiState(
            selectedSource = SourceType.KAVITA,
            sourceItems = books,
            kavitaReadingLists = AppKavitaReadingListState(
                summaries = listOf(
                    ReadingListSummary(id = "list", sourceType = SourceType.KAVITA, remoteId = "1", title = "Want to Read"),
                ),
                selectedReadingListId = "list",
            ),
        )

        assertEquals(LibraryHeader(title = "Want to Read", hasSummaryLine = false), state.libraryHeader())
        assertNull(state.libraryCatalogKind())
    }

    @Test
    fun `a search has no header`() {
        val state = AppUiState(
            selectedSource = SourceType.KAVITA,
            sourceItems = books,
            librarySearchQueriesBySource = mapOf(SourceType.KAVITA to "salt"),
            kavitaReadingLists = AppKavitaReadingListState(
                summaries = listOf(
                    ReadingListSummary(id = "list", sourceType = SourceType.KAVITA, remoteId = "1", title = "Want to Read"),
                ),
                selectedReadingListId = "list",
            ),
        )

        assertNull(state.libraryHeader())
    }
}
