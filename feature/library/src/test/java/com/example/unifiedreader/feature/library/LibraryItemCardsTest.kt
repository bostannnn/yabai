package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.core.model.buildStumpSeriesContainer
import com.example.unifiedreader.feature.library.menu.ReadStatusMenuActionState
import com.example.unifiedreader.feature.library.menu.buildReadingDirectionMenuState
import com.example.unifiedreader.feature.library.menu.buildLibraryItemMenuActions
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryItemCardsTest {
    @Test
    fun `remote cover prefetch includes every remote source`() {
        assertTrue(shouldPrefetchRemoteCover(kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")))
        assertTrue(shouldPrefetchRemoteCover(stumpIssue(id = "issue-1", title = "Akira 001", seriesKey = "series-7")))
        assertTrue(
            shouldPrefetchRemoteCover(
                ReadableItem(
                    id = "grimmory:item:book-1",
                    sourceType = SourceType.GRIMMORY,
                    libraryId = "grimmory:library:books",
                    remoteId = "book-1",
                    title = "Grimmory Book",
                    format = FormatType.EPUB,
                    coverUrl = "https://grimmory.example/api/v1/media/book/book-1/thumbnail",
                ),
            ),
        )
        assertFalse(
            shouldPrefetchRemoteCover(
                ReadableItem(
                    id = "local:item:book-1",
                    sourceType = SourceType.LOCAL,
                    libraryId = "local-imports",
                    title = "Local Book",
                    format = FormatType.EPUB,
                ),
            ),
        )
    }

    @Test
    fun `kavita series container exposes download action`() {
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(
                kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7"),
            ),
        )
        var selectedItem: ReadableItem? = null

        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem),
            allItems = listOf(kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")),
            downloadedItemIds = emptySet(),
            downloadingItemIds = emptySet(),
        )
        val downloadRequest = itemDownloadRequest(
            item = seriesItem,
            downloadableItemIds = actionAvailability.downloadableItemIds,
            onDownloadItem = { selectedItem = it },
        )

        assertNotNull(downloadRequest)
        downloadRequest?.invoke()
        assertEquals(seriesItem, selectedItem)
    }

    @Test
    fun `stump series container exposes delete downloaded action when stored`() {
        val seriesItem = buildStumpSeriesContainer(
            seriesKey = "stump:library:stump-comics::series-7",
            issues = listOf(
                stumpIssue(id = "issue-1", title = "Akira 001", seriesKey = "series-7"),
            ),
        )
        var selectedItem: ReadableItem? = null

        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem),
            allItems = listOf(stumpIssue(id = "issue-1", title = "Akira 001", seriesKey = "series-7")),
            downloadedItemIds = setOf("stump:item:issue-1"),
            downloadingItemIds = emptySet(),
        )
        val deleteRequest = itemDownloadedDeleteRequest(
            item = seriesItem,
            deletableDownloadedItemIds = actionAvailability.deletableDownloadedItemIds,
            onDeleteDownloadedItem = { selectedItem = it },
        )

        assertNotNull(deleteRequest)
        deleteRequest?.invoke()
        assertEquals(seriesItem, selectedItem)
    }

    @Test
    fun `partially downloaded series keeps both download and delete actions`() {
        val firstIssue = kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")
        val secondIssue = kavitaIssue(id = "chapter-2", title = "Akira 002", seriesKey = "series-7")
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem),
            allItems = listOf(firstIssue, secondIssue),
            downloadedItemIds = setOf(firstIssue.id),
            downloadingItemIds = emptySet(),
        )

        assertTrue(seriesItem.id in actionAvailability.downloadableItemIds)
        assertTrue(seriesItem.id in actionAvailability.deletableDownloadedItemIds)
    }

    @Test
    fun `series container download marks child issues as actively downloading`() {
        val firstIssue = kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")
        val secondIssue = kavitaIssue(id = "chapter-2", title = "Akira 002", seriesKey = "series-7")
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(firstIssue, secondIssue),
        )

        val actionAvailability = buildRemoteDownloadActionAvailability(
            sourceItems = listOf(seriesItem, firstIssue, secondIssue),
            allItems = listOf(firstIssue, secondIssue),
            downloadedItemIds = emptySet(),
            downloadingItemIds = setOf(seriesItem.id),
        )

        assertTrue(seriesItem.id in actionAvailability.activeDownloadingItemIds)
        assertTrue(firstIssue.id in actionAvailability.activeDownloadingItemIds)
        assertTrue(secondIssue.id in actionAvailability.activeDownloadingItemIds)
        assertTrue(firstIssue.id !in actionAvailability.downloadableItemIds)
        assertTrue(secondIssue.id !in actionAvailability.downloadableItemIds)
    }

    @Test
    fun `series menu keeps download action when reading direction uses icon toggle row`() {
        val menuActions = buildLibraryItemMenuActions(
            isDownloading = false,
            readStatusActionState = null,
            onDeleteRequested = null,
            onDownloadRequested = {},
            onDeleteDownloadedRequested = null,
        )

        assertEquals("Download file", menuActions[0].label)
    }

    @Test
    fun `series menu exposes reading direction toggle state separately`() {
        val menuState = buildReadingDirectionMenuState(
            seriesReadingDirection = ComicReadingDirection.RTL,
            onSeriesReadingDirectionSelected = {},
        )

        assertEquals(ComicReadingDirection.RTL, menuState?.selectedDirection)
    }

    @Test
    fun `reading direction menu state is absent when series direction is unavailable`() {
        assertNull(
            buildReadingDirectionMenuState(
                seriesReadingDirection = null,
                onSeriesReadingDirectionSelected = {},
            ),
        )
    }

    @Test
    fun `menu exposes mark as read for unread remote item`() {
        val menuActions = buildLibraryItemMenuActions(
            isDownloading = false,
            readStatusActionState = ReadStatusMenuActionState(
                isRead = false,
                onToggleRequested = {},
            ),
            onDeleteRequested = null,
            onDownloadRequested = null,
            onDeleteDownloadedRequested = null,
        )

        assertEquals("Mark as read", menuActions.single().label)
    }

    @Test
    fun `series containers do not expose read status toggle`() {
        val seriesItem = buildKavitaSeriesContainer(
            seriesKey = "kavita:library:k-comics::series-7",
            issues = listOf(kavitaIssue(id = "chapter-1", title = "Akira 001", seriesKey = "series-7")),
        )

        assertNull(
            itemReadStatusActionState(
                item = seriesItem,
                progress = null,
                onSetReadStatus = { _, _ -> },
            ),
        )
    }
}

private fun kavitaIssue(
    id: String,
    title: String,
    seriesKey: String,
): ReadableItem = ReadableItem(
    id = "kavita:item:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:k-comics",
    remoteId = id,
    remoteFileType = seriesKey,
    title = title,
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Akira",
        issueTitle = title,
    ),
)

private fun stumpIssue(
    id: String,
    title: String,
    seriesKey: String,
): ReadableItem = ReadableItem(
    id = "stump:item:$id",
    sourceType = SourceType.STUMP,
    libraryId = "stump:library:stump-comics",
    remoteId = id,
    remoteFileType = seriesKey,
    title = title,
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Akira",
        issueTitle = title,
    ),
)
