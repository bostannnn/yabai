package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySearchMatchesTest {
    private val lustCaution = ReadableItem(
        id = "1",
        sourceType = SourceType.GRIMMORY,
        libraryId = "1",
        title = "Lust, Caution",
        format = FormatType.EPUB,
        description = "In Japanese-occupied Shanghai during World War II, Wong Chia-chi poses as a society wife to " +
            "seduce Mr. Yee, a collaborator, so that he can be assassinated. Julia Lovell's translation.",
        bookMetadata = BookMetadata(author = "Eileen Chang", tags = listOf("Espionage", "China"), genres = listOf("Historical Fiction")),
    )

    @Test
    fun `a description match gives its sentence with the words marked`() {
        val snippet = librarySearchSnippet(lustCaution, "shanghai")!!

        assertEquals(LibrarySearchMatchSource.DESCRIPTION, snippet.source)
        assertEquals(
            "In Japanese-occupied Shanghai during World War II, Wong Chia-chi poses as a society wife to seduce " +
                "Mr. Yee, a collaborator, so that he can be assassinated.",
            snippet.text,
        )
        assertEquals("Shanghai", snippet.text.substring(snippet.matches.single()))
    }

    @Test
    fun `a tag or genre match names it, and a title match needs no reason`() {
        assertEquals(LibrarySearchSnippet("Espionage", listOf(0..5), LibrarySearchMatchSource.TAG), librarySearchSnippet(lustCaution, "espion"))
        assertEquals(LibrarySearchMatchSource.GENRE, librarySearchSnippet(lustCaution, "historical")?.source)
        assertNull(librarySearchSnippet(lustCaution, "caution"))
        assertNull(librarySearchSnippet(lustCaution, "eileen"))
        assertNull(librarySearchSnippet(lustCaution, "dragons"))
    }

    @Test
    fun `tag suggestions put whole names first and count books`() {
        val items = listOf(
            lustCaution,
            lustCaution.copy(id = "2", bookMetadata = BookMetadata(tags = listOf("Japan", "Japanese art"))),
            lustCaution.copy(id = "3", bookMetadata = BookMetadata(tags = listOf("Japan"))),
        )

        val sorting = LibraryTagSorting().with(
            listOf(LibraryTagSortChange(LibraryTagField.TAG, "Japan", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA))),
        )
        val suggestions = librarySearchTagSuggestions(items, "japan", sorting)

        assertEquals(
            listOf(
                LibrarySearchTagSuggestion(LibraryBrowseChip(LibraryBrowseRow.SET_IN, "Japan"), 2),
                LibrarySearchTagSuggestion(LibraryBrowseChip(LibraryBrowseRow.ABOUT, "Japanese art"), 1),
            ),
            suggestions,
        )
        assertEquals(emptyList<LibrarySearchTagSuggestion>(), librarySearchTagSuggestions(items, "j", sorting))
    }
}
