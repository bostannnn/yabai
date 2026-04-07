package com.example.unifiedreader.feature.reader.comics

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
        )

        assertEquals(listOf(2, 1), spreads[1].pageIndexes)
        assertEquals(1, spreads[1].resumePageIndex)
        assertEquals(1, spreads.indexOfPage(1))
        assertEquals(1, spreads.indexOfPage(2))
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
        contentDescription = "Page ${index + 1}",
    )
}
