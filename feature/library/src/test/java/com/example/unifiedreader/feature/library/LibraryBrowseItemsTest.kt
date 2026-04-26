package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBrowseItemsTest {
    @Test
    fun `series detail keeps canonical issue order regardless of saved library sort`() {
        val issue10 = seriesIssue(id = "10", title = "Saga 10", issueNumber = "10")
        val issue2 = seriesIssue(id = "2", title = "Saga 2", issueNumber = "2")
        val issue1 = seriesIssue(id = "1", title = "Saga 1", issueNumber = "1")

        val visibleItems = buildVisibleLibraryItems(
            sourceItems = listOf(issue10, issue2, issue1),
            filterState = com.example.unifiedreader.core.model.LibraryFilterState(),
            searchQuery = "",
            sortMode = LibrarySortMode.LAST_READ,
            lastReadAtEpochMillisByItemId = mapOf(
                issue10.id to 9_000L,
                issue2.id to 5_000L,
            ),
            forceSeriesIssueOrder = true,
        )

        assertEquals(listOf(issue1, issue2, issue10), visibleItems)
    }

    @Test
    fun `series detail falls back to natural title order when issue metadata is missing`() {
        val issue10 = seriesIssue(id = "10", title = "Batman 10")
        val issue2 = seriesIssue(id = "2", title = "Batman 2")
        val issue1 = seriesIssue(id = "1", title = "Batman 1")

        val visibleItems = buildVisibleLibraryItems(
            sourceItems = listOf(issue10, issue2, issue1),
            filterState = com.example.unifiedreader.core.model.LibraryFilterState(),
            searchQuery = "",
            sortMode = LibrarySortMode.TITLE,
            forceSeriesIssueOrder = true,
        )

        assertEquals(listOf(issue1, issue2, issue10), visibleItems)
    }

    @Test
    fun `non series browse still uses the selected library sort mode`() {
        val issue10 = seriesIssue(id = "10", title = "Saga 10", issueNumber = "10")
        val issue2 = seriesIssue(id = "2", title = "Saga 2", issueNumber = "2")
        val issue1 = seriesIssue(id = "1", title = "Saga 1", issueNumber = "1")

        val visibleItems = buildVisibleLibraryItems(
            sourceItems = listOf(issue10, issue2, issue1),
            filterState = com.example.unifiedreader.core.model.LibraryFilterState(),
            searchQuery = "",
            sortMode = LibrarySortMode.LAST_READ,
            lastReadAtEpochMillisByItemId = mapOf(
                issue10.id to 9_000L,
                issue2.id to 5_000L,
            ),
            forceSeriesIssueOrder = false,
        )

        assertEquals(listOf(issue10, issue2, issue1), visibleItems)
    }

    @Test
    fun `series search keeps relevance before canonical issue order`() {
        val weakEarlierIssue = seriesIssue(
            id = "2",
            title = "Saga 2",
            issueNumber = "2",
            tags = listOf("raw her"),
        )
        val strongLaterIssue = seriesIssue(
            id = "10",
            title = "Raw Hero 10",
            issueNumber = "10",
        )

        val visibleItems = buildVisibleLibraryItems(
            sourceItems = listOf(weakEarlierIssue, strongLaterIssue),
            filterState = com.example.unifiedreader.core.model.LibraryFilterState(),
            searchQuery = "raw her",
            sortMode = LibrarySortMode.TITLE,
            forceSeriesIssueOrder = true,
        )

        assertEquals(listOf(strongLaterIssue, weakEarlierIssue), visibleItems)
    }
}

private fun seriesIssue(
    id: String,
    title: String,
    issueNumber: String? = null,
    tags: List<String> = emptyList(),
): ReadableItem = ReadableItem(
    id = "kavita:item:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:1",
    remoteId = id,
    remoteFileType = "series-1",
    title = title,
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Saga",
        issueTitle = title,
        issueNumber = issueNumber,
        tags = tags,
    ),
)
