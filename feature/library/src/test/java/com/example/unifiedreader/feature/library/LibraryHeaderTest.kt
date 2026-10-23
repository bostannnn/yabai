package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryHeaderTest {
    @Test
    fun `source summary counts books, comics with series issues, and books in progress`() {
        val items = listOf(
            item("a", FormatType.EPUB),
            item("b", FormatType.PDF),
            item("c", FormatType.CBZ),
            item("series", FormatType.CBZ, issueCount = 3),
        )

        assertEquals("2 books · 4 comics · 1 in progress", librarySourceSummary(items, inProgressCount = 1))
    }

    @Test
    fun `source summary leaves out empty parts and says nothing for an empty source`() {
        assertEquals("1 book", librarySourceSummary(listOf(item("a", FormatType.EPUB)), inProgressCount = 0))
        assertNull(librarySourceSummary(emptyList(), inProgressCount = 0))
    }

    @Test
    fun `series summary counts issues read and in progress`() {
        val issues = listOf(item("1", FormatType.CBZ), item("2", FormatType.CBZ), item("3", FormatType.CBZ))
        val progress = mapOf(
            "1" to finished(),
            "2" to started(fraction = 0.4f, lastRead = 10),
        )

        assertEquals("3 issues · 1 read · 1 in progress", librarySeriesSummary(issues, progress))
    }

    @Test
    fun `next issue is the one being read, the most recent if several`() {
        val issues = listOf(item("1", FormatType.CBZ), item("2", FormatType.CBZ), item("3", FormatType.CBZ))
        val progress = mapOf(
            "1" to started(fraction = 0.2f, lastRead = 5),
            "3" to started(fraction = 0.5f, lastRead = 9),
        )

        assertEquals(LibrarySeriesNext(issues[2], inProgress = true), librarySeriesNextIssue(issues, progress))
    }

    @Test
    fun `next issue follows the last finished one`() {
        val issues = listOf(item("1", FormatType.CBZ), item("2", FormatType.CBZ), item("3", FormatType.CBZ))

        assertEquals(
            LibrarySeriesNext(issues[2], inProgress = false),
            librarySeriesNextIssue(issues, mapOf("1" to finished(), "2" to finished())),
        )
        assertEquals(LibrarySeriesNext(issues[0], inProgress = false), librarySeriesNextIssue(issues, emptyMap()))
        assertNull(librarySeriesNextIssue(issues, issues.associate { it.id to finished() }))
    }

    @Test
    fun `reading card position names the chapter, else the page, else the summary label`() {
        assertEquals(
            "Page 47 of 73",
            libraryReadingCardPositionLabel(ItemProgressSummary(label = "Resume", fraction = 0.64f, currentPage = 47, pageCount = 73)),
        )
        assertEquals("Resume at 40%", libraryReadingCardPositionLabel(ItemProgressSummary(label = "Resume at 40%", fraction = 0.4f)))
        assertEquals(
            "Chapter 3 of 6",
            libraryReadingCardPositionLabel(
                ItemProgressSummary(label = "Resume", fraction = 0.64f, currentPage = 47, pageCount = 73, chapter = 3, chapterCount = 6),
            ),
        )
    }

    private fun finished() = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true, lastReadAtEpochMillis = 1)

    private fun started(fraction: Float, lastRead: Long) =
        ItemProgressSummary(label = "Resume", fraction = fraction, lastReadAtEpochMillis = lastRead)

    private fun item(id: String, format: FormatType, issueCount: Int? = null) = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "library",
        title = "Item $id",
        format = format,
        issueCount = issueCount,
        comicMetadata = if (format == FormatType.CBZ) ComicMetadata(series = "Series", issueNumber = id) else null,
    )
}
