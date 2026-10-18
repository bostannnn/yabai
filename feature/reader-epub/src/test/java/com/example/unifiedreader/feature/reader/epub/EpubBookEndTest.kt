package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBookEndTest {
    @Test
    fun `a paginated book ends on the last page of its last resource, not where its last position starts`() {
        // A two-position book: the second position starts half-way, on page 2 of 4.
        assertFalse(isEpubBookEnd(isLastResource = true, isScrollMode = false, pageIndex = 1, totalPages = 4, scrolledToResourceEnd = null))
        assertTrue(isEpubBookEnd(isLastResource = true, isScrollMode = false, pageIndex = 3, totalPages = 4, scrolledToResourceEnd = null))
        // The last page of an earlier chapter.
        assertFalse(isEpubBookEnd(isLastResource = false, isScrollMode = false, pageIndex = 3, totalPages = 4, scrolledToResourceEnd = null))
        // The position saved when the navigator goes away carries no page count.
        assertFalse(isEpubBookEnd(isLastResource = true, isScrollMode = false, pageIndex = 0, totalPages = null, scrolledToResourceEnd = null))
    }

    @Test
    fun `a scrolled book ends when the last resource is scrolled to the bottom`() {
        // Scroll mode reports a single page per resource.
        assertFalse(isEpubBookEnd(isLastResource = true, isScrollMode = true, pageIndex = 0, totalPages = 1, scrolledToResourceEnd = false))
        assertTrue(isEpubBookEnd(isLastResource = true, isScrollMode = true, pageIndex = 0, totalPages = 1, scrolledToResourceEnd = true))
        assertFalse(isEpubBookEnd(isLastResource = false, isScrollMode = true, pageIndex = 0, totalPages = 1, scrolledToResourceEnd = true))
    }
}
