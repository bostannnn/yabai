package com.example.unifiedreader.feature.reader.comics

import org.junit.Assert.assertEquals
import org.junit.Test

class ComicsReaderChromeLabelsTest {
    @Test
    fun `the dock and the Pages panel say where you are`() {
        assertEquals("2–3 / 8", comicDockPositionLabel(setOf(2, 1), pageCount = 8))
        assertEquals("5 / 8", comicDockPositionLabel(setOf(4), pageCount = 8))
        assertEquals("Pages 2–3 of 8", comicPagesPositionLabel(setOf(1, 2), pageCount = 8))
        assertEquals("Page 5 of 8", comicPagesPositionLabel(setOf(4), pageCount = 8))
        assertEquals("Neon Ronin #2 · 8 pages", comicPagesSubtitle(issueLabel = "Neon Ronin #2", pageCount = 8))
        assertEquals("38%", comicPercentLabel(0.375f))
    }
}
