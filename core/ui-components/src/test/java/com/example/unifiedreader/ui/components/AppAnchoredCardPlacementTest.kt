package com.example.unifiedreader.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class AppAnchoredCardPlacementTest {
    private val window = IntSize(1200, 1900)
    private val card = IntSize(380, 400)

    @Test
    fun `sits under the word, centred on it`() {
        val word = Rect(left = 500f, top = 300f, right = 600f, bottom = 330f)
        assertEquals(IntOffset(360, 342), placeAnchoredCard(word, card, window, gutter = 24, gap = 12))
    }

    @Test
    fun `goes over the word near the bottom of the page`() {
        val word = Rect(left = 500f, top = 1700f, right = 600f, bottom = 1730f)
        assertEquals(IntOffset(360, 1288), placeAnchoredCard(word, card, window, gutter = 24, gap = 12))
    }

    @Test
    fun `stays inside the gutter at the page edge`() {
        val word = Rect(left = 0f, top = 300f, right = 40f, bottom = 330f)
        assertEquals(24, placeAnchoredCard(word, card, window, gutter = 24, gap = 12).x)
    }

    @Test
    fun `centres without an anchor`() {
        assertEquals(IntOffset(410, 750), placeAnchoredCard(null, card, window, gutter = 24, gap = 12))
    }
}
