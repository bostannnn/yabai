package com.example.unifiedreader.feature.reader.core

import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderDockProgressTest {
    @Test
    fun `rtl progress is drawn from the right edge`() {
        assertEquals(
            ReaderProgressFillRange(
                startFraction = 0.75f,
                endFraction = 1f,
            ),
            resolveReaderProgressFillRange(
                progress = 0.25f,
                readingDirection = ComicReadingDirection.RTL,
            ),
        )
    }

    @Test
    fun `rtl progress selection maps right edge to start of book`() {
        assertEquals(
            0f,
            resolveReaderProgressSelection(
                horizontalFraction = 1f,
                readingDirection = ComicReadingDirection.RTL,
            ),
        )
    }

    @Test
    fun `ltr progress selection remains left to right`() {
        assertEquals(
            0.25f,
            resolveReaderProgressSelection(
                horizontalFraction = 0.25f,
                readingDirection = ComicReadingDirection.LTR,
            ),
        )
    }
}
