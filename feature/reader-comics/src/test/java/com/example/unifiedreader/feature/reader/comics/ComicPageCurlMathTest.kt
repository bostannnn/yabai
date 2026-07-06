package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPageCurlMathTest {
    @Test
    fun `shader surface is gated to api 33 and newer`() {
        assertFalse(isComicPageCurlShaderSupported(sdkInt = 32))
        assertTrue(isComicPageCurlShaderSupported(sdkInt = 33))
    }

    @Test
    fun `curl pager layer keeps slide path on unsupported api or slide style`() {
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Slide,
                sdkInt = 33,
            ),
        )
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                sdkInt = 32,
            ),
        )
        assertTrue(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                sdkInt = 33,
            ),
        )
    }

    @Test
    fun `curl availability requires curl style paged mode api 33 unzoomed and composites`() {
        val available = resolveComicPageCurlAvailability(
            pageTurnStyle = ComicPageTurnStyle.Curl,
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
                pageTurnStyle = ComicPageTurnStyle.Curl,
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
                pageTurnStyle = ComicPageTurnStyle.Slide,
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
                pageTurnStyle = ComicPageTurnStyle.Curl,
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
                pageTurnStyle = ComicPageTurnStyle.Curl,
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
            pageTurnStyle = ComicPageTurnStyle.Curl,
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
    fun `curl commit threshold commits past threshold and cancels at or below it`() {
        assertFalse(shouldCommitComicPageCurl(progress = 0.25f))
        assertTrue(shouldCommitComicPageCurl(progress = 0.251f))
        assertTrue(shouldCommitComicPageCurl(progress = 0.1f, velocity = 1.6f))
    }

    @Test
    fun `ltr drag left targets next and advances next progress`() {
        assertEquals(
            ComicPageCurlTarget.Next,
            resolveComicPageCurlTarget(
                horizontalDrag = -32f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            0.25f,
            comicPageCurlProgressDelta(
                dragAmount = -100f,
                containerWidth = 400f,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
            0.0001f,
        )
    }

    @Test
    fun `rtl drag right targets next and advances next progress`() {
        assertEquals(
            ComicPageCurlTarget.Next,
            resolveComicPageCurlTarget(
                horizontalDrag = 32f,
                readingDirection = ComicReadingDirection.RTL,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            0.25f,
            comicPageCurlProgressDelta(
                dragAmount = 100f,
                containerWidth = 400f,
                readingDirection = ComicReadingDirection.RTL,
                target = ComicPageCurlTarget.Next,
            ),
            0.0001f,
        )
    }

    @Test
    fun `previous progress mirrors next progress by reading direction`() {
        assertEquals(
            0.25f,
            comicPageCurlProgressDelta(
                dragAmount = 100f,
                containerWidth = 400f,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Previous,
            ),
            0.0001f,
        )
        assertEquals(
            0.25f,
            comicPageCurlProgressDelta(
                dragAmount = -100f,
                containerWidth = 400f,
                readingDirection = ComicReadingDirection.RTL,
                target = ComicPageCurlTarget.Previous,
            ),
            0.0001f,
        )
    }

    @Test
    fun `short drags do not pick a curl target`() {
        assertNull(
            resolveComicPageCurlTarget(
                horizontalDrag = 8f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
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

    @Test
    fun `progress delta is clamped into shader range`() {
        assertEquals(
            1f,
            applyComicPageCurlProgressDelta(
                progress = 0.9f,
                dragAmount = -200f,
                containerWidth = 400f,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            applyComicPageCurlProgressDelta(
                progress = 0.1f,
                dragAmount = 200f,
                containerWidth = 400f,
                readingDirection = ComicReadingDirection.LTR,
                target = ComicPageCurlTarget.Next,
            ),
            0.0001f,
        )
    }

    @Test
    fun `ltr next axis starts beyond right edge and finishes beyond left edge`() {
        val start = calculateComicPageCurlAxis(
            progress = 0f,
            tilt = 0f,
            containerWidth = 400f,
            containerHeight = 600f,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        )
        val end = calculateComicPageCurlAxis(
            progress = 1f,
            tilt = 0f,
            containerWidth = 400f,
            containerHeight = 600f,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Next,
        )

        assertTrue(start.originX > 400f)
        assertTrue(end.originX < 0f)
        assertEquals(1f, start.directionX, 0.0001f)
        assertEquals(0f, start.directionY, 0.0001f)
    }

    @Test
    fun `rtl next axis starts beyond left edge and finishes beyond right edge`() {
        val start = calculateComicPageCurlAxis(
            progress = 0f,
            tilt = 0f,
            containerWidth = 400f,
            containerHeight = 600f,
            readingDirection = ComicReadingDirection.RTL,
            target = ComicPageCurlTarget.Next,
        )
        val end = calculateComicPageCurlAxis(
            progress = 1f,
            tilt = 0f,
            containerWidth = 400f,
            containerHeight = 600f,
            readingDirection = ComicReadingDirection.RTL,
            target = ComicPageCurlTarget.Next,
        )

        assertTrue(start.originX < 0f)
        assertTrue(end.originX > 400f)
        assertEquals(-1f, start.directionX, 0.0001f)
        assertEquals(0f, start.directionY, 0.0001f)
    }

    @Test
    fun `tilt keeps axis direction normalized`() {
        val axis = calculateComicPageCurlAxis(
            progress = 0.5f,
            tilt = 0.7f,
            containerWidth = 400f,
            containerHeight = 600f,
            readingDirection = ComicReadingDirection.LTR,
            target = ComicPageCurlTarget.Previous,
        )

        assertTrue(axis.directionX < 0f)
        assertTrue(axis.directionY > 0f)
        assertEquals(1f, axis.directionX * axis.directionX + axis.directionY * axis.directionY, 0.0001f)
    }
}
