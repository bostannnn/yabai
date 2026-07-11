package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAlphabetRailTest {
    @Test
    fun `assembles sections with cumulative offsets, hash first, blanks unsectioned`() {
        // Buckets in sort order (as SQL ORDER BY MIN(sortKey) yields them): a blank-key group first
        // (unsectioned but occupies positions), then a digit group, then letters.
        val sections = assembleLibraryAlphabetSections(
            listOf(
                LibraryAlphabetBucket(firstChar = "", count = 1),
                LibraryAlphabetBucket(firstChar = "1", count = 2),
                LibraryAlphabetBucket(firstChar = "a", count = 3),
                LibraryAlphabetBucket(firstChar = "b", count = 2),
            ),
        )

        assertEquals(
            listOf(
                LibraryAlphabetSection(letter = "#", itemOffset = 1),
                LibraryAlphabetSection(letter = "A", itemOffset = 3),
                LibraryAlphabetSection(letter = "B", itemOffset = 6),
            ),
            sections,
        )
    }

    @Test
    fun `multiple non-letter buckets collapse to a single hash section at the first offset`() {
        val sections = assembleLibraryAlphabetSections(
            listOf(
                LibraryAlphabetBucket(firstChar = "1", count = 2),
                LibraryAlphabetBucket(firstChar = "-", count = 1),
                LibraryAlphabetBucket(firstChar = "c", count = 4),
            ),
        )

        assertEquals(
            listOf(
                LibraryAlphabetSection(letter = "#", itemOffset = 0),
                LibraryAlphabetSection(letter = "C", itemOffset = 3),
            ),
            sections,
        )
    }

    @Test
    fun `empty buckets produce no sections`() {
        assertEquals(emptyList<LibraryAlphabetSection>(), assembleLibraryAlphabetSections(emptyList()))
    }
}
