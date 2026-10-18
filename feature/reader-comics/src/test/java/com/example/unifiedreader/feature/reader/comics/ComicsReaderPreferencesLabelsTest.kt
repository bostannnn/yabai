package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicsReaderPreferencesLabelsTest {

    @Test
    fun `direction segments are words, not the LTR and RTL abbreviations`() {
        assertEquals("Left to right", comicReadingDirectionLabel(ComicReadingDirection.LTR))
        assertEquals("Right to left", comicReadingDirectionLabel(ComicReadingDirection.RTL))
    }

    @Test
    fun `fit segments drop the verb the row label carries`() {
        assertEquals(
            listOf("Screen", "Width", "Height"),
            ComicPageFitMode.entries.map(::comicPageFitLabel),
        )
    }

    @Test
    fun `every option has a distinct label`() {
        assertEquals(
            ComicReadingDirection.entries.size,
            ComicReadingDirection.entries.map(::comicReadingDirectionLabel).toSet().size,
        )
        assertEquals(
            ComicPageFitMode.entries.size,
            ComicPageFitMode.entries.map(::comicPageFitLabel).toSet().size,
        )
    }
}
