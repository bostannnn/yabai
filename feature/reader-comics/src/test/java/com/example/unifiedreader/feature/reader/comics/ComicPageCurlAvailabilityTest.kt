package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPageCurlAvailabilityTest {
    @Test
    fun `curl pager layer keeps slide path on unsupported api or slide style`() {
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = PageTurnStyle.Slide,
                sdkInt = 33,
            ),
        )
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = PageTurnStyle.Curl,
                readerMode = ComicReaderMode.VerticalScroll,
                sdkInt = 33,
            ),
        )
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = PageTurnStyle.Curl,
                sdkInt = 32,
            ),
        )
        assertTrue(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = PageTurnStyle.Curl,
                sdkInt = 33,
            ),
        )
    }

    @Test
    fun `curl availability requires curl style paged mode api 33 unzoomed and composites`() {
        val available = resolveComicPageCurlAvailability(
            pageTurnStyle = PageTurnStyle.Curl,
            readerMode = ComicReaderMode.Paged,
            sdkInt = 33,
            isCurrentPageZoomed = false,
            hasCurrentComposite = true,
            hasAdjacentComposite = true,
            containerSize = IntSize(400, 600),
        )

        assertTrue(available.isAvailable)
        assertNull(available.reason)
    }

    @Test
    fun `curl availability falls back for unsupported api slide style scroll mode or missing composites`() {
        assertEquals(
            ComicPageCurlUnavailableReason.UnsupportedApi,
            resolveComicPageCurlAvailability(
                pageTurnStyle = PageTurnStyle.Curl,
                readerMode = ComicReaderMode.Paged,
                sdkInt = 32,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = true,
            ).reason,
        )
        assertEquals(
            ComicPageCurlUnavailableReason.PageTurnStyleNotCurl,
            resolveComicPageCurlAvailability(
                pageTurnStyle = PageTurnStyle.Slide,
                readerMode = ComicReaderMode.Paged,
                sdkInt = 33,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = true,
            ).reason,
        )
        assertEquals(
            ComicPageCurlUnavailableReason.ReaderModeNotPaged,
            resolveComicPageCurlAvailability(
                pageTurnStyle = PageTurnStyle.Curl,
                readerMode = ComicReaderMode.VerticalScroll,
                sdkInt = 33,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = true,
            ).reason,
        )
        assertEquals(
            ComicPageCurlUnavailableReason.MissingAdjacentComposite,
            resolveComicPageCurlAvailability(
                pageTurnStyle = PageTurnStyle.Curl,
                readerMode = ComicReaderMode.Paged,
                sdkInt = 33,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = false,
            ).reason,
        )
    }

    @Test
    fun `curl availability is disabled while current page is zoomed`() {
        val availability = resolveComicPageCurlAvailability(
            pageTurnStyle = PageTurnStyle.Curl,
            readerMode = ComicReaderMode.Paged,
            sdkInt = 33,
            isCurrentPageZoomed = true,
            hasCurrentComposite = true,
            hasAdjacentComposite = true,
        )

        assertFalse(availability.isAvailable)
        assertEquals(ComicPageCurlUnavailableReason.Zoomed, availability.reason)
    }

    @Test
    fun `target spread selection keeps reading order for ltr and rtl drags`() {
        assertEquals(
            2,
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = -32f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
                currentSpreadIndex = 1,
                spreadCount = 4,
            ),
        )
        assertEquals(
            2,
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = 32f,
                readingDirection = ComicReadingDirection.RTL,
                touchSlop = 8f,
                currentSpreadIndex = 1,
                spreadCount = 4,
            ),
        )
        assertEquals(
            0,
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = 32f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
                currentSpreadIndex = 1,
                spreadCount = 4,
            ),
        )
        assertNull(
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = -32f,
                readingDirection = ComicReadingDirection.RTL,
                touchSlop = 8f,
                currentSpreadIndex = 0,
                spreadCount = 4,
            ),
        )
    }

}
