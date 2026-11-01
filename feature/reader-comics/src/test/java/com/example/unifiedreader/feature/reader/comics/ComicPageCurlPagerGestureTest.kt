package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPageCurlPagerGestureTest {
    @Test
    fun `valid adjacent target starts curl throughout reader surface`() {
        assertEquals(
            ComicPageCurlGestureStartPolicy.StartCurl,
            resolveComicPageCurlGestureStartPolicy(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = IntRect(left = 100, top = 0, right = 300, bottom = 300),
            ),
        )
    }

    @Test
    fun `valid adjacent target inside sheet starts curl`() {
        assertEquals(
            ComicPageCurlGestureStartPolicy.StartCurl,
            resolveComicPageCurlGestureStartPolicy(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = IntRect(left = 100, top = 0, right = 300, bottom = 300),
                transitionPolicy = ComicPageCurlTransitionPolicy.UseCurl,
            ),
        )
    }

    @Test
    fun `incompatible transition yields to pager even when down is inside sheet`() {
        assertEquals(
            ComicPageCurlGestureStartPolicy.YieldToPager,
            resolveComicPageCurlGestureStartPolicy(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = IntRect(left = 100, top = 0, right = 300, bottom = 300),
                transitionPolicy = ComicPageCurlTransitionPolicy.YieldToPager,
            ),
        )
    }

    @Test
    fun `missing adjacent target preserves pager fallback`() {
        assertEquals(
            ComicPageCurlGestureStartPolicy.YieldToPager,
            resolveComicPageCurlGestureStartPolicy(
                targetSpreadIndex = null,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = IntRect(left = 100, top = 0, right = 300, bottom = 300),
            ),
        )
    }

    @Test
    fun `missing composite preserves pager fallback`() {
        assertEquals(
            ComicPageCurlGestureStartPolicy.YieldToPager,
            resolveComicPageCurlGestureStartPolicy(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = false,
                sheetBounds = IntRect(left = 100, top = 0, right = 300, bottom = 300),
            ),
        )
    }

    @Test
    fun `empty sheet bounds preserve pager fallback`() {
        assertEquals(
            ComicPageCurlGestureStartPolicy.YieldToPager,
            resolveComicPageCurlGestureStartPolicy(
                targetSpreadIndex = 2,
                hasCurrentComposite = true,
                hasUnderneathComposite = true,
                sheetBounds = IntRect(left = 100, top = 0, right = 100, bottom = 300),
            ),
        )
    }
}
