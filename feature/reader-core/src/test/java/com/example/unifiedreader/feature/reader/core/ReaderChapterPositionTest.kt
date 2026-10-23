package com.example.unifiedreader.feature.reader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChapterPositionTest {
    @Test
    fun `chapter position round trips through the session preferences`() {
        val session = ReaderSessionSnapshot(
            itemId = "book",
            preferences = mapOf(readerSessionChapterPreference(ReaderChapterPosition(index = 3, count = 6))),
        )

        assertEquals(ReaderChapterPosition(index = 3, count = 6), session.chapterPosition)
    }

    @Test
    fun `an unknown or malformed chapter reads as none`() {
        assertNull(ReaderSessionSnapshot("book", preferences = mapOf(readerSessionChapterPreference(null))).chapterPosition)
        assertNull(ReaderSessionSnapshot("book").chapterPosition)
        assertNull(ReaderSessionSnapshot("book", preferences = mapOf(ReaderSessionChapterKey to "7/6")).chapterPosition)
        assertNull(ReaderSessionSnapshot("book", preferences = mapOf(ReaderSessionChapterKey to "two/six")).chapterPosition)
    }
}
