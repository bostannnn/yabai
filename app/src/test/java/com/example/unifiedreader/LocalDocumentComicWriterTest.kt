package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDocumentComicWriterTest {
    @Test
    fun `comic info writer is parsed`() {
        val metadata = parseComicInfoMetadata(
            """
            <?xml version="1.0"?>
            <ComicInfo>
              <Series>Look Back</Series>
              <Writer>Tatsuki Fujimoto, Somebody Else</Writer>
            </ComicInfo>
            """.trimIndent().toByteArray(),
        )

        assertEquals("Tatsuki Fujimoto, Somebody Else", metadata?.writer)
    }

    @Test
    fun `comic archive writers become comic writers, not a book author`() {
        val item = recordToReadableItem(
            record(format = FormatType.CBZ, author = "Tatsuki Fujimoto, Somebody Else"),
        )

        assertEquals(listOf("Tatsuki Fujimoto", "Somebody Else"), item.comicMetadata?.writers)
        assertNull(item.bookMetadata)
    }

    @Test
    fun `epub author stays a book author`() {
        val item = recordToReadableItem(record(format = FormatType.EPUB, author = "Donna Tartt"))

        assertEquals("Donna Tartt", item.bookMetadata?.author)
        assertNull(item.comicMetadata)
    }

    @Test
    fun `comic without author or issue details has no comic metadata`() {
        val item = recordToReadableItem(record(format = FormatType.CBR, author = null))

        assertNull(item.comicMetadata)
        assertNull(item.bookMetadata)
    }

    private fun record(
        format: FormatType,
        author: String?,
    ): LocalDocumentRecord = LocalDocumentRecord(
        id = "comic-test",
        uri = "content://test/doc",
        displayName = "Look-Back.cbz",
        title = "Look-Back",
        format = format,
        pageCount = 2,
        pageEntryNames = listOf("001.jpg", "002.jpg"),
        author = author,
        addedAtEpochMillis = 1L,
    )
}
