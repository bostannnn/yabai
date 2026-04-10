package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import com.example.unifiedreader.feature.downloads.RemoteDownloadWorkerRunner
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryItemActionsTest {
    @Test
    fun `download queue runner stops batch when queued work is cancelled`() = runTest {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val dataSource = BlockingRemoteDownloadDataSource(sourceType = SourceType.KAVITA)
        val store = TestDownloadQueueStore()
        val scheduler = ImmediateDownloadWorkScheduler(this)
        val runner = RemoteDownloadWorkerRunner(
            store = store,
            remoteDownloadDataSources = listOf(dataSource),
            completionSideEffects = emptyList(),
        )
        scheduler.runner = runner
        val controller = RemoteDownloadQueueController(
            store = store,
            scheduler = scheduler,
            remoteDownloadDataSources = listOf(dataSource),
        )
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val job = backgroundScope.launch {
            controller.enqueue(
                item = seriesItem,
                items = listOf(firstIssue, secondIssue),
            )
        }
        dataSource.awaitFirstDownloadStarted()

        controller.cancel(store.entries.single().id)
        job.join()

        assertEquals(listOf(firstIssue.id), dataSource.startedItemIds())
    }
}
