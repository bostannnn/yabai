package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsButtonStateTest {
    @Test
    fun `nothing queued shows no ring`() {
        assertEquals(DownloadsButtonState(), DownloadEntriesSnapshot(emptyList()).buttonState)
    }

    @Test
    fun `a failure with nothing running shows the dot`() {
        val snapshot = DownloadEntriesSnapshot(listOf(entry("a", DownloadEntryStatus.Failed, files = 1)))

        assertEquals(DownloadsButtonState(hasFailed = true), snapshot.buttonState)
    }

    @Test
    fun `the first file of a run downloads without a known fraction`() {
        val snapshot = DownloadEntriesSnapshot(listOf(entry("a", DownloadEntryStatus.Running, files = 1)))

        assertEquals(DownloadsButtonState(activeCount = 1, progress = null), snapshot.buttonState)
    }

    @Test
    fun `entries finished during the run keep counting toward it`() {
        val snapshot = DownloadEntriesSnapshot(
            listOf(
                entry("old", DownloadEntryStatus.Completed, files = 4, done = 4, createdAt = 1, completedAt = 5),
                entry("a", DownloadEntryStatus.Completed, files = 1, done = 1, createdAt = 10, completedAt = 20),
                entry("b", DownloadEntryStatus.Running, files = 2, done = 1, createdAt = 11),
                entry("c", DownloadEntryStatus.Queued, files = 1, createdAt = 12),
            ),
        )

        // The run started with "a" at 10; "old" finished before it. 2 of 4 files are done.
        assertEquals(DownloadsButtonState(activeCount = 2, progress = 0.5f), snapshot.buttonState)
    }

    @Test
    fun `a queue waiting for its server keeps its fraction`() {
        val snapshot = DownloadEntriesSnapshot(
            listOf(entry("a", DownloadEntryStatus.PausedConnectivity, files = 1, createdAt = 10)),
        )

        assertEquals(DownloadsButtonState(activeCount = 1, progress = 0f), snapshot.buttonState)
    }

    private fun entry(
        id: String,
        status: DownloadEntryStatus,
        files: Int,
        done: Int = 0,
        createdAt: Long = 1L,
        completedAt: Long? = null,
    ): DownloadEntry {
        val items = (1..files).map { index -> queuedItem("$id-$index") }
        return DownloadEntry(
            id = id,
            selectedItemId = id,
            selectedItemTitle = id,
            sourceType = SourceType.KAVITA,
            actionItems = items,
            trackedItemIds = items.mapTo(mutableSetOf(), QueuedDownloadItem::id),
            completedItemIds = items.take(done).mapTo(mutableSetOf(), QueuedDownloadItem::id),
            status = status,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = completedAt ?: createdAt,
            completedAtEpochMillis = completedAt,
        )
    }

    private fun queuedItem(id: String) = QueuedDownloadItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "library",
        remoteId = id,
        remoteFileId = null,
        remoteFileType = null,
        title = id,
        formatName = "CBZ",
        pageCount = null,
    )
}
