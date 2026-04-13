package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.buildLibraryFilterFacets
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFiltersIntegrationTest {
    @Test
    fun `library filters use multi-valued metadata facets`() {
        val item = ReadableItem(
            id = SourceType.GRIMMORY.remoteItemId("book-1"),
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("books"),
            remoteId = "book-1",
            title = "A Wizard of Earthsea",
            format = FormatType.EPUB,
            kind = ReadableItemKind.BOOK,
            bookMetadata = BookMetadata(
                author = "Ursula K. Le Guin",
                authors = listOf("Ursula K. Le Guin"),
                seriesName = "Earthsea",
                publishers = listOf("Parnassus"),
                genres = listOf("Fantasy"),
                tags = listOf("Wizard School"),
                languages = listOf("English"),
            ),
        )

        val filtered = applyLibraryFilters(
            items = listOf(item),
            filters = LibraryFilterState(
                publishers = setOf("Parnassus"),
                genres = setOf("Fantasy"),
                tags = setOf("Wizard School"),
                languages = setOf("English"),
                series = setOf("Earthsea"),
            ),
        )
        val facets = buildLibraryFilterFacets(listOf(item))

        assertEquals(listOf(item), filtered)
        assertEquals(listOf("Parnassus"), facets.publishers)
        assertEquals(listOf("Fantasy"), facets.genres)
        assertEquals(listOf("Wizard School"), facets.tags)
        assertEquals(listOf("English"), facets.languages)
        assertEquals(listOf("Earthsea"), facets.series)
    }
}
