package com.example.unifiedreader.feature.reader.pdf

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PdfZoomLayoutTest {
    @Test
    fun `page layout fits inside padded viewport`() {
        val layout = pdfPageZoomLayout(
            viewportSize = IntSize(width = 1000, height = 1000),
            imageSize = IntSize(width = 1000, height = 2000),
            contentPaddingPx = 20f,
        )

        assertEquals(Size(width = 480f, height = 960f), layout.contentSize)
        assertEquals(IntSize(width = 1000, height = 1000), layout.viewportSize)
    }

    @Test
    fun `invalid page layout remains unavailable`() {
        val layout = pdfPageZoomLayout(
            viewportSize = IntSize(width = 1000, height = 1000),
            imageSize = IntSize.Zero,
            contentPaddingPx = 20f,
        )

        assertFalse(layout.isReady)
    }

    @Test
    fun `render resolution increases in bounded buckets`() {
        assertEquals(2200, pdfRenderLongEdgeBucket(1000))
        assertEquals(3200, pdfRenderLongEdgeBucket(3000))
        assertEquals(4096, pdfRenderLongEdgeBucket(9000))
    }
}
