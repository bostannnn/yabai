package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicReaderDisplayTitleTest {
    @Test
    fun `series and title are joined`() {
        val item = displayTitleItem(
            title = "Romance Dawn",
            comicMetadata = ComicMetadata(series = "One Piece"),
        )

        assertEquals("One Piece • Romance Dawn", comicReaderDisplayTitle(item))
    }

    @Test
    fun `missing series shows only the title`() {
        val item = displayTitleItem(
            title = "An Extraordinarily Long Comic Title",
            comicMetadata = ComicMetadata(series = null),
        )

        assertEquals("An Extraordinarily Long Comic Title", comicReaderDisplayTitle(item))
    }

    @Test
    fun `missing detail title shows only the series`() {
        val item = displayTitleItem(
            title = " ",
            comicMetadata = ComicMetadata(series = "One Piece"),
        )

        assertEquals("One Piece", comicReaderDisplayTitle(item))
    }

    @Test
    fun `series equal to title is shown once`() {
        val item = displayTitleItem(
            title = "One Piece",
            comicMetadata = ComicMetadata(series = "One Piece"),
        )

        assertEquals("One Piece", comicReaderDisplayTitle(item))
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
