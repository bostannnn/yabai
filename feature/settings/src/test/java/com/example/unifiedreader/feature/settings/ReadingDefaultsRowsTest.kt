package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingDefaultsRowsTest {
    @Test
    fun `direction and mode lists join on the library id`() {
        val rows = buildReadingDefaultsRows(
            directionLibraries = listOf(
                ReadingDirectionLibraryUiState("a", "Comics", ComicReadingDirection.LTR),
                ReadingDirectionLibraryUiState("b", "Manga", ComicReadingDirection.RTL),
            ),
            modeLibraries = listOf(
                ReaderModeLibraryUiState("b", "Manga", ComicReaderMode.entries.first()),
                ReaderModeLibraryUiState("c", "Webtoons", ComicReaderMode.entries.last()),
            ),
        )

        assertEquals(listOf("a", "b", "c"), rows.map { it.libraryId })
        assertEquals(ComicReadingDirection.LTR, rows[0].direction)
        assertEquals(null, rows[0].mode)
        assertEquals(ComicReadingDirection.RTL, rows[1].direction)
        assertEquals(ComicReaderMode.entries.first(), rows[1].mode)
        assertEquals(null, rows[2].direction)
        assertEquals(ComicReaderMode.entries.last(), rows[2].mode)
    }
}
