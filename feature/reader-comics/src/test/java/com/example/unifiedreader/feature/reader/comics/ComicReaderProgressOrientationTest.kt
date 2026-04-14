package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicReaderProgressOrientationTest {
    @Test
    fun `vertical scroll progress always uses ltr orientation`() {
        assertEquals(
            ComicReadingDirection.LTR,
            comicProgressBarDirection(
                readerMode = ComicReaderMode.VerticalScroll,
                readingDirection = ComicReadingDirection.RTL,
            ),
        )
    }

    @Test
    fun `paged progress preserves configured reading direction`() {
        assertEquals(
            ComicReadingDirection.RTL,
            comicProgressBarDirection(
                readerMode = ComicReaderMode.Paged,
                readingDirection = ComicReadingDirection.RTL,
            ),
        )
    }
}
