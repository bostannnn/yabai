package com.example.unifiedreader.feature.reader.comics

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
