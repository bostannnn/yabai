package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterFacetCounts
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterSheetModelTest {
    private val authorFacets = LibraryFilterFacets(
        authors = listOf("Ruth Amari", "Lena Aster", "Tomas Ellery", "Анна Лесная", "Илья Горский"),
        counts = LibraryFilterFacetCounts(
            authors = mapOf("ruth amari" to 1, "lena aster" to 2, "tomas ellery" to 1, "анна лесная" to 1, "илья горский" to 3),
        ),
    )

    @Test
    fun `picked values are pinned under Selected and leave their letter`() {
        val groups = libraryFacetPageGroups(
            panel = LibraryFilterPanel.Author,
            state = LibraryFilterState(authors = setOf("Анна Лесная")),
            facets = authorFacets,
            libraryTitlesById = emptyMap(),
        )

        assertEquals(listOf(SelectedGroupTitle, "A", "E", "Г"), groups.map(LibraryFacetPageGroup::title))
        assertEquals(listOf("Анна Лесная"), groups.first().values.map(LibraryFacetPageValue::label))
        assertTrue(groups.first().values.single().selected)
        // Authors go under their surname's letter.
        assertEquals(listOf("Ruth Amari", "Lena Aster"), groups[1].values.map(LibraryFacetPageValue::label))
        assertEquals(2, groups[1].values[1].count)
    }

    @Test
    fun `a search keeps only matching values`() {
        val groups = libraryFacetPageGroups(
            panel = LibraryFilterPanel.Author,
            state = LibraryFilterState(),
            facets = authorFacets,
            libraryTitlesById = emptyMap(),
            query = " ast ",
        )

        assertEquals(listOf("A"), groups.map(LibraryFacetPageGroup::title))
        assertEquals(listOf("Lena Aster"), groups.single().values.map(LibraryFacetPageValue::label))
    }

    @Test
    fun `a picked value the facets no longer have still shows, at zero`() {
        val groups = libraryFacetPageGroups(
            panel = LibraryFilterPanel.Author,
            state = LibraryFilterState(authors = setOf("Gone Author")),
            facets = authorFacets,
            libraryTitlesById = emptyMap(),
        )

        val picked = groups.first().values.single()
        assertEquals("Gone Author", picked.label)
        assertEquals(0, picked.count)
    }

    @Test
    fun `libraries show by title and group by it`() {
        val groups = libraryFacetPageGroups(
            panel = LibraryFilterPanel.Library,
            state = LibraryFilterState(),
            facets = LibraryFilterFacets(libraries = listOf("2", "1")),
            libraryTitlesById = mapOf("1" to "Manga", "2" to "Books"),
        )

        assertEquals(listOf("B", "M"), groups.map(LibraryFacetPageGroup::title))
        assertEquals("2", groups.first().values.single().value)
        // Uncounted facets show no counts.
        assertNull(groups.first().values.single().count)
    }

    @Test
    fun `the row summary names up to two picks, then how many more`() {
        val state = LibraryFilterState(languages = setOf("English", "Русский", "Deutsch"))

        assertNull(libraryFacetSelectionSummary(LibraryFilterPanel.Author, state, emptyMap()))
        assertEquals(
            "English",
            libraryFacetSelectionSummary(LibraryFilterPanel.Language, LibraryFilterState(languages = setOf("English")), emptyMap()),
        )
        assertEquals("Deutsch, English +1", libraryFacetSelectionSummary(LibraryFilterPanel.Language, state, emptyMap()))
        assertEquals(
            "Manga",
            libraryFacetSelectionSummary(
                LibraryFilterPanel.Library,
                LibraryFilterState(libraries = setOf("1")),
                mapOf("1" to "Manga"),
            ),
        )
    }

    @Test
    fun `format chips keep every format the source has, with counts`() {
        val facets = LibraryFilterFacets(
            formats = listOf(FormatType.EPUB),
            counts = LibraryFilterFacetCounts(formats = mapOf(FormatType.EPUB to 12, FormatType.PDF to 0)),
        )

        val options = libraryFilterChipOptions(
            LibraryFilterPanel.Format,
            LibraryFilterState(formats = setOf(FormatType.EPUB)),
            facets,
        )

        assertEquals(setOf("EPUB", "PDF"), options.map(LibraryFilterChipOption::label).toSet())
        val epub = options.single { it.label == "EPUB" }
        assertTrue(epub.selected)
        assertEquals(12, epub.count)
        assertEquals(LibraryFilterState(), epub.toggled)
        assertEquals(0, options.single { it.label == "PDF" }.count)
    }

    @Test
    fun `reading status chips are always the three statuses`() {
        val options = libraryFilterChipOptions(
            LibraryFilterPanel.ReadingStatus,
            LibraryFilterState(),
            LibraryFilterFacets(),
        )

        assertEquals(LibraryReadingStatus.entries.map { it.name }, options.map(LibraryFilterChipOption::key))
        assertNull(options.first().count)
        assertFalse(options.any(LibraryFilterChipOption::selected))
    }

    @Test
    fun `facet pages are the long lists only`() {
        assertFalse(LibraryFilterPanel.Format.opensAsPage)
        assertFalse(LibraryFilterPanel.ReadingStatus.opensAsPage)
        assertTrue(LibraryFilterPanel.Author.opensAsPage)
        assertTrue(LibraryFilterPanel.Library.opensAsPage)
        assertTrue(libraryFilterChipOptions(LibraryFilterPanel.Author, LibraryFilterState(), authorFacets).isEmpty())
    }

    @Test
    fun `labels for the counts`() {
        assertEquals("Showing 14 of 21", libraryFilterShowingLabel(shownCount = 14, totalCount = 21))
        assertEquals("Showing all 21", libraryFilterShowingLabel(shownCount = 21, totalCount = 21))
        assertEquals("Showing 3", libraryFilterShowingLabel(shownCount = 3, totalCount = null))
        assertEquals("Show 1 book", libraryFilterShowButtonLabel(1))
        assertEquals("Show 14 books", libraryFilterShowButtonLabel(14))
        assertEquals(5, libraryFacetValueCount(LibraryFilterPanel.Author, LibraryFilterState(), authorFacets))
    }
}
