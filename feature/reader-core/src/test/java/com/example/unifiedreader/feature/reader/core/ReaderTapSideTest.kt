package com.example.unifiedreader.feature.reader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapSideTest {
    @Test
    fun `a phone keeps a quarter of the width on each side`() {
        // 411 dp at 2.625: a quarter is 103 dp, under the 160 dp cap.
        val width = 411f * 2.625f
        val maxZone = 160f * 2.625f

        assertEquals(width * 0.25f, readerTapZoneWidth(width, maxZone), 0.01f)
        assertEquals(ReaderTapSide.Left, readerTapSide(width * 0.24f, width, maxZone))
        assertEquals(ReaderTapSide.Middle, readerTapSide(width * 0.5f, width, maxZone))
        assertEquals(ReaderTapSide.Right, readerTapSide(width * 0.76f, width, maxZone))
    }

    @Test
    fun `a tablet spread turns only within an inch of each edge`() {
        // The landscape tablet: 1292 dp, two pages of 646 dp.
        val density = 2.625f
        val width = 1292f * density
        val maxZone = 160f * density

        assertEquals(maxZone, readerTapZoneWidth(width, maxZone), 0.01f)
        assertEquals(ReaderTapSide.Left, readerTapSide(150f * density, width, maxZone))
        // The middle of either page opens the menu.
        assertEquals(ReaderTapSide.Middle, readerTapSide(323f * density, width, maxZone))
        assertEquals(ReaderTapSide.Middle, readerTapSide(969f * density, width, maxZone))
        assertEquals(ReaderTapSide.Right, readerTapSide(1142f * density, width, maxZone))
    }

    @Test
    fun `an unmeasured page has only a middle`() {
        assertEquals(ReaderTapSide.Middle, readerTapSide(5f, 0f, 100f))
    }
}
