package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTileProgressTest {

    @Test
    fun `a book opened on its first screen is just started, with no bar`() {
        val progress = ItemProgressSummary(label = "Resume from saved location", fraction = 0f, currentPage = 1, pageCount = 73)

        assertTrue(progress.isJustStarted)
        assertEquals("Just started", progress.tileLabel)
        assertNull(progress.tileBarFraction)
    }

    @Test
    fun `under one percent is just started`() {
        val progress = ItemProgressSummary(label = "Resume at 1%", fraction = 0.006f)

        assertEquals("Just started", progress.tileLabel)
        assertNull(progress.tileBarFraction)
    }

    @Test
    fun `a comic on its first page is just started although the page counts as read`() {
        val progress = ItemProgressSummary(label = "Resume on page 1 of 8", fraction = 0.125f, currentPage = 1, pageCount = 8)

        assertTrue(progress.isJustStarted)
        assertEquals("Just started", progress.tileLabel)
        assertNull(progress.tileBarFraction)
    }

    @Test
    fun `from the second page a tile says the rounded percentage, without the page`() {
        val progress = ItemProgressSummary(label = "Resume on page 2 of 8", fraction = 0.25f, currentPage = 2, pageCount = 8)

        assertFalse(progress.isJustStarted)
        assertEquals("25%", progress.tileLabel)
        assertEquals(0.25f, progress.tileBarFraction)
        assertEquals("96%", ItemProgressSummary(label = "", fraction = 0.96f, currentPage = 71, pageCount = 73).tileLabel)
    }

    @Test
    fun `a finished book says nothing on its tile`() {
        val finished = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true)
        val lastPage = ItemProgressSummary(label = "Resume on page 1 of 1", fraction = 1f, currentPage = 1, pageCount = 1)

        assertNull(finished.tileLabel)
        assertNull(finished.tileBarFraction)
        assertFalse(finished.isJustStarted)
        assertNull(lastPage.tileLabel)
        assertFalse(lastPage.isJustStarted)
    }

    @Test
    fun `a series counts only issues read into as in progress`() {
        val issues = (1..3).map { number -> issue(number) }
        val progress = mapOf(
            issues[0].id to ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true),
            issues[1].id to ItemProgressSummary(label = "", fraction = 0.5f, currentPage = 4, pageCount = 8),
            issues[2].id to ItemProgressSummary(label = "", fraction = 0.125f, currentPage = 1, pageCount = 8),
        )

        assertEquals("3 issues · 1 read · 1 in progress", librarySeriesSummary(issues, progress))
    }
}

private fun issue(number: Int): ReadableItem = ReadableItem(
    id = "local:issue-$number",
    sourceType = SourceType.LOCAL,
    libraryId = "local:library",
    title = "Star Courier $number",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(series = "Star Courier", issueNumber = number.toString()),
)
