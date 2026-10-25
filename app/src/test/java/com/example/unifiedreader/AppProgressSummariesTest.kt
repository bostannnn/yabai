package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.ReaderChapterPosition
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.readerSessionChapterPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppProgressSummariesTest {
    private val book = ReadableItem(
        id = "local:book",
        sourceType = SourceType.LOCAL,
        libraryId = "local",
        title = "The Salt Orchard",
        format = FormatType.EPUB,
    )

    @Test
    fun `an EPUB summary carries the chapter its reader saved`() {
        val summary = summaries(isFinished = false).getValue(book.id)

        assertEquals(3, summary.chapter)
        assertEquals(6, summary.chapterCount)
        assertEquals("Low Tide", summary.chapterTitle)
        assertEquals(chapterStarts, summary.chapterStarts)
    }

    @Test
    fun `a finished book drops the chapter but keeps how many it has and where they start`() {
        val summary = summaries(isFinished = true).getValue(book.id)

        assertNull(summary.chapter)
        assertNull(summary.chapterTitle)
        assertEquals(6, summary.chapterCount)
        assertEquals(chapterStarts, summary.chapterStarts)
    }

    private val chapterStarts = listOf(0f, 0.1f, 0.24f, 0.38f, 0.61f, 0.8f)

    private fun summaries(isFinished: Boolean) = buildProgressSummaries(
        items = listOf(book),
        progressByItemId = mapOf(
            book.id to ReaderProgressSnapshot(
                itemId = book.id,
                locatorType = LocatorType.CFI,
                page = 47,
                percent = 0.64,
                isFinished = isFinished,
            ),
        ),
        sessionByItemId = mapOf(
            book.id to ReaderSessionSnapshot(
                itemId = book.id,
                pageCount = 73,
                preferences = readerSessionChapterPreferences(
                    ReaderChapterPosition(index = 3, count = 6, title = "Low Tide", starts = chapterStarts),
                ),
            ),
        ),
        localPageCountProvider = { 0 },
    )
}
