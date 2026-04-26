package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.buildContextualLibraryFilterFacets
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

    @Test
    fun `contextual facets respect active filters from other panels`() {
        val comicsLibrary = SourceType.KAVITA.remoteLibraryId("comics")
        val mangaLibrary = SourceType.KAVITA.remoteLibraryId("manga")
        val officeItem = comicItem(
            id = "raw-hero",
            libraryId = comicsLibrary,
            title = "RaW Hero",
            tags = listOf("Office"),
        )
        val seaItem = comicItem(
            id = "blue-period",
            libraryId = mangaLibrary,
            title = "Blue Period",
            tags = listOf("Ocean"),
        )

        val facets = buildContextualLibraryFilterFacets(
            items = listOf(officeItem, seaItem),
            filters = LibraryFilterState(libraries = setOf(comicsLibrary)),
        )

        assertEquals(listOf(comicsLibrary, mangaLibrary), facets.libraries)
        assertEquals(listOf("Office"), facets.tags)
    }

    @Test
    fun `library search ranks title phrase and prefix matches before weak metadata matches`() {
        val metadataMatch = comicItem(
            id = "office",
            libraryId = "library",
            title = "Office Story",
            tags = listOf("raw her"),
        )
        val titlePrefixMatch = comicItem(
            id = "raw-hero",
            libraryId = "library",
            title = "RaW Hero",
        )
        val titleTokenMatch = comicItem(
            id = "hero-raw",
            libraryId = "library",
            title = "Hero Raw",
        )

        val results = applyLibrarySearch(
            items = listOf(metadataMatch, titleTokenMatch, titlePrefixMatch),
            query = "raw her",
        )

        assertEquals(
            listOf("RaW Hero", "Hero Raw", "Office Story"),
            results.map(ReadableItem::title),
        )
    }
}

private fun comicItem(
    id: String,
    libraryId: String,
    title: String,
    tags: List<String> = emptyList(),
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = libraryId,
    remoteId = id,
    title = title,
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    comicMetadata = com.example.unifiedreader.core.model.ComicMetadata(
        series = title,
        tags = tags,
    ),
)
