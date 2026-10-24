package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.ReaderChapterPosition
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.readerSessionChapterPreference
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
    }

    @Test
    fun `a finished book drops the chapter but keeps how many it has`() {
        val summary = summaries(isFinished = true).getValue(book.id)

        assertNull(summary.chapter)
        assertEquals(6, summary.chapterCount)
    }

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
                preferences = mapOf(readerSessionChapterPreference(ReaderChapterPosition(index = 3, count = 6))),
            ),
        ),
        localPageCountProvider = { 0 },
    )
}
