package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicsReaderSessionCodecTest {
    @Test
    fun `restore comic reader preferences defaults missing values`() {
        val restored = restoreComicReaderPreferences(
            ReaderSessionSnapshot(
                itemId = "comic-1",
                preferences = mapOf(
                    ComicsReadingDirectionKey to ComicReadingDirection.RTL.name,
                ),
            ),
        )

        requireNotNull(restored)
        assertEquals(ComicReaderMode.Paged, restored.readerMode)
        assertEquals(ComicReadingDirection.RTL, restored.readingDirection)
        assertEquals(ComicPageFitMode.FitPage, restored.pageFitMode)
        assertEquals(ComicSpreadMode.Auto, restored.spreadMode)
        assertEquals(PageTurnStyle.Slide, restored.pageTurnStyle)
        assertEquals(false, restored.splitWidePages)
        assertNull(restored.resumeAnchorPage)
        assertNull(restored.resumeSegment)
    }

    @Test
    fun `restore comic reader preferences returns null when no known keys exist`() {
        val restored = restoreComicReaderPreferences(
            ReaderSessionSnapshot(
                itemId = "comic-1",
                preferences = mapOf("epub.theme" to "Dark"),
            ),
        )

        assertNull(restored)
    }

    @Test
    fun `restore comic reader preferences includes resume anchor page`() {
        val restored = restoreComicReaderPreferences(
            ReaderSessionSnapshot(
                itemId = "comic-1",
                preferences = mapOf(
                    ComicsResumeAnchorPageKey to "12",
                    ComicsResumeSegmentKey to ComicPageSegment.RightHalf.name,
                ),
            ),
        )

        requireNotNull(restored)
        assertEquals(12, restored.resumeAnchorPage)
        assertEquals(ComicPageSegment.RightHalf, restored.resumeSegment)
    }

    @Test
    fun `restore comic reader preferences includes page turn style`() {
        val restored = restoreComicReaderPreferences(
            ReaderSessionSnapshot(
                itemId = "comic-1",
                preferences = mapOf(
                    ComicsPageTurnStyleKey to PageTurnStyle.Curl.name,
                ),
            ),
        )

        requireNotNull(restored)
        assertEquals(PageTurnStyle.Curl, restored.pageTurnStyle)
    }

    @Test
    fun `build comic reader preferences patch includes page turn style`() {
        val patch = buildComicReaderPreferencesPatch(
            pageTurnStyle = PageTurnStyle.Curl,
        )

        assertEquals(PageTurnStyle.Curl.name, patch[ComicsPageTurnStyleKey])
    }
}
