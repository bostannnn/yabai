package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderPreferencesModelTest {

    @Test
    fun `pages choice is Auto while the orientation default is on, whatever the stored mode`() {
        EpubReaderLayoutMode.entries.forEach { mode ->
            assertEquals(mode.name, EpubPagesChoice.Auto, epubPagesChoice(useOrientationLayoutDefault = true, layoutMode = mode))
        }
    }

    @Test
    fun `pages choice maps each fixed layout and back`() {
        EpubReaderLayoutMode.entries.forEach { mode ->
            val choice = epubPagesChoice(useOrientationLayoutDefault = false, layoutMode = mode)
            assertEquals(mode, choice.layoutMode)
        }
    }

    @Test
    fun `text size steps by a tenth on clean values and stops at the ends`() {
        assertEquals(1.1, stepEpubFontSize(1.0, +1), 1e-9)
        assertEquals(0.9, stepEpubFontSize(1.0, -1), 1e-9)
        assertEquals(1.2, stepEpubFontSize(1.14, +1), 1e-9)
        assertEquals(EpubFontSizeMax, stepEpubFontSize(EpubFontSizeMax, +1), 1e-9)
        assertEquals(EpubFontSizeMin, stepEpubFontSize(EpubFontSizeMin, -1), 1e-9)
        assertEquals(EpubFontSizeMax, stepEpubFontSize(2.35, +1), 1e-9)
    }

    @Test
    fun `stepper buttons disable at the range ends only`() {
        assertTrue(canStepEpubFontSize(1.0, +1))
        assertTrue(canStepEpubFontSize(1.0, -1))
        assertFalse(canStepEpubFontSize(EpubFontSizeMax, +1))
        assertTrue(canStepEpubFontSize(EpubFontSizeMax, -1))
        assertFalse(canStepEpubFontSize(EpubFontSizeMin, -1))
        assertTrue(canStepEpubFontSize(EpubFontSizeMin, +1))
    }

    @Test
    fun `alignment segments are one short word each`() {
        assertEquals(listOf("Auto", "Left", "Justify"), EpubReaderTextAlign.entries.map(::epubTextAlignLabel))
    }

    @Test
    fun `typography mode wording`() {
        assertEquals("Book's own", epubTypographyModeLabel(EpubTypographyMode.Original))
        assertEquals("Custom", epubTypographyModeLabel(EpubTypographyMode.Custom))
    }
}
