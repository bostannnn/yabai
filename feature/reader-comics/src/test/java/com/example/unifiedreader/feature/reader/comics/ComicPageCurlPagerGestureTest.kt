package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPageCurlPagerGestureTest {
    @Test
    fun `valid adjacent target outside sheet is ignored instead of yielded`() {
        assertEquals(
            ComicPageCurlGestureStartResolution.Ignore,
            resolveComicPageCurlGestureStartResolution(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = ComicRasterRect(left = 100, top = 0, width = 200, height = 300),
                downPosition = Offset(50f, 150f),
            ),
        )
    }

    @Test
    fun `valid adjacent target inside sheet starts curl`() {
        assertEquals(
            ComicPageCurlGestureStartResolution.Start,
            resolveComicPageCurlGestureStartResolution(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = ComicRasterRect(left = 100, top = 0, width = 200, height = 300),
                downPosition = Offset(150f, 150f),
            ),
        )
    }

    @Test
    fun `missing adjacent target preserves pager fallback`() {
        assertEquals(
            ComicPageCurlGestureStartResolution.Yield,
            resolveComicPageCurlGestureStartResolution(
                targetSpreadIndex = null,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = ComicRasterRect(left = 100, top = 0, width = 200, height = 300),
                downPosition = Offset(50f, 150f),
            ),
        )
    }

    @Test
    fun `missing composite preserves pager fallback`() {
        assertEquals(
            ComicPageCurlGestureStartResolution.Yield,
            resolveComicPageCurlGestureStartResolution(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = false,
                sheetBounds = ComicRasterRect(left = 100, top = 0, width = 200, height = 300),
                downPosition = Offset(150f, 150f),
            ),
        )
    }

    @Test
    fun `empty sheet bounds preserve pager fallback`() {
        assertEquals(
            ComicPageCurlGestureStartResolution.Yield,
            resolveComicPageCurlGestureStartResolution(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = ComicRasterRect(left = 100, top = 0, width = 0, height = 300),
                downPosition = Offset(100f, 150f),
            ),
        )
    }
}
