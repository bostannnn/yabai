package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalMediaType
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepUploadsOnDeviceUseCaseTest {
    @Test
    fun `keeping a gone book's uploads stops them and keeps everything`() = runTest {
        val store = InMemoryProgressStore(
            progress(GoneId, SyncState.FAILED),
            progress(OtherId, SyncState.DIRTY),
        )
        val journal = SyncStateJournal(
            entries = listOf(entry(GoneId), entry(OtherId)),
            sessions = listOf(session("local:gone:1", GoneId), session("local:other:1", OtherId)),
        )

        KeepUploadsOnDeviceUseCase(progressSyncStore = store, journalRepository = journal)(setOf(GoneId))

        assertEquals(SyncState.CLEAN, store.currentProgressByItemId().getValue(GoneId).syncState)
        assertEquals(0.4, store.currentProgressByItemId().getValue(GoneId).percent)
        assertEquals(SyncState.DIRTY, store.currentProgressByItemId().getValue(OtherId).syncState)
        assertEquals(mapOf(GoneId to SyncState.LOCAL_ONLY), journal.entryStates)
        assertEquals(mapOf("local:gone:1" to SyncState.LOCAL_ONLY), journal.sessionStates)
    }
}

private const val GoneId = "grimmory:item:2"
private const val OtherId = "grimmory:item:3"

private fun progress(itemId: String, syncState: SyncState) = ProgressSyncSnapshot(
    itemId = itemId,
    locatorType = LocatorType.PERCENT,
    percent = 0.4,
    syncState = syncState,
    updatedAtEpochMillis = 1_000L,
)

private fun entry(itemId: String) = ItemJournalEntry(
    itemId = itemId,
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    itemTitle = itemId,
    coverUrl = null,
    itemKind = ReadableItemKind.BOOK,
    format = FormatType.EPUB,
    rating = 4,
    reviewText = "",
    reviewedAtEpochMillis = null,
    updatedAtEpochMillis = 300L,
    syncState = SyncState.DIRTY,
)

private fun session(id: String, itemId: String) = JournalReadingSession(
    id = id,
    itemId = itemId,
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    itemTitle = itemId,
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
    syncState = SyncState.DIRTY,
)

/** The journal's dirty rows, recording the states the use case writes. */
private class SyncStateJournal(
    private val entries: List<ItemJournalEntry>,
    private val sessions: List<JournalReadingSession>,
) : JournalRepository {
    val entryStates = mutableMapOf<String, SyncState>()
    val sessionStates = mutableMapOf<String, SyncState>()

    override fun observeSnapshot(): Flow<JournalSnapshot> = flowOf(JournalSnapshot())

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(item: ReadableItem, rating: Int?, reviewText: String) = Unit

    override suspend fun saveJournalEntry(item: JournalItemRef, rating: Int?, reviewText: String) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit

    override suspend fun getDirtyJournalEntriesForSource(sourceType: SourceType): List<ItemJournalEntry> =
        entries.filter { entry -> entry.sourceType == sourceType }

    override suspend fun getDirtyReadingSessionsForSource(sourceType: SourceType): List<JournalReadingSession> =
        sessions.filter { session -> session.sourceType == sourceType }

    override suspend fun markJournalEntrySyncState(itemId: String, syncState: SyncState) {
        entryStates[itemId] = syncState
    }

    override suspend fun markReadingSessionSyncState(sessionId: String, syncState: SyncState) {
        sessionStates[sessionId] = syncState
    }
}

private class InMemoryProgressStore(vararg initial: ProgressSyncSnapshot) : ProgressSyncStore {
    private val progressState = MutableStateFlow(initial.associateBy(ProgressSyncSnapshot::itemId))
    override val isHydratedFlow = MutableStateFlow(true).asStateFlow()
    override val progressByItemIdFlow = progressState.asStateFlow()

    override fun currentProgressByItemId(): Map<String, ProgressSyncSnapshot> = progressState.value

    override fun getProgress(item: ReadableItem): ProgressSyncSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ProgressSyncLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) = Unit

    override suspend fun replaceProgressIfCurrent(
        expected: ProgressSyncSnapshot,
        replacement: ProgressSyncSnapshot?,
    ): Boolean {
        if (progressState.value[expected.itemId] != expected) return false
        progressState.value = if (replacement == null) {
            progressState.value - expected.itemId
        } else {
            progressState.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) = clearProgress(itemId)
}
