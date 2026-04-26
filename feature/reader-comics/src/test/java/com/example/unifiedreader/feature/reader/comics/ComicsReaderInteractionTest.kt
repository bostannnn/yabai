package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicsReaderInteractionTest {
    @Test
    fun `spread builder keeps cover as single page and pairs later pages for ltr`() {
        val pages = samplePages(count = 5)

        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        )

        assertEquals(listOf(0), spreads[0].pageIndexes)
        assertEquals(listOf(1, 2), spreads[1].pageIndexes)
        assertEquals(listOf(3, 4), spreads[2].pageIndexes)
        assertEquals(1, spreads[1].resumePageIndex)
        assertEquals("2-3 / 5", spreads[1].progressLabel)
    }

    @Test
    fun `spread builder reverses display order for rtl but keeps resume page stable`() {
        val pages = samplePages(count = 4)

        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.RTL,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        )

        assertEquals(listOf(2, 1), spreads[1].pageIndexes)
        assertEquals(1, spreads[1].resumePageIndex)
        assertEquals(1, spreads.indexOfPage(1))
        assertEquals(1, spreads.indexOfPage(2))
    }

    @Test
    fun `spread builder isolates wide pages in double page mode`() {
        val pages = samplePages(count = 5)

        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = mapOf(
                pages[1].id to ComicPageMetadata(width = 2400, height = 1200),
            ),
        )

        assertEquals(listOf(0), spreads[0].pageIndexes)
        assertEquals(listOf(1), spreads[1].pageIndexes)
        assertEquals(listOf(2), spreads[2].pageIndexes)
        assertEquals(listOf(3, 4), spreads[3].pageIndexes)
    }

    @Test
    fun `single page mode keeps wide pages as one page`() {
        val pages = samplePages(count = 2)

        val spreads = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.RTL,
            useDoublePageSpreads = false,
            pageMetadataById = mapOf(
                pages[0].id to ComicPageMetadata(width = 2400, height = 1200),
            ),
        )

        assertEquals(listOf(0), spreads[0].pageIndexes)
        assertEquals("spread-0-full", spreads[0].id)
        assertEquals(listOf(1), spreads[1].pageIndexes)
    }

    @Test
    fun `preview width allows wide pages to stay visibly landscape`() {
        val page = ComicSpreadPage(
            spec = samplePages(count = 1).single(),
            metadata = ComicPageMetadata(width = 2400, height = 1200),
        )

        val width = previewPageWidth(
            spreadPage = page,
            result = null,
            previewHeight = 176.dp,
        )

        assertEquals(352.dp, width)
    }

    @Test
    fun `odd width split windows cover the full source image`() {
        val leftWindow = ComicPageSegment.LeftHalf.sourceWindow(
            imageWidth = 5,
            imageHeight = 10,
        )
        val rightWindow = ComicPageSegment.RightHalf.sourceWindow(
            imageWidth = 5,
            imageHeight = 10,
        )

        assertEquals(0, leftWindow.offsetX)
        assertEquals(3, leftWindow.width)
        assertEquals(3, rightWindow.offsetX)
        assertEquals(2, rightWindow.width)
        assertEquals(5, leftWindow.width + rightWindow.width)
    }

    @Test
    fun `split last page only finishes on terminal half`() {
        assertFalse(
            isComicProgressFinished(
                pageIndex = 4,
                pageCount = 5,
                segment = ComicPageSegment.LeftHalf,
                readingDirection = ComicReadingDirection.LTR,
            ),
        )
        assertTrue(
            isComicProgressFinished(
                pageIndex = 4,
                pageCount = 5,
                segment = ComicPageSegment.RightHalf,
                readingDirection = ComicReadingDirection.LTR,
            ),
        )
        assertFalse(
            isComicProgressFinished(
                pageIndex = 4,
                pageCount = 5,
                segment = ComicPageSegment.RightHalf,
                readingDirection = ComicReadingDirection.RTL,
            ),
        )
        assertTrue(
            isComicProgressFinished(
                pageIndex = 4,
                pageCount = 5,
                segment = ComicPageSegment.LeftHalf,
                readingDirection = ComicReadingDirection.RTL,
            ),
        )
    }

    @Test
    fun `settled final spread stores last visible page as completed progress`() {
        val pages = samplePages(count = 5)
        val finalSpread = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        ).last()

        val settledPosition = finalSpread.settledReadingPosition(
            currentPage = 2,
            pageCount = pages.size,
        )

        assertEquals(4, settledPosition.pageIndex)
        assertEquals(3, settledPosition.resumeAnchorPage)
        assertTrue(
            isComicProgressFinished(
                pageIndex = settledPosition.pageIndex,
                pageCount = pages.size,
                segment = settledPosition.segment,
                readingDirection = ComicReadingDirection.LTR,
            ),
        )
    }

    @Test
    fun `settled middle spread keeps existing current page when it is visible`() {
        val pages = samplePages(count = 6)
        val middleSpread = buildComicPageSpreads(
            pages = pages,
            readingDirection = ComicReadingDirection.LTR,
            useDoublePageSpreads = true,
            pageMetadataById = emptyMap(),
        )[1]

        val settledPosition = middleSpread.settledReadingPosition(
            currentPage = 2,
            pageCount = pages.size,
        )

        assertEquals(2, settledPosition.pageIndex)
        assertEquals(1, settledPosition.resumeAnchorPage)
    }

    @Test
    fun `terminal forward swipe resolves by reading direction`() {
        assertTrue(
            isTerminalForwardSwipe(
                horizontalDrag = -24f,
                verticalDrag = 2f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
        assertFalse(
            isTerminalForwardSwipe(
                horizontalDrag = 24f,
                verticalDrag = 2f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
        assertTrue(
            isTerminalForwardSwipe(
                horizontalDrag = 24f,
                verticalDrag = 2f,
                readingDirection = ComicReadingDirection.RTL,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `terminal forward swipe ignores short or mostly vertical drags`() {
        assertFalse(
            isTerminalForwardSwipe(
                horizontalDrag = -4f,
                verticalDrag = 0f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
        assertFalse(
            isTerminalForwardSwipe(
                horizontalDrag = -24f,
                verticalDrag = 32f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `auto spread mode prefers large or landscape layouts`() {
        assertTrue(
            shouldUseDoublePageSpreads(
                spreadMode = ComicSpreadMode.Auto,
                maxWidthDp = 900f,
                maxHeightDp = 1200f,
                pageCount = 10,
            ),
        )
        assertTrue(
            shouldUseDoublePageSpreads(
                spreadMode = ComicSpreadMode.Auto,
                maxWidthDp = 800f,
                maxHeightDp = 600f,
                pageCount = 10,
            ),
        )
        assertFalse(
            shouldUseDoublePageSpreads(
                spreadMode = ComicSpreadMode.Auto,
                maxWidthDp = 500f,
                maxHeightDp = 900f,
                pageCount = 10,
            ),
        )
    }

    @Test
    fun `double page committed metadata indexing only requires current spread prefix`() {
        assertEquals(
            listOf(4, 5, 6, 7),
            requiredMetadataIndexes(
                currentPage = 6,
                pageCount = 10,
                useDoublePageSpreads = true,
            ),
        )
    }

    @Test
    fun `double page background metadata indexing keeps warm lookahead prefix`() {
        assertEquals(
            listOf(4, 5, 6, 7, 8, 9),
            backgroundMetadataIndexes(
                currentPage = 6,
                pageCount = 10,
                useDoublePageSpreads = true,
            ),
        )
    }

    @Test
    fun `single page committed metadata indexing stays local when split wide pages disabled`() {
        assertEquals(
            listOf(4),
            requiredMetadataIndexes(
                currentPage = 4,
                pageCount = 8,
                useDoublePageSpreads = false,
            ),
        )
    }

    @Test
    fun `single page background metadata indexing stays local to reading window`() {
        assertEquals(
            listOf(4, 5, 3, 6, 2),
            backgroundMetadataIndexes(
                currentPage = 4,
                pageCount = 8,
                useDoublePageSpreads = false,
            ),
        )
    }

    @Test
    fun `legacy split resume segment is sanitized away`() {
        val state = ComicsReaderState(
            initialPage = 0,
            pageCount = 5,
            initialReaderMode = ComicReaderMode.Paged,
            initialReadingDirection = ComicReadingDirection.LTR,
            initialPageFitMode = ComicPageFitMode.FitPage,
            initialSpreadMode = ComicSpreadMode.Single,
            initialSplitWidePages = false,
            initialResumeAnchorPage = 4,
            initialResumeSegment = ComicPageSegment.LeftHalf,
            initialResumeScrollOffsetRatio = null,
        )

        assertEquals(null, state.resumeSegment)
        assertFalse(state.restoreResumeSegmentIfUnchanged(ComicPageSegment.RightHalf))
        assertEquals(null, state.resumeSegment)
    }

    @Test
    fun `tap action uses edge zones for ltr reading`() {
        assertEquals(
            ReaderTapAction.Previous,
            resolveReaderTapAction(
                tapX = 10f,
                containerWidth = 100f,
                readingDirection = ComicReadingDirection.LTR,
                isZoomed = false,
            ),
        )
        assertEquals(
            ReaderTapAction.ToggleOverlay,
            resolveReaderTapAction(
                tapX = 50f,
                containerWidth = 100f,
                readingDirection = ComicReadingDirection.LTR,
                isZoomed = false,
            ),
        )
        assertEquals(
            ReaderTapAction.Next,
            resolveReaderTapAction(
                tapX = 95f,
                containerWidth = 100f,
                readingDirection = ComicReadingDirection.LTR,
                isZoomed = false,
            ),
        )
    }

    @Test
    fun `tap action mirrors edge zones for rtl reading`() {
        assertEquals(
            ReaderTapAction.Next,
            resolveReaderTapAction(
                tapX = 10f,
                containerWidth = 100f,
                readingDirection = ComicReadingDirection.RTL,
                isZoomed = false,
            ),
        )
        assertEquals(
            ReaderTapAction.Previous,
            resolveReaderTapAction(
                tapX = 95f,
                containerWidth = 100f,
                readingDirection = ComicReadingDirection.RTL,
                isZoomed = false,
            ),
        )
    }

    @Test
    fun `tap action falls back to overlay when zoomed or width is invalid`() {
        assertEquals(
            ReaderTapAction.ToggleOverlay,
            resolveReaderTapAction(
                tapX = 5f,
                containerWidth = 100f,
                readingDirection = ComicReadingDirection.LTR,
                isZoomed = true,
            ),
        )
        assertEquals(
            ReaderTapAction.ToggleOverlay,
            resolveReaderTapAction(
                tapX = 5f,
                containerWidth = 0f,
                readingDirection = ComicReadingDirection.LTR,
                isZoomed = false,
            ),
        )
    }
}

private fun samplePages(
    count: Int,
): List<ComicPageSpec> = List(count) { index ->
    ComicPageSpec(
        id = "page-$index",
        pageIndex = index,
        contentDescription = "Page ${index + 1}",
    )
}
