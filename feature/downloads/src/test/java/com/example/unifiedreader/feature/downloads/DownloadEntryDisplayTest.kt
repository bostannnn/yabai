package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadEntryDisplayTest {
    @Test
    fun `completed batch prefers selected item over last current item`() {
        val series = readableItem(id = "series-1", title = "Series")
        val issueOne = readableItem(id = "issue-1", title = "Issue 1")
        val issueTwo = readableItem(id = "issue-2", title = "Issue 2")
        val entry = DownloadEntry(
            id = "entry-1",
            selectedItemId = series.id,
            selectedItemTitle = series.title,
            sourceType = SourceType.KAVITA,
            actionItems = listOf(issueOne.toQueuedDownloadItem(), issueTwo.toQueuedDownloadItem()),
            trackedItemIds = setOf(series.id, issueOne.id, issueTwo.id),
            completedItemIds = setOf(issueOne.id, issueTwo.id),
            status = DownloadEntryStatus.Completed,
            currentItemId = issueTwo.id,
            currentItemTitle = issueTwo.title,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
            completedAtEpochMillis = 3L,
        )
        val itemsById = listOf(series, issueOne, issueTwo).associateBy(ReadableItem::id)

        assertEquals(series, entry.displayItemForDownloads(itemsById::get))
    }

    @Test
    fun `running batch keeps showing current item`() {
        val series = readableItem(id = "series-1", title = "Series")
        val issueOne = readableItem(id = "issue-1", title = "Issue 1")
        val issueTwo = readableItem(id = "issue-2", title = "Issue 2")
        val entry = DownloadEntry(
            id = "entry-1",
            selectedItemId = series.id,
            selectedItemTitle = series.title,
            sourceType = SourceType.KAVITA,
            actionItems = listOf(issueOne.toQueuedDownloadItem(), issueTwo.toQueuedDownloadItem()),
            trackedItemIds = setOf(series.id, issueOne.id, issueTwo.id),
            completedItemIds = setOf(issueOne.id),
            status = DownloadEntryStatus.Running,
            currentItemId = issueTwo.id,
            currentItemTitle = issueTwo.title,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )
        val itemsById = listOf(series, issueOne, issueTwo).associateBy(ReadableItem::id)

        assertEquals(issueTwo, entry.displayItemForDownloads(itemsById::get))
    }
}

private fun readableItem(
    id: String,
    title: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "library",
    remoteId = id,
    title = title,
    format = FormatType.CBZ,
)

private fun ReadableItem.toQueuedDownloadItem(): QueuedDownloadItem = QueuedDownloadItem(
    id = id,
    sourceType = sourceType,
    libraryId = libraryId,
    remoteId = remoteId,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    formatName = format.name,
    pageCount = pageCount,
)
