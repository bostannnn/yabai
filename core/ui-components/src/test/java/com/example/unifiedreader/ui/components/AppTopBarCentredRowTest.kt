package com.example.unifiedreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTopBarCentredRowTest {

    @Test
    fun `a wide bar centres the field on the bar, whatever the ends hold`() {
        // Tablet landscape beside the rail: no menu button, Downloads and ••• at the end.
        val placement = appTopBarCentredPlacement(
            barWidth = 1128,
            leadingWidth = 0,
            trailingWidth = 96,
            maxCentreWidth = 640,
            gap = 4,
        )
        assertEquals(640, placement.width)
        assertEquals((1128 - 640) / 2, placement.x)
    }

    @Test
    fun `a field that would reach the buttons slides toward the roomier side`() {
        val placement = appTopBarCentredPlacement(
            barWidth = 800,
            leadingWidth = 48,
            trailingWidth = 96,
            maxCentreWidth = 640,
            gap = 4,
        )
        assertEquals(640, placement.width)
        // Centred it would end at 720; the buttons start at 704, so it stops 4 before them.
        assertEquals(800 - 96 - 4 - 640, placement.x)
    }

    @Test
    fun `a narrow bar fills the room between the buttons`() {
        // A phone: menu, field, two buttons.
        val placement = appTopBarCentredPlacement(
            barWidth = 380,
            leadingWidth = 48,
            trailingWidth = 96,
            maxCentreWidth = 640,
            gap = 4,
        )
        assertEquals(52, placement.x)
        assertEquals(380 - 96 - 4 - 52, placement.width)
    }
}
