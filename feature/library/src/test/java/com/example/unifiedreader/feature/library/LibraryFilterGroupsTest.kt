package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterFacetCounts
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterGroupsTest {
    @Test
    fun `the default order heads each group that shows two sections or more`() {
        val headings = libraryFilterGroupHeadings(defaultVisibleLibraryFilterPanels(SourceType.GRIMMORY))

        assertEquals(
            mapOf(0 to "Which books", 5 to "What kind", 8 to "Who, where, when"),
            headings,
        )
    }

    @Test
    fun `a custom order that splits a group shows no headings`() {
        val panels = listOf(
            LibraryFilterPanel.ReadingStatus,
            LibraryFilterPanel.Genre,
            LibraryFilterPanel.Format,
        )

        assertEquals(emptyMap<Int, String>(), libraryFilterGroupHeadings(panels))
    }

    @Test
    fun `a section with one value to choose stays out unless something is picked in it`() {
        val facets = LibraryFilterFacets(
            formats = listOf(FormatType.EPUB),
            libraries = listOf("1"),
            languages = listOf("English", "english"),
        )

        assertFalse(LibraryFilterPanel.Format.offersChoice(LibraryFilterState(), facets))
        assertFalse(LibraryFilterPanel.Library.offersChoice(LibraryFilterState(), facets))
        assertFalse(LibraryFilterPanel.Language.offersChoice(LibraryFilterState(), facets))
        assertTrue(LibraryFilterPanel.Library.offersChoice(LibraryFilterState(libraries = setOf("1")), facets))
        assertTrue(LibraryFilterPanel.Genre.offersChoice(LibraryFilterState(), facets))
    }

    @Test
    fun `library chips are named by the library's title`() {
        val facets = LibraryFilterFacets(
            libraries = listOf("1", "2"),
            counts = LibraryFilterFacetCounts(libraries = mapOf("1" to 3, "2" to 9)),
        )

        val chips = libraryFilterTopChips(
            panel = LibraryFilterPanel.Library,
            state = LibraryFilterState(),
            facets = facets,
            limit = LibraryFilterPanel.Library.topChipLimit,
            libraryTitlesById = mapOf("1" to "Books", "2" to "Comics"),
        )

        assertEquals(listOf("Comics", "Books"), chips.map { it.label })
    }

    @Test
    fun `named periods are the times without a pile, most books first, picked ones left to the piles`() {
        val facets = LibraryFilterFacets(
            times = listOf("1800s", "Cold War", "World War II", "Belle Époque"),
            counts = LibraryFilterFacetCounts(times = mapOf("1800s" to 9, "cold war" to 2, "world war ii" to 5, "belle époque" to 1)),
        )

        val periods = libraryFilterNamedPeriods(LibraryFilterState(times = setOf("Cold War")), facets)

        assertEquals(listOf("World War II", "Belle Époque"), periods.map { it.value })
    }
}
