package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicsReaderStateTest {
    @Test
    fun `page turn style defaults to slide`() {
        val state = comicsReaderState()

        assertEquals(PageTurnStyle.Slide, state.pageTurnStyle)
    }

    @Test
    fun `fit mode labels describe viewport behavior`() {
        assertEquals("Fit screen", ComicPageFitMode.FitPage.label)
        assertEquals("Fit width", ComicPageFitMode.FitWidth.label)
        assertEquals("Fit height", ComicPageFitMode.FitHeight.label)
    }

    @Test
    fun `empty reader message omits diagnostic internals`() {
        val item = ReadableItem(
            id = "item-42",
            libraryId = "library-1",
            sourceType = SourceType.KAVITA,
            title = "Private Comic",
            format = FormatType.CBZ,
        )
        val message = emptyComicReaderMessage(
            item = item,
            diagnostic = ComicPagesUnavailableDiagnostic(
                title = "Downloaded comic file was not found",
                detail = "itemId=item-42, remoteId=remote-9, storedUri=file:///private/path/book.cbz",
            ),
        )

        assertEquals("Downloaded comic file was not found\n\nPrivate Comic", message)
        assertFalse(message.contains("itemId="))
        assertFalse(message.contains("file:///private/path"))
        assertFalse(message.contains("remote-9"))
    }

    @Test
    fun `page settle keeps visible page and normalizes resume anchor`() {
        val state = comicsReaderState(
            initialSpreadMode = ComicSpreadMode.Double,
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
        val state = comicsReaderState(
            initialSpreadMode = ComicSpreadMode.Double,
        )

        assertTrue(state.restorePageIfUnchanged(4))
        assertEquals(4, state.currentPage)
        assertEquals(4, state.resumeAnchorPage)
    }

    @Test
    fun `restoring a newer page after the first settle moves the anchor the pager follows`() {
        val state = comicsReaderState(
            initialPage = 10,
            pageCount = 40,
            initialResumeAnchorPage = 10,
        )
        // The pager's first settle at open, then progress pulled after the reader opened.
        state.onPageSettled(pageIndex = 10, resumeAnchorPage = 10)

        assertTrue(state.restorePageIfUnchanged(30))
        assertEquals(30, state.currentPage)
        assertEquals(30, state.resumeAnchorPage)
    }

    @Test
    fun `restoring the page a webtoon anchor already points at keeps its scroll offset`() {
        val state = comicsReaderState(
            pageCount = 40,
            initialReaderMode = ComicReaderMode.VerticalScroll,
            initialResumeAnchorPage = 5,
            initialResumeScrollOffsetRatio = 0.4f,
        )

        assertTrue(state.restorePageIfUnchanged(5))
        assertEquals(5, state.resumeAnchorPage)
        assertEquals(0.4f, checkNotNull(state.resumeScrollOffsetRatio), 0.0001f)
    }

    @Test
    fun `explicit resume anchor restore happens only once`() {
        val state = comicsReaderState(
            initialPage = 3,
            initialSpreadMode = ComicSpreadMode.Double,
        )

        assertTrue(state.restoreResumeAnchorIfUnchanged(2))
        assertEquals(2, state.resumeAnchorPage)
        assertFalse(state.restoreResumeAnchorIfUnchanged(1))
        assertEquals(2, state.resumeAnchorPage)
    }

    @Test
    fun `switching to vertical scroll reanchors on current page and clears paged resume state`() {
        val state = comicsReaderState(
            initialPage = 2,
            initialSpreadMode = ComicSpreadMode.Double,
            initialResumeAnchorPage = 1,
            initialResumeSegment = ComicPageSegment.Full,
        )

        state.onReaderModeChanged(ComicReaderMode.VerticalScroll)

        assertEquals(ComicReaderMode.VerticalScroll, state.readerMode)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals(null, state.resumeSegment)
        assertEquals(null, state.resumeScrollOffsetRatio)
    }

    @Test
    fun `sync page count preserves current page and anchor when still in range`() {
        val state = comicsReaderState(
            initialPage = 1,
            initialSpreadMode = ComicSpreadMode.Double,
            initialResumeAnchorPage = 1,
        )

        state.onPageSettled(pageIndex = 2, resumeAnchorPage = 2)
        state.syncPageCount(4)

        assertEquals(2, state.currentPage)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals("3 / 4", state.progressLabel)
    }

    @Test
    fun `sync page count clamps current page and anchor when count shrinks`() {
        val state = comicsReaderState(
            initialPage = 4,
            initialSpreadMode = ComicSpreadMode.Double,
            initialResumeAnchorPage = 4,
        )

        state.syncPageCount(3)

        assertEquals(2, state.currentPage)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals("3 / 3", state.progressLabel)
    }

    @Test
    fun `page turn style restore respects explicit user selection`() {
        val state = comicsReaderState()

        assertTrue(state.restorePageTurnStyleIfUnchanged(PageTurnStyle.Curl))
        assertEquals(PageTurnStyle.Curl, state.pageTurnStyle)

        state.onPageTurnStyleChanged(PageTurnStyle.Slide)

        assertFalse(state.restorePageTurnStyleIfUnchanged(PageTurnStyle.Curl))
        assertEquals(PageTurnStyle.Slide, state.pageTurnStyle)
    }

    @Test
    fun `page turn style changes do not rewrite progress or resume state`() {
        val state = comicsReaderState(
            initialPage = 3,
            initialResumeAnchorPage = 2,
            initialResumeSegment = ComicPageSegment.Full,
        )

        state.onPageTurnStyleChanged(PageTurnStyle.Curl)
        state.onPageTurnStyleChanged(PageTurnStyle.Slide)

        assertEquals(3, state.currentPage)
        assertEquals(2, state.resumeAnchorPage)
        assertEquals(ComicPageSegment.Full, state.resumeSegment)
        assertEquals(null, state.resumeScrollOffsetRatio)
    }

    @Test
    fun `state saver restores page turn style and old payloads default to slide`() {
        val state = comicsReaderState()
        state.onPageTurnStyleChanged(PageTurnStyle.Curl)
        val saved = saveState(state)

        val restored = restoreState(saved)
        assertEquals(PageTurnStyle.Curl, restored.pageTurnStyle)

        val restoredOldPayload = restoreState(saved.dropLast(2))
        assertEquals(PageTurnStyle.Slide, restoredOldPayload.pageTurnStyle)
    }
}

private fun comicsReaderState(
    initialPage: Int = 0,
    pageCount: Int = 6,
    initialReaderMode: ComicReaderMode = ComicReaderMode.Paged,
    initialReadingDirection: ComicReadingDirection = ComicReadingDirection.LTR,
    initialPageFitMode: ComicPageFitMode = ComicPageFitMode.FitPage,
    initialSpreadMode: ComicSpreadMode = ComicSpreadMode.Auto,
    initialPageTurnStyle: PageTurnStyle = PageTurnStyle.Slide,
    initialSplitWidePages: Boolean = false,
    initialResumeAnchorPage: Int? = null,
    initialResumeSegment: ComicPageSegment? = null,
    initialResumeScrollOffsetRatio: Float? = null,
): ComicsReaderState = ComicsReaderState(
    initialPage = initialPage,
    pageCount = pageCount,
    initialReaderMode = initialReaderMode,
    initialReadingDirection = initialReadingDirection,
    initialPageFitMode = initialPageFitMode,
    initialSpreadMode = initialSpreadMode,
    initialPageTurnStyle = initialPageTurnStyle,
    initialSplitWidePages = initialSplitWidePages,
    initialResumeAnchorPage = initialResumeAnchorPage,
    initialResumeSegment = initialResumeSegment,
    initialResumeScrollOffsetRatio = initialResumeScrollOffsetRatio,
)

@Suppress("UNCHECKED_CAST")
private fun stateSaver(): Saver<ComicsReaderState, Any> =
    ComicsReaderState.saver(pageCount = 6) as Saver<ComicsReaderState, Any>

private fun saveState(
    state: ComicsReaderState,
): List<Any?> {
    val saved = with(stateSaver()) {
        AlwaysSaveableSaverScope.save(state)
    }
    return saved as? List<Any?> ?: error("Expected list state")
}

private fun restoreState(
    saved: List<Any?>,
): ComicsReaderState = stateSaver().restore(saved) ?: error("Expected restored state")

private object AlwaysSaveableSaverScope : SaverScope {
    override fun canBeSaved(
        value: Any,
    ): Boolean = true
}
