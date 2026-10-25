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
