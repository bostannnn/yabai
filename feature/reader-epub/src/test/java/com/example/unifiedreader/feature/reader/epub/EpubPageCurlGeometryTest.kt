package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.unit.IntRect
import com.example.unifiedreader.feature.reader.core.curl.PageCurlFollow
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPageCurlGeometryTest {
    private val view = IntRect(left = 0, top = 0, right = 1200, bottom = 1800)

    @Test
    fun `single page turns forward from the reading-start edge and comes back over it`() {
        val forward = resolveEpubPageCurlTurn(view, twoPageSpread = false, rightToLeft = false, target = PageCurlTarget.Next)
            ?: error("Expected a forward turn")
        val backward = resolveEpubPageCurlTurn(view, twoPageSpread = false, rightToLeft = false, target = PageCurlTarget.Previous)
            ?: error("Expected a backward turn")

        assertEquals(view, forward.paperBounds)
        assertEquals(view, forward.bookBounds)
        assertEquals(0f, forward.sheet.hingeX, 0.0001f)
        assertEquals(1f, forward.sheet.outward, 0.0001f)
        assertEquals(PageCurlFollow.Edge, forward.follow)
        assertFalse(forward.backIsFacingPage)
        assertEquals(PageCurlFollow.Fold, backward.follow)
        assertTrue(backward.sheetIsAdjacentPage)
    }

    @Test
    fun `right to left single page is hinged on the right`() {
        val forward = resolveEpubPageCurlTurn(view, twoPageSpread = false, rightToLeft = true, target = PageCurlTarget.Next)
            ?: error("Expected a forward turn")

        assertEquals(1200f, forward.sheet.hingeX, 0.0001f)
        assertEquals(-1f, forward.sheet.outward, 0.0001f)
    }

    @Test
    fun `spread turns its outgoing half over the middle and shows the facing page on its back`() {
        val forward = resolveEpubPageCurlTurn(view, twoPageSpread = true, rightToLeft = false, target = PageCurlTarget.Next)
            ?: error("Expected a forward turn")
        val backward = resolveEpubPageCurlTurn(view, twoPageSpread = true, rightToLeft = false, target = PageCurlTarget.Previous)
            ?: error("Expected a backward turn")
        val rtlForward = resolveEpubPageCurlTurn(view, twoPageSpread = true, rightToLeft = true, target = PageCurlTarget.Next)
            ?: error("Expected a forward turn")

        assertEquals(IntRect(left = 600, top = 0, right = 1200, bottom = 1800), forward.paperBounds)
        assertEquals(view, forward.bookBounds)
        assertEquals(600f, forward.sheet.hingeX, 0.0001f)
        assertEquals(1f, forward.sheet.outward, 0.0001f)
        assertEquals(PageCurlFollow.Edge, forward.follow)
        assertTrue(forward.backIsFacingPage)
        assertEquals(IntRect(left = 0, top = 0, right = 600, bottom = 1800), backward.paperBounds)
        assertEquals(600f, backward.sheet.hingeX, 0.0001f)
        assertEquals(-1f, backward.sheet.outward, 0.0001f)
        assertEquals(PageCurlFollow.Edge, backward.follow)
        assertEquals(IntRect(left = 0, top = 0, right = 600, bottom = 1800), rtlForward.paperBounds)
        assertEquals(-1f, rtlForward.sheet.outward, 0.0001f)
    }

    @Test
    fun `an empty view has no turn`() {
        assertNull(
            resolveEpubPageCurlTurn(
                viewBounds = IntRect(left = 0, top = 0, right = 0, bottom = 0),
                twoPageSpread = false,
                rightToLeft = false,
                target = PageCurlTarget.Next,
            ),
        )
    }
}
