package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicsReaderStateTest {
    @Test
    fun `fit mode labels describe viewport behavior`() {
        assertEquals("Fit screen", ComicPageFitMode.FitPage.label)
        assertEquals("Fit width", ComicPageFitMode.FitWidth.label)
        assertEquals("Fit height", ComicPageFitMode.FitHeight.label)
    }

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

    @Test
    fun `sync page count preserves current page and anchor when still in range`() {
        val state = ComicsReaderState(
            initialPage = 1,
            pageCount = 6,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Double,
            initialSplitWidePages = false,
            initialResumeAnchorPage = 1,
            initialResumeSegment = null,
            initialResumeScrollOffsetRatio = null,
        )

        state.onPageSettled(pageIndex = 2, resumeAnchorPage = 2)
        state.syncPageCount(4)

        assertEquals(2, state.currentPage)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals("3 / 4", state.progressLabel)
    }

    @Test
    fun `sync page count clamps current page and anchor when count shrinks`() {
        val state = ComicsReaderState(
            initialPage = 4,
            pageCount = 6,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Double,
            initialSplitWidePages = false,
            initialResumeAnchorPage = 4,
            initialResumeSegment = null,
            initialResumeScrollOffsetRatio = null,
        )

        state.syncPageCount(3)

        assertEquals(2, state.currentPage)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals("3 / 3", state.progressLabel)
    }
}
