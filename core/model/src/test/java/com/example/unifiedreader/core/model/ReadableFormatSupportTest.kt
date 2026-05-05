package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableFormatSupportTest {
    @Test
    fun `readable format classification includes all active readers`() {
        assertTrue(FormatType.EPUB.isBookReaderFormat())
        assertTrue(FormatType.PDF.isBookReaderFormat())
        assertTrue(FormatType.CBZ.isComicReaderFormat())
        assertTrue(FormatType.CBR.isComicReaderFormat())
        assertEquals(setOf(FormatType.EPUB, FormatType.PDF, FormatType.CBZ, FormatType.CBR), AppReadableFormats)
    }

    @Test
    fun `readable format resolves from type extension or file name`() {
        assertEquals(FormatType.CBR, readableFormatFromTypeOrExtension(type = null, extension = "rar"))
        assertEquals(FormatType.CBZ, readableFormatFromTypeOrExtension(type = null, extension = null, fileName = "Issue.cbz"))
        assertEquals(FormatType.EPUB, readableFormatFromTypeOrExtension(type = "EPUB", extension = "pdf"))
        assertNull(readableFormatFromTypeOrExtension(type = "mobi", extension = null, fileName = "book.mobi"))
    }
}
