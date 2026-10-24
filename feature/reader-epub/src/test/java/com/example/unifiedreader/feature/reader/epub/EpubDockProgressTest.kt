package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubDockProgressTest {

    @Test
    fun `the last page reads 100 percent though Readium stops short of 1`() {
        val progress = epubDockProgress(progress = 0.986, position = 73, pageCount = 73)

        assertEquals(1.0, progress, 0.0)
        assertEquals("100%", buildEpubPercentLabel(progress))
    }

    @Test
    fun `a page before the last never rounds up to 100 percent`() {
        val progress = epubDockProgress(progress = 0.997, position = 72, pageCount = 73)

        assertEquals("99%", buildEpubPercentLabel(progress))
    }

    @Test
    fun `earlier pages keep their progression`() {
        assertEquals(0.29, epubDockProgress(progress = 0.29, position = 22, pageCount = 73), 0.0)
    }

    @Test
    fun `without known pages the progression shows as it is`() {
        assertEquals(1.0, epubDockProgress(progress = 1.0, position = 0, pageCount = 0), 0.0)
        assertEquals(0.4, epubDockProgress(progress = 0.4, position = 12, pageCount = 0), 0.0)
    }
}
