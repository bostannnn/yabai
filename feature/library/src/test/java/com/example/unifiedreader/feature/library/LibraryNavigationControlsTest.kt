package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryNavigationControlsTest {
    @Test
    fun `alphabet sections group numeric and symbolic titles under hash`() {
        val sections = alphabetNavigationSections(
            listOf(
                "B",
                "#",
                "A",
                "#",
                "Ж",
            ),
        )

        assertEquals(listOf("#", "A", "B", "Ж"), sections)
        assertEquals("#", "3 Devils".filterLetter { it })
        assertEquals("#", "...And Then Emily Was Gone".filterLetter { it })
        assertEquals("A", "akira".filterLetter { it })
    }

    @Test
    fun `section starts preserve first item for each section`() {
        val starts = alphabetSectionStarts(
            listOf(
                "#",
                "A",
                "A",
                "B",
                "B",
            ),
        )

        assertEquals(
            mapOf(
                "#" to 0,
                "A" to 1,
                "B" to 3,
            ),
            starts,
        )
    }

    @Test
    fun `active section ignores leading grid content`() {
        val sections = listOf("#", "A", "B")

        assertEquals(null, activeAlphabetSection(sections, firstVisibleItemIndex = 1, leadingContentItemCount = 2))
        assertEquals("#", activeAlphabetSection(sections, firstVisibleItemIndex = 2, leadingContentItemCount = 2))
        assertEquals("B", activeAlphabetSection(sections, firstVisibleItemIndex = 4, leadingContentItemCount = 2))
    }

    @Test
    fun `back to top appears after two grid rows`() {
        assertFalse(
            shouldShowLibraryBackToTop(
                firstVisibleItemIndex = 8,
                firstVisibleItemScrollOffset = 0,
                leadingContentItemCount = 2,
                columnCount = 4,
            ),
        )
        assertTrue(
            shouldShowLibraryBackToTop(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 1,
                leadingContentItemCount = 2,
                columnCount = 4,
            ),
        )
        assertTrue(
            shouldShowLibraryBackToTop(
                firstVisibleItemIndex = 10,
                firstVisibleItemScrollOffset = 0,
                leadingContentItemCount = 2,
                columnCount = 4,
            ),
        )
    }
}
