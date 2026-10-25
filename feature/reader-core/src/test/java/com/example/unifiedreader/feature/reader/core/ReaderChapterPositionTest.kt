package com.example.unifiedreader.feature.reader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChapterPositionTest {
    @Test
    fun `chapter position round trips through the session preferences`() {
        val position = ReaderChapterPosition(
            index = 3,
            count = 4,
            title = "The Meridian Line",
            starts = listOf(0f, 0.1f, 0.24f, 0.6125f),
        )
        val session = ReaderSessionSnapshot(itemId = "book", preferences = readerSessionChapterPreferences(position))

        assertEquals(position, session.chapterPosition)
    }

    @Test
    fun `a position saved before titles and starts reads without them`() {
        val session = ReaderSessionSnapshot("book", preferences = mapOf(ReaderSessionChapterKey to "3/6"))

        assertEquals(ReaderChapterPosition(index = 3, count = 6), session.chapterPosition)
    }

    @Test
    fun `starts that don't match the chapters or don't rise are left out`() {
        fun startsOf(value: String) = ReaderSessionSnapshot(
            "book",
            preferences = mapOf(ReaderSessionChapterKey to "1/3", ReaderSessionChapterStartsKey to value),
        ).chapterPosition?.starts

        assertEquals(emptyList<Float>(), startsOf("0.0000,0.5000"))
        assertEquals(emptyList<Float>(), startsOf("0.0000,0.6000,0.5000"))
        assertEquals(listOf(0f, 0.2f, 0.7f), startsOf("0.0000,0.2000,0.7000"))
    }

    @Test
    fun `an unknown or malformed chapter reads as none`() {
        assertNull(ReaderSessionSnapshot("book", preferences = readerSessionChapterPreferences(null)).chapterPosition)
        assertNull(ReaderSessionSnapshot("book").chapterPosition)
        assertNull(ReaderSessionSnapshot("book", preferences = mapOf(ReaderSessionChapterKey to "7/6")).chapterPosition)
        assertNull(ReaderSessionSnapshot("book", preferences = mapOf(ReaderSessionChapterKey to "two/six")).chapterPosition)
    }
}
