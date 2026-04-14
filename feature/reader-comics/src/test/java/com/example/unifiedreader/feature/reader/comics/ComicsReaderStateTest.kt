package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicsReaderStateTest {
    @Test
    fun `page settle keeps visible page and normalizes resume anchor`() {
        val state = ComicsReaderState(
            initialPage = 0,
            pageCount = 6,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Double,
            initialSplitWidePages = false,
            initialResumeAnchorPage = null,
            initialResumeSegment = null,
            initialResumeScrollOffsetRatio = null,
        )

        state.onPageSettled(
            pageIndex = 2,
            resumeAnchorPage = 1,
            segment = null,
        )

        assertEquals(2, state.currentPage)
        assertEquals(1, state.resumeAnchorPage)
    }

    @Test
    fun `restoring page also restores resume anchor when no explicit anchor exists`() {
        val state = ComicsReaderState(
            initialPage = 0,
            pageCount = 6,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Double,
            initialSplitWidePages = false,
            initialResumeAnchorPage = null,
            initialResumeSegment = null,
            initialResumeScrollOffsetRatio = null,
        )

        assertTrue(state.restorePageIfUnchanged(4))
        assertEquals(4, state.currentPage)
        assertEquals(4, state.resumeAnchorPage)
    }

    @Test
    fun `explicit resume anchor restore happens only once`() {
        val state = ComicsReaderState(
            initialPage = 3,
            pageCount = 6,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Double,
            initialSplitWidePages = false,
            initialResumeAnchorPage = null,
            initialResumeSegment = null,
            initialResumeScrollOffsetRatio = null,
        )

        assertTrue(state.restoreResumeAnchorIfUnchanged(2))
        assertEquals(2, state.resumeAnchorPage)
        assertFalse(state.restoreResumeAnchorIfUnchanged(1))
        assertEquals(2, state.resumeAnchorPage)
    }

    @Test
    fun `switching to vertical scroll reanchors on current page and clears paged resume state`() {
        val state = ComicsReaderState(
            initialPage = 2,
            pageCount = 6,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Double,
            initialSplitWidePages = false,
            initialResumeAnchorPage = 1,
            initialResumeSegment = ComicPageSegment.Full,
            initialResumeScrollOffsetRatio = null,
        )

        state.onReaderModeChanged(ComicReaderMode.VerticalScroll)

        assertEquals(ComicReaderMode.VerticalScroll, state.readerMode)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals(null, state.resumeSegment)
        assertEquals(null, state.resumeScrollOffsetRatio)
    }
}
