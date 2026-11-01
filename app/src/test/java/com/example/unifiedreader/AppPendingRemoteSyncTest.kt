package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalMediaType
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPendingRemoteSyncTest {
    private val inLibrary = grimmoryBook(id = "grimmory:item:1", title = "Still here")

    @Test
    fun `uploads for a book gone from a loaded library leave the queue for the gone list`() {
        val summary = buildPendingRemoteSyncSummary(
            progressByItemId = mapOf(
                inLibrary.id to progress(inLibrary.id),
                GoneId to progress(GoneId),
            ),
            journalSnapshot = JournalSnapshot(
                sessions = listOf(session(id = "local:gone:1", itemId = GoneId), session(id = "local:gone:2", itemId = GoneId)),
                entries = listOf(entry(itemId = GoneId)),
            ),
            catalogItems = listOf(inLibrary),
            reachabilityBySource = emptyMap(),
            attemptsBySource = emptyMap(),
            isCatalogComplete = true,
        )

        assertEquals(1, summary.totalCount)
        assertEquals(listOf(inLibrary.id), summary.groups.single().items.map(AppPendingRemoteSyncItem::itemId))
        assertEquals(setOf(GoneId), summary.goneItemIds)
        assertEquals(
            listOf(RemoteSyncArtifact.PROGRESS, RemoteSyncArtifact.JOURNAL_ENTRY, RemoteSyncArtifact.READING_SESSION, RemoteSyncArtifact.READING_SESSION),
            summary.goneItems.map(AppPendingRemoteSyncItem::artifact).sorted(),
        )
        // Progress names no book: it takes the journal's title.
        assertTrue(summary.goneItems.all { item -> item.itemTitle == "Moths of Lisbon" && item.sourceType == SourceType.GRIMMORY })
    }

    @Test
    fun `nothing is gone while the library is still loading`() {
        val summary = buildPendingRemoteSyncSummary(
            progressByItemId = mapOf(GoneId to progress(GoneId)),
            journalSnapshot = JournalSnapshot(sessions = listOf(session(id = "local:gone:1", itemId = GoneId))),
            catalogItems = listOf(inLibrary),
            reachabilityBySource = emptyMap(),
            attemptsBySource = emptyMap(),
            isCatalogComplete = false,
        )

        assertTrue(summary.goneItems.isEmpty())
        // As before: the session waits under its journal title, the bookless progress stays out.
        assertEquals(listOf(RemoteSyncArtifact.READING_SESSION), summary.groups.single().items.map(AppPendingRemoteSyncItem::artifact))
    }

    @Test
    fun `a server the app holds no books from proves nothing gone`() {
        val summary = buildPendingRemoteSyncSummary(
            progressByItemId = mapOf(GoneId to progress(GoneId)),
            journalSnapshot = JournalSnapshot(sessions = listOf(session(id = "local:gone:1", itemId = GoneId))),
            catalogItems = listOf(inLibrary.copy(id = "kavita:item:9", sourceType = SourceType.KAVITA)),
            reachabilityBySource = emptyMap(),
            attemptsBySource = emptyMap(),
            isCatalogComplete = true,
        )

        assertTrue(summary.goneItems.isEmpty())
        assertEquals(1, summary.totalCount)
    }

    @Test
    fun `kept uploads are neither waiting nor gone`() {
        val summary = buildPendingRemoteSyncSummary(
            progressByItemId = mapOf(GoneId to progress(GoneId, syncState = SyncState.CLEAN)),
            journalSnapshot = JournalSnapshot(
                sessions = listOf(session(id = "local:gone:1", itemId = GoneId, syncState = SyncState.LOCAL_ONLY)),
                entries = listOf(entry(itemId = GoneId, syncState = SyncState.LOCAL_ONLY)),
            ),
            catalogItems = listOf(inLibrary),
            reachabilityBySource = emptyMap(),
            attemptsBySource = emptyMap(),
            isCatalogComplete = true,
        )

        assertTrue(summary.goneItems.isEmpty())
        assertEquals(0, summary.totalCount)
    }
}

private const val GoneId = "grimmory:item:2"

private fun grimmoryBook(id: String, title: String) = ReadableItem(
    id = id,
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    remoteId = id.substringAfterLast(':'),
    title = title,
    format = FormatType.EPUB,
)

private fun progress(itemId: String, syncState: SyncState = SyncState.DIRTY) = ReaderProgressSnapshot(
    itemId = itemId,
    locatorType = LocatorType.PERCENT,
    percent = 0.4,
    syncState = syncState,
    updatedAtEpochMillis = 1_000L,
)

private fun session(id: String, itemId: String, syncState: SyncState = SyncState.DIRTY) = JournalReadingSession(
    id = id,
    itemId = itemId,
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    itemTitle = "Moths of Lisbon",
    coverUrl = null,
    itemKind = ReadableItemKind.BOOK,
    format = FormatType.EPUB,
    mediaType = JournalMediaType.BOOK,
    startedAtEpochMillis = 100L,
    endedAtEpochMillis = 200L,
    durationMillis = 100L,
    fromPercent = null,
    toPercent = null,
    fromPage = null,
    toPage = null,
    pageCount = null,
    syncState = syncState,
)

private fun entry(itemId: String, syncState: SyncState = SyncState.DIRTY) = ItemJournalEntry(
    itemId = itemId,
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    itemTitle = "Moths of Lisbon",
    coverUrl = null,
    itemKind = ReadableItemKind.BOOK,
    format = FormatType.EPUB,
    rating = 4,
    reviewText = "",
    reviewedAtEpochMillis = null,
    updatedAtEpochMillis = 300L,
    syncState = syncState,
)
