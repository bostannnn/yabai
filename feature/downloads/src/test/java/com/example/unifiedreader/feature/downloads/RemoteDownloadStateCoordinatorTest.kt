package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.core.model.remoteLibraryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteDownloadStateCoordinatorTest {
    private val coordinator = RemoteDownloadStateCoordinator()

    @Test
    fun `start download tracks selected item`() {
        val item = sampleRemoteItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )

        val start = coordinator.startDownload(
            currentState = RemoteDownloadState(),
            item = item,
            items = listOf(item),
        )

        assertEquals(setOf(item.id), start?.trackedItemIds)
        assertEquals(RemoteDownloadState(downloadingItemIds = setOf(item.id)), start?.state)
        assertEquals("Downloading Remote Book.", start?.libraryMessage)
    }

    @Test
    fun `start series download tracks root and child items`() {
        val firstIssue = sampleRemoteItem(
            id = "chapter-1",
            title = "Issue 1",
            remoteFileType = "series-7",
        )
        val secondIssue = sampleRemoteItem(
            id = "chapter-2",
            title = "Issue 2",
            remoteFileType = "series-7",
        )
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = SourceType.KAVITA.remoteLibraryId("k-comics") + "::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val start = coordinator.startDownload(
            currentState = RemoteDownloadState(),
            item = seriesItem,
            items = listOf(firstIssue, secondIssue),
        )

        assertEquals(
            setOf(seriesItem.id, firstIssue.id, secondIssue.id),
            start?.trackedItemIds,
        )
    }

    @Test
    fun `start download returns null when any tracked item is already downloading`() {
        val firstIssue = sampleRemoteItem(
            id = "chapter-1",
            title = "Issue 1",
            remoteFileType = "series-7",
        )
        val secondIssue = sampleRemoteItem(
            id = "chapter-2",
            title = "Issue 2",
            remoteFileType = "series-7",
        )
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = SourceType.KAVITA.remoteLibraryId("k-comics") + "::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val start = coordinator.startDownload(
            currentState = RemoteDownloadState(downloadingItemIds = setOf(firstIssue.id)),
            item = seriesItem,
            items = listOf(firstIssue, secondIssue),
        )

        assertNull(start)
    }

    @Test
    fun `finish download clears tracked ids`() {
        val state = coordinator.finishDownload(
            currentState = RemoteDownloadState(
                downloadingItemIds = setOf("chapter-1", "chapter-2", "other"),
            ),
            trackedItemIds = setOf("chapter-1", "chapter-2"),
        )

        assertEquals(RemoteDownloadState(downloadingItemIds = setOf("other")), state)
    }
}

private fun sampleRemoteItem(
    id: String,
    title: String,
    format: FormatType = FormatType.CBZ,
    remoteFileType: String? = null,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
    remoteId = id,
    title = title,
    format = format,
    remoteFileType = remoteFileType,
    comicMetadata = ComicMetadata(series = "Akira", issueTitle = title),
)
