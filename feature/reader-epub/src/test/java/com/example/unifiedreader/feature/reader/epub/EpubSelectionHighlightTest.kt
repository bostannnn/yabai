package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.unifiedreader.ui.theme.ReaderPageColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSelectionHighlightTest {
    @Test
    fun `selected text stays readable on every page colour`() {
        ReaderPageColor.entries.forEach { pageColor ->
            val colors = pageColor.colors
            val fill = EpubSelectionHighlight.over(colors.page)
            val textContrast = contrast(colors.text.luminance(), fill.luminance())
            val fillContrast = contrast(colors.page.luminance(), fill.luminance())
            assertTrue("${pageColor.label}: text on the selection is $textContrast:1", textContrast >= 4.5)
            assertTrue("${pageColor.label}: the selection barely shows ($fillContrast:1)", fillContrast >= 1.3)
        }
    }

    @Test
    fun `css value matches the colour`() {
        val argb = EpubSelectionHighlight.color.toArgb()
        val css = "rgba(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}, " +
            "${Math.round(((argb ushr 24) / 255.0) * 100) / 100.0})"
        assertEquals(EpubSelectionHighlight.css, css)
    }

    private fun contrast(a: Float, b: Float): Double {
        val lighter = maxOf(a, b) + 0.05
        val darker = minOf(a, b) + 0.05
        return lighter / darker
    }
}
