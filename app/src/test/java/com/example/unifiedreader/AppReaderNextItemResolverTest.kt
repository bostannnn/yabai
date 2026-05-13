package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.library.LibraryOrderedItemContinuationResolver
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppReaderNextItemResolverTest {
    @Test
    fun `next item resolution uses fresh session progress instead of stale ui summaries`() {
        val firstIssue = issue(id = "1", issueNumber = "1")
        val secondIssue = issue(id = "2", issueNumber = "2")
        val thirdIssue = issue(id = "3", issueNumber = "3")
        val sessionStore = FakeReaderSessionStore(
            initialProgress = mapOf(
                secondIssue.id to ReaderProgressSnapshot(
                    itemId = secondIssue.id,
                    locatorType = LocatorType.PAGE,
                    page = 27,
                    isFinished = true,
                    syncState = SyncState.CLEAN,
                    updatedAtEpochMillis = 1_000L,
                ),
            ),
        )
        val resolver = AppReaderNextItemResolver(
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = emptyList(),
            ),
            readerSessionStore = sessionStore,
            seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            orderedItemContinuationResolver = LibraryOrderedItemContinuationResolver(),
        )

        val nextItem = resolver.nextUnreadAfter(
            currentItem = firstIssue,
            items = listOf(firstIssue, secondIssue, thirdIssue),
        )

        assertEquals(thirdIssue, nextItem)
    }

    @Test
    fun `next item resolution follows active ordered item scope`() {
        val firstIssue = issue(id = "1", issueNumber = "1")
        val secondIssue = issue(id = "2", issueNumber = "2")
        val thirdIssue = issue(id = "3", issueNumber = "3")
        val resolver = AppReaderNextItemResolver(
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = emptyList(),
            ),
            readerSessionStore = FakeReaderSessionStore(),
            seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            orderedItemContinuationResolver = LibraryOrderedItemContinuationResolver(),
        )

        val nextItem = resolver.nextUnreadAfter(
            currentItem = firstIssue,
            items = listOf(firstIssue, secondIssue, thirdIssue),
            orderedItemIds = listOf(firstIssue.id, thirdIssue.id, secondIssue.id),
        )

        assertEquals(thirdIssue, nextItem)
    }

    @Test
    fun `next item resolution does not fall back outside active ordered item scope`() {
        val firstIssue = issue(id = "1", issueNumber = "1")
        val secondIssue = issue(id = "2", issueNumber = "2")
        val thirdIssue = issue(id = "3", issueNumber = "3")
        val resolver = AppReaderNextItemResolver(
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = emptyList(),
            ),
            readerSessionStore = FakeReaderSessionStore(),
            seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            orderedItemContinuationResolver = LibraryOrderedItemContinuationResolver(),
        )

        val nextItem = resolver.nextUnreadAfter(
            currentItem = secondIssue,
            items = listOf(firstIssue, secondIssue, thirdIssue),
            orderedItemIds = listOf(firstIssue.id, secondIssue.id),
        )

        assertNull(nextItem)
    }
}

private fun issue(
    id: String,
    issueNumber: String,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("library"),
    remoteId = id,
    remoteFileType = "series-7",
    title = "One Piece $issueNumber",
    format = FormatType.CBZ,
    pageCount = 28,
    comicMetadata = ComicMetadata(
        series = "One Piece",
        issueTitle = "Issue $issueNumber",
        issueNumber = issueNumber,
    ),
)
