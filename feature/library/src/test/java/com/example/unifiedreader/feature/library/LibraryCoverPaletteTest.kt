package com.example.unifiedreader.feature.library

import com.example.unifiedreader.ui.theme.Hsl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCoverPaletteTest {
    @Test
    fun `the two biggest colours are the ambient pair and a vivid one is the accent`() {
        // A dusk cover: mostly indigo sky, a band of mauve hills, a small coral sun.
        val pixels = cover(
            0xFF2B2F4E.toInt() to 3000,
            0xFF5D3C50.toInt() to 2000,
            0xFFE07A62.toInt() to 400,
        )

        val swatches = coverSwatches(pixels)!!

        assertNear(0xFF2B2F4E.toInt(), swatches.ambient)
        assertNear(0xFF5D3C50.toInt(), swatches.ambientSecond)
        assertNear(0xFFE07A62.toInt(), swatches.accent!!)
    }

    @Test
    fun `near shades fold into one colour`() {
        val pixels = cover(
            0xFF2B2F4E.toInt() to 2000,
            0xFF2F3352.toInt() to 2000,
            0xFFD9C9A0.toInt() to 1000,
        )

        val swatches = coverSwatches(pixels)!!

        // The two indigos are one cluster, so the second ambient colour is the cream.
        assertNear(0xFFD9C9A0.toInt(), swatches.ambientSecond, tolerance = 4)
    }

    @Test
    fun `a grey cover has no accent`() {
        val pixels = cover(
            0xFFF2F2F2.toInt() to 3000,
            0xFF202020.toInt() to 2000,
            0xFF808080.toInt() to 400,
        )

        assertNull(coverSwatches(pixels)!!.accent)
    }

    @Test
    fun `too dark or too light colours do not become the accent`() {
        val pixels = cover(
            0xFF101010.toInt() to 3000,
            // Saturated but nearly black: L under 0.22.
            0xFF300808.toInt() to 2000,
            // A saturated mid green, smaller but usable.
            0xFF3A8F2C.toInt() to 300,
        )

        val accent = coverSwatches(pixels)!!.accent
        assertNotNull(accent)
        assertTrue(Hsl.of(accent!!).h in 100f..120f)
    }

    @Test
    fun `transparent pixels are ignored and an empty cover has no swatches`() {
        assertNull(coverSwatches(IntArray(100) { 0x00FF0000 }))
        val swatches = coverSwatches(IntArray(100) { 0x00FF0000 } + cover(0xFF2B2F4E.toInt() to 10))!!
        assertNear(0xFF2B2F4E.toInt(), swatches.ambient)
    }

    private fun cover(vararg colors: Pair<Int, Int>): IntArray =
        colors.flatMap { (argb, count) -> List(count) { argb } }.toIntArray()

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2) {
        listOf(16, 8, 0).forEach { shift ->
            val delta = ((expected shr shift) and 0xFF) - ((actual shr shift) and 0xFF)
            assertTrue(
                "expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}",
                kotlin.math.abs(delta) <= tolerance,
            )
        }
        assertEquals(0xFF, (actual ushr 24))
    }
}
