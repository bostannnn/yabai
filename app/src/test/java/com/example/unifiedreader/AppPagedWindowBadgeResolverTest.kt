package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.deriveKavitaSeriesGrouping
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPagedWindowBadgeResolverTest {
    private val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
    private val issueOne = issue("k-1", "akira", "Akira 1")
    private val issueTwo = issue("k-2", "akira", "Akira 2")
    private val standalone = book("book-1", "A Standalone Book")

    // The grid roots the paged window renders: the akira container + the standalone book root.
    private val grouping = deriveKavitaSeriesGrouping(listOf(issueOne, issueTwo, standalone))
    private val rootItems = grouping.rootItems
    private val containerId = rootItems.first(ReadableItem::isKavitaSeriesContainer).id

    @Test
    fun `resolves container badges from bounded progress, ratings and downloads`() = runBlocking {
        val sessionStore = FakeReaderSessionStore(
            initialProgress = mapOf(
                issueOne.id to finishedProgress(issueOne.id, updatedAt = 10L),
                issueTwo.id to finishedProgress(issueTwo.id, updatedAt = 20L),
            ),
        )
        val resolver = AppPagedWindowBadgeResolver(
            readableItemLookup = FakeMemberLookup(listOf(issueOne, issueTwo, standalone)),
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = listOf(
                    StoredIdsDownloadDataSource(
                        sourceType = SourceType.KAVITA,
                        storedItemIds = setOf(issueOne.id, issueTwo.id),
                    ),
                ),
            ),
            readerSessionStore = sessionStore,
        )

        val badges = resolver.resolve(
            source = SourceType.KAVITA,
            rootItems = rootItems,
            ratingByItemId = mapOf(issueOne.id to 8, issueTwo.id to 10),
            downloadingItemIds = emptySet(),
        )

        // Both issues finished -> the container shows finished progress and the average rating badge,
        // keyed to the grid-root id (proves the resolver's grouping matches the derived container id).
        assertTrue(badges.displayProgressByItemId[containerId]?.isFinished == true)
        assertEquals(20L, badges.displayProgressByItemId[containerId]?.lastReadAtEpochMillis)
        assertEquals("Avg 4.5", badges.displayRatingByItemId[containerId]?.label)

        // Both issues downloaded -> the container is locally available (checkmark) and deletable, and
        // has nothing left to download.
        assertTrue(containerId in badges.locallyAvailableItemIds)
        assertTrue(containerId in badges.downloadActionAvailability.deletableDownloadedItemIds)
        assertTrue(containerId !in badges.downloadActionAvailability.downloadableItemIds)

        // The unread standalone root passes through with no progress badge.
        assertNull(badges.displayProgressByItemId[standalone.id])
    }

    @Test
    fun `does not persist the window-scoped scan to the shared local-state cache`() = runBlocking {
        val cache = RecordingRemoteItemLocalStateCache()
        val resolver = AppPagedWindowBadgeResolver(
            readableItemLookup = FakeMemberLookup(listOf(issueOne, issueTwo, standalone)),
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = listOf(
                    StoredIdsDownloadDataSource(SourceType.KAVITA, setOf(issueOne.id)),
                ),
                remoteItemLocalStateCache = cache,
            ),
            readerSessionStore = FakeReaderSessionStore(),
        )

        resolver.resolve(
            source = SourceType.KAVITA,
            rootItems = rootItems,
            ratingByItemId = emptyMap(),
            downloadingItemIds = emptySet(),
        )

        // Storing a bounded subset would overwrite the library scope's full downloaded-set entry.
        assertEquals(0, cache.storeCalls)
    }

    @Test
    fun `empty window resolves to empty badges without touching sources`() = runBlocking {
        val resolver = AppPagedWindowBadgeResolver(
            readableItemLookup = FailingLookup,
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = emptyList(),
            ),
            readerSessionStore = FakeReaderSessionStore(),
        )

        val badges = resolver.resolve(
            source = SourceType.KAVITA,
            rootItems = emptyList(),
            ratingByItemId = emptyMap(),
            downloadingItemIds = emptySet(),
        )

        assertEquals(emptyMap<String, Any>(), badges.displayProgressByItemId)
        assertTrue(badges.locallyAvailableItemIds.isEmpty())
    }

    private fun finishedProgress(itemId: String, updatedAt: Long) = ReaderProgressSnapshot(
        itemId = itemId,
        locatorType = LocatorType.PAGE,
        page = 9,
        isFinished = true,
        updatedAtEpochMillis = updatedAt,
    )

    private fun issue(
        id: String,
        seriesKey: String,
        title: String,
    ): ReadableItem = ReadableItem(
        id = "kavita:item:$id",
        sourceType = SourceType.KAVITA,
        libraryId = libraryId,
        remoteId = id,
        remoteFileType = seriesKey,
        title = title,
        format = FormatType.CBZ,
        pageCount = 10,
        comicMetadata = ComicMetadata(
            series = seriesKey.replaceFirstChar(Char::titlecase),
            issueTitle = title,
        ),
    )

    // An EPUB with no comic metadata -> kind BOOK -> stays a standalone grid root (not grouped).
    private fun book(
        id: String,
        title: String,
    ): ReadableItem = ReadableItem(
        id = "kavita:item:$id",
        sourceType = SourceType.KAVITA,
        libraryId = libraryId,
        remoteId = id,
        title = title,
        format = FormatType.EPUB,
        pageCount = 100,
    )
}

private class FakeMemberLookup(
    private val members: List<ReadableItem>,
) : ReadableItemLookup {
    override suspend fun getItemById(itemId: String): ReadableItem? = members.firstOrNull { it.id == itemId }
    override suspend fun getItemsByIds(itemIds: List<String>): List<ReadableItem> =
        members.filter { it.id in itemIds }
    override suspend fun getSeriesMemberItems(item: ReadableItem): List<ReadableItem> = members
    override suspend fun getMemberItemsForRoots(source: SourceType, rootIds: List<String>): List<ReadableItem> = members
}

private object FailingLookup : ReadableItemLookup {
    override suspend fun getItemById(itemId: String): ReadableItem? = error("should not be called")
    override suspend fun getItemsByIds(itemIds: List<String>): List<ReadableItem> = error("should not be called")
    override suspend fun getSeriesMemberItems(item: ReadableItem): List<ReadableItem> = error("should not be called")
    override suspend fun getMemberItemsForRoots(source: SourceType, rootIds: List<String>): List<ReadableItem> =
        error("should not be called")
}

private class RecordingRemoteItemLocalStateCache : RemoteItemLocalStateCache {
    var storeCalls: Int = 0
        private set

    override fun load(items: List<ReadableItem>): RemoteItemLocalState? = null

    override fun store(items: List<ReadableItem>, state: RemoteItemLocalState) {
        storeCalls += 1
    }
}

private class StoredIdsDownloadDataSource(
    override val sourceType: SourceType,
    private val storedItemIds: Set<String>,
) : RemoteItemDownloadDataSource {
    override fun storedDocumentUri(item: ReadableItem): Uri? = if (item.id in storedItemIds) {
        Uri.parse("file:///downloads/${item.id}")
    } else {
        null
    }

    override suspend fun downloadToLocalStorage(item: ReadableItem): Uri? = null

    override suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String> =
        items.filter { it.id in storedItemIds }.mapTo(mutableSetOf(), ReadableItem::id)

    override suspend fun deleteStoredDocument(item: ReadableItem): Boolean = false
}
