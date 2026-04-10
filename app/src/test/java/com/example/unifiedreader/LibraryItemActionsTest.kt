package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryItemActionsTest {
    @Test
    fun `download use case stops batch when coroutine is cancelled`() = runTest {
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
        val useCase = DownloadRemoteItemUseCase(
            remoteDownloadDataSources = listOf(dataSource),
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = listOf(dataSource),
            ),
            sourceProgressSyncs = emptyList(),
        )
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val job = backgroundScope.launch {
            useCase(
                item = seriesItem,
                items = listOf(firstIssue, secondIssue),
            )
        }
        dataSource.awaitFirstDownloadStarted()

        job.cancel()
        dataSource.cancelFirstDownload()
        job.join()

        assertEquals(listOf(firstIssue.id), dataSource.startedItemIds())
    }
}
