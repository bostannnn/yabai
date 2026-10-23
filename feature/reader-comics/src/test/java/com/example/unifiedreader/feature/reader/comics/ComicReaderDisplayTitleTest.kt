package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicReaderDisplayTitleTest {
    @Test
    fun `an issue reads as its series and number, over its place in the series`() {
        val item = displayTitleItem(
            title = "Neon-Ronin-002",
            comicMetadata = ComicMetadata(series = "Neon Ronin", issueTitle = "Neon Ronin #2", issueNumber = "2"),
        )

        assertEquals(ComicReaderTitles("Neon Ronin #2", "Neon Ronin · issue 2"), comicReaderTitles(item))
    }

    @Test
    fun `an issue with a title of its own shows it under the number`() {
        val item = displayTitleItem(
            title = "Swamp Thing (1986)",
            comicMetadata = ComicMetadata(
                series = "Swamp Thing (1986)",
                issueTitle = "Issue #52 - Natural Consequences",
                issueNumber = "52",
            ),
        )

        assertEquals(
            ComicReaderTitles("Swamp Thing (1986) #52", "Issue #52 - Natural Consequences"),
            comicReaderTitles(item),
        )
    }

    @Test
    fun `a Kavita volume reads as the series over Volume 1`() {
        val item = displayTitleItem(
            title = "Chapter v1",
            comicMetadata = ComicMetadata(series = "Goodbye, Eri", issueTitle = "Chapter v1", issueNumber = "v1"),
        )

        assertEquals(ComicReaderTitles("Goodbye, Eri", "Volume 1"), comicReaderTitles(item))
    }

    @Test
    fun `the series over the file title, or the title alone without a series`() {
        assertEquals(
            ComicReaderTitles("One Piece", "Romance Dawn"),
            comicReaderTitles(displayTitleItem(title = "Romance Dawn", comicMetadata = ComicMetadata(series = "One Piece"))),
        )
        assertEquals(
            ComicReaderTitles("An Extraordinarily Long Comic Title", null),
            comicReaderTitles(displayTitleItem(title = "An Extraordinarily Long Comic Title", comicMetadata = null)),
        )
        assertEquals(
            ComicReaderTitles("One Piece", null),
            comicReaderTitles(displayTitleItem(title = "One Piece", comicMetadata = ComicMetadata(series = "One Piece"))),
        )
    }

    @Test
    fun `generated names and impossible numbers are left out`() {
        val volume = displayTitleItem(
            title = "Chapter -100000",
            comicMetadata = ComicMetadata(series = "One Piece", volume = "2"),
        )
        val badNumber = displayTitleItem(
            title = "Volume 2.cbz",
            comicMetadata = ComicMetadata(series = "One Piece", issueNumber = "-10000"),
        )

        assertEquals(ComicReaderTitles("One Piece", "Volume 2"), comicReaderTitles(volume))
        assertEquals(ComicReaderTitles("One Piece", "Volume 2.cbz"), comicReaderTitles(badNumber))
    }
}

private fun displayTitleItem(
    title: String,
    comicMetadata: ComicMetadata?,
): ReadableItem = ReadableItem(
    id = "local:item:comic",
    sourceType = SourceType.LOCAL,
    libraryId = "local:library",
    title = title,
    format = FormatType.CBZ,
    comicMetadata = comicMetadata,
)
