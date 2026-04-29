package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingEventType
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryJournalSyncSupportTest {
    @Test
    fun `grimmory pull imports sessions without scanning per-book notes`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val otherItem = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "89", format = FormatType.EPUB)
        val repository = RecordingJournalRepository()
        val api = FakeGrimmoryApi(
            sessionsByBook = mapOf(
                "88" to listOf(
                    GrimmoryReadingSessionDto(
                        id = 10,
                        startTime = "2026-04-21T10:00:00Z",
                        endTime = "2026-04-21T10:30:00Z",
                        startProgress = 25f,
                        endProgress = 100f,
                    ),
                ),
            ),
            sessionFailuresByBook = mapOf("89" to IllegalStateException("session fetch failed")),
        )

        pullGrimmoryJournalCache(
            api = api,
            accessToken = "token",
            entries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    personalRating = 7,
                    dateFinished = "2026-04-21T10:30:00Z",
                    lastReadTime = "2026-04-21T10:30:00Z",
                ),
                GrimmoryBookCatalogEntry(
                    item = otherItem,
                    progress = null,
                    personalRating = 6,
                    lastReadTime = "2026-04-21T11:30:00Z",
                ),
            ),
            journalRepository = repository,
        )

        assertTrue(api.requestedNotesForBooks.isEmpty())
        assertEquals(listOf("88", "89"), api.requestedSessionsForBooks)
        assertEquals(listOf(7, 6), repository.replacedEntries.map { entry -> entry.rating })
        assertEquals(listOf("", ""), repository.replacedEntries.map { entry -> entry.reviewText })
        assertEquals(JournalReadingEventType.FINISHED, repository.replacedEvents.single().eventType)
        assertEquals(1.0, repository.replacedEvents.single().toPercent ?: -1.0, 0.0001)
        assertEquals(true, repository.replacedPreserveReadingEventsWhenIncomingIncomplete)
    }

    @Test
    fun `grimmory pull recovers reserved review note through notebook search`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository()
        val api = FakeGrimmoryApi(
            notebookEntries = listOf(
                GrimmoryNotebookEntryDto(
                    id = 12,
                    type = "NOTE",
                    bookId = 88,
                    text = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                    note = "Recovered review",
                    chapterTitle = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                    updatedAt = "2026-04-21T10:30:00",
                ),
                GrimmoryNotebookEntryDto(
                    id = 13,
                    type = "NOTE",
                    bookId = 88,
                    text = "ordinary note",
                    note = "Do not import",
                    chapterTitle = "Chapter 1",
                    updatedAt = "2026-04-22T10:30:00",
                ),
            ),
        )

        pullGrimmoryJournalCache(
            api = api,
            accessToken = "token",
            entries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    personalRating = 7,
                    lastReadTime = "2026-04-21T11:30:00Z",
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(listOf("NOTE"), api.requestedNotebookTypes)
        assertEquals(GRIMMORY_UNIFIED_READER_REVIEW_TITLE, api.requestedNotebookSearch)
        assertTrue(api.requestedNotesForBooks.isEmpty())
        assertEquals(listOf("88"), api.requestedSessionsForBooks)
        assertEquals(7, repository.replacedEntries.single().rating)
        assertEquals("Recovered review", repository.replacedEntries.single().reviewText)
        assertEquals("12", repository.replacedEntries.single().remoteReviewNoteId)
        assertEquals(false, repository.replacedPreserveReviewFieldsWhenIncomingBlank)
    }

    @Test
    fun `grimmory pull clears cached review when notebook lookup succeeds without reserved note`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository()

        pullGrimmoryJournalCache(
            api = FakeGrimmoryApi(
                notebookEntries = listOf(
                    GrimmoryNotebookEntryDto(
                        id = 13,
                        type = "NOTE",
                        bookId = 88,
                        text = "ordinary note",
                        note = "Do not import",
                        chapterTitle = "Chapter 1",
                        updatedAt = "2026-04-22T10:30:00",
                    ),
                ),
            ),
            accessToken = "token",
            entries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    personalRating = 7,
                    lastReadTime = "2026-04-21T11:30:00Z",
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(false, repository.replacedPreserveReviewFieldsWhenIncomingBlank)
        assertEquals("", repository.replacedEntries.single().reviewText)
        assertEquals(null, repository.replacedEntries.single().remoteReviewNoteId)
    }

    @Test
    fun `grimmory pull preserves cached review only when notebook lookup fails`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository()

        pullGrimmoryJournalCache(
            api = FakeGrimmoryApi(
                notebookFailure = IllegalStateException("notebook unavailable"),
            ),
            accessToken = "token",
            entries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    personalRating = 7,
                    lastReadTime = "2026-04-21T11:30:00Z",
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(true, repository.replacedPreserveReviewFieldsWhenIncomingBlank)
    }

    @Test
    fun `grimmory pull preserves cached events when sessions are unavailable`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val successfulItem = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "89", format = FormatType.EPUB)
        val repository = RecordingJournalRepository()
        val api = FakeGrimmoryApi(
            sessionFailuresByBook = mapOf("88" to IllegalStateException("sessions unavailable")),
        )

        pullGrimmoryJournalCache(
            api = api,
            accessToken = "token",
            entries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PERCENT,
                        percent = 1.0,
                        isFinished = true,
                        updatedAtEpochMillis = 1_777_400_000_000L,
                    ),
                    dateFinished = "2026-04-21T10:30:00Z",
                    lastReadTime = "2026-04-21T10:30:00Z",
                ),
                GrimmoryBookCatalogEntry(
                    item = successfulItem,
                    progress = ReadingProgress(
                        itemId = successfulItem.id,
                        locatorType = LocatorType.PERCENT,
                        percent = 1.0,
                        isFinished = true,
                        updatedAtEpochMillis = 1_777_400_000_000L,
                    ),
                    lastReadTime = "2026-04-21T11:30:00Z",
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(successfulItem.id, repository.replacedEvents.single().itemId)
        assertEquals(true, repository.replacedPreserveReadingEventsWhenIncomingIncomplete)
        assertEquals(listOf("88", "89"), api.requestedSessionsForBooks)
        assertEquals(setOf(successfulItem.id), repository.replacedReadingEventReplacementItemIds)
    }

    @Test
    fun `grimmory dirty push updates only the reserved review note`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(
                journalEntry(item = item, rating = 8, reviewText = "Updated review")
                    .copy(remoteReviewNoteId = "2"),
            ),
        )
        val api = FakeGrimmoryApi(
            notesByBook = mapOf(
                "88" to listOf(
                    GrimmoryBookNoteV2Dto(id = 1, selectedText = "ordinary note", noteContent = "Leave me"),
                ),
            ),
        )

        pushDirtyGrimmoryJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(item),
            journalRepository = repository,
        )

        assertEquals(8, api.updatedRating)
        assertEquals("2", api.updatedNoteId)
        assertEquals("Updated review", api.updatedNoteContent)
        assertTrue(api.requestedNotesForBooks.isEmpty())
        assertTrue(api.deletedNoteIds.isEmpty())
        assertEquals(SyncState.CLEAN, repository.markedSyncStates[item.id])
    }

    @Test
    fun `grimmory dirty push marks unresolved catalog entry failed`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = 8, reviewText = "Updated review")),
        )

        pushDirtyGrimmoryJournalEntries(
            api = FakeGrimmoryApi(),
            accessToken = "token",
            entries = emptyList(),
            journalRepository = repository,
        )

        assertEquals(SyncState.FAILED, repository.markedSyncStates[item.id])
    }

    @Test
    fun `grimmory dirty push creates app review note without scanning notes and caches id`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = 8, reviewText = "New review")),
        )
        val api = FakeGrimmoryApi(createdNoteId = 12)

        pushDirtyGrimmoryJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(item),
            journalRepository = repository,
        )

        assertTrue(api.requestedNotesForBooks.isEmpty())
        assertEquals("New review", api.createdNoteContent)
        assertEquals("12", repository.remoteReviewNoteIds[item.id])
        assertEquals(SyncState.CLEAN, repository.markedSyncStates[item.id])
    }

    @Test
    fun `grimmory dirty push updates existing reserved note before creating duplicate`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = 8, reviewText = "Retry review")),
        )
        val api = FakeGrimmoryApi(
            notebookEntries = listOf(
                GrimmoryNotebookEntryDto(
                    id = 22,
                    type = "NOTE",
                    bookId = 88,
                    text = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                    note = "Existing remote review",
                    chapterTitle = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                    updatedAt = "2026-04-21T10:30:00",
                ),
            ),
            createdNoteId = 12,
        )

        pushDirtyGrimmoryJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(item),
            journalRepository = repository,
        )

        assertEquals(GRIMMORY_UNIFIED_READER_REVIEW_TITLE, api.requestedNotebookSearch)
        assertEquals(null, api.createdNoteContent)
        assertEquals("22", api.updatedNoteId)
        assertEquals("Retry review", api.updatedNoteContent)
        assertEquals("22", repository.remoteReviewNoteIds[item.id])
        assertEquals(SyncState.CLEAN, repository.markedSyncStates[item.id])
    }

    @Test
    fun `grimmory dirty push fails created review note without id`() = runTest {
        val item = readableItem(sourceType = SourceType.GRIMMORY, remoteId = "88", format = FormatType.EPUB)
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = 8, reviewText = "New review")),
        )
        val api = FakeGrimmoryApi(createdNoteId = null)

        pushDirtyGrimmoryJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(item),
            journalRepository = repository,
        )

        assertEquals("New review", api.createdNoteContent)
        assertEquals(SyncState.FAILED, repository.markedSyncStates[item.id])
        assertTrue(item.id !in repository.remoteReviewNoteIds)
    }
}

private class RecordingJournalRepository(
    private val dirtyEntries: List<ItemJournalEntry> = emptyList(),
) : JournalRepository {
    private val snapshot = MutableStateFlow(JournalSnapshot())
    var replacedSourceType: SourceType? = null
    var replacedEvents: List<JournalReadingEvent> = emptyList()
    var replacedEntries: List<ItemJournalEntry> = emptyList()
    var replacedPreserveReviewFieldsWhenIncomingBlank: Boolean? = null
    var replacedPreserveReadingEventsWhenIncomingIncomplete: Boolean? = null
    var replacedReadingEventReplacementItemIds: Set<String>? = null
    val markedSyncStates = mutableMapOf<String, SyncState>()
    val remoteReviewNoteIds = mutableMapOf<String, String?>()

    override fun observeSnapshot(): Flow<JournalSnapshot> = snapshot

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(item: ReadableItem, rating: Int?, reviewText: String) = Unit

    override suspend fun saveJournalEntry(item: JournalItemRef, rating: Int?, reviewText: String) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit

    override suspend fun replaceCleanSourceJournalCache(
        sourceType: SourceType,
        events: List<JournalReadingEvent>,
        entries: List<ItemJournalEntry>,
        preserveReviewFieldsWhenIncomingBlank: Boolean,
        preserveRatingWhenIncomingBlank: Boolean,
        preserveReadingEventsWhenIncomingIncomplete: Boolean,
        readingEventReplacementItemIds: Set<String>?,
    ) {
        replacedSourceType = sourceType
        replacedEvents = events
        replacedEntries = entries
        replacedPreserveReviewFieldsWhenIncomingBlank = preserveReviewFieldsWhenIncomingBlank
        replacedPreserveReadingEventsWhenIncomingIncomplete = preserveReadingEventsWhenIncomingIncomplete
        replacedReadingEventReplacementItemIds = readingEventReplacementItemIds
    }

    override suspend fun getDirtyJournalEntriesForSource(sourceType: SourceType): List<ItemJournalEntry> =
        dirtyEntries.filter { entry -> entry.sourceType == sourceType }

    override suspend fun markJournalEntrySyncState(itemId: String, syncState: SyncState) {
        markedSyncStates[itemId] = syncState
    }

    override suspend fun updateJournalEntryRemoteReviewNoteId(itemId: String, remoteReviewNoteId: String?) {
        remoteReviewNoteIds[itemId] = remoteReviewNoteId
    }
}


private class FakeGrimmoryApi(
    private val notesByBook: Map<String, List<GrimmoryBookNoteV2Dto>> = emptyMap(),
    private val sessionsByBook: Map<String, List<GrimmoryReadingSessionDto>> = emptyMap(),
    private val sessionFailuresByBook: Map<String, Throwable> = emptyMap(),
    private val notebookEntries: List<GrimmoryNotebookEntryDto> = emptyList(),
    private val notebookFailure: Throwable? = null,
    private val createdNoteId: Long? = 2,
) : GrimmoryApi {
    var updatedRating: Int? = null
    var updatedNoteId: String? = null
    var updatedNoteContent: String? = null
    var createdNoteContent: String? = null
    val deletedNoteIds = mutableListOf<String>()
    val requestedNotesForBooks = mutableListOf<String>()
    val requestedSessionsForBooks = mutableListOf<String>()
    var requestedNotebookTypes: List<String> = emptyList()
    var requestedNotebookSearch: String? = null

    override suspend fun login(username: String, password: String): GrimmorySession = error("Unused")
    override suspend fun getLibraries(accessToken: String): List<Library> = emptyList()
    override suspend fun getBooks(accessToken: String, libraryId: String): List<ReadableItem> = emptyList()
    override fun bookContentUrl(bookId: String, bookType: String?): String = "content/$bookId"
    override fun bookCoverUrl(bookId: String): String = "cover/$bookId"
    override suspend fun updatePersonalRating(accessToken: String, bookId: String, rating: Int) =
        GrimmoryPersonalRatingResponseDto(bookId = bookId.toLong(), personalRating = rating).also {
            updatedRating = rating
        }
    override suspend fun resetPersonalRating(accessToken: String, bookId: String): GrimmoryPersonalRatingResponseDto? =
        GrimmoryPersonalRatingResponseDto(bookId = bookId.toLong(), personalRating = null)
    override suspend fun getReadingSessionsForBook(accessToken: String, bookId: String, pageSize: Int): List<GrimmoryReadingSessionDto> {
        requestedSessionsForBooks += bookId
        sessionFailuresByBook[bookId]?.let { error -> throw error }
        return sessionsByBook[bookId].orEmpty()
    }
    override suspend fun getBookNotesV2(accessToken: String, bookId: String): List<GrimmoryBookNoteV2Dto> =
        notesByBook[bookId].orEmpty().also {
            requestedNotesForBooks += bookId
        }
    override suspend fun getNotebookEntriesExport(
        accessToken: String,
        types: List<String>,
        search: String?,
        sort: String,
    ): List<GrimmoryNotebookEntryDto> {
        requestedNotebookTypes = types
        requestedNotebookSearch = search
        notebookFailure?.let { throw it }
        return notebookEntries
    }
    override suspend fun updateBookNoteV2(
        accessToken: String,
        noteId: String,
        noteContent: String,
        chapterTitle: String,
        color: String?,
    ): GrimmoryBookNoteV2Dto {
        updatedNoteId = noteId
        updatedNoteContent = noteContent
        return GrimmoryBookNoteV2Dto(id = noteId.toLong(), noteContent = noteContent, chapterTitle = chapterTitle)
    }
    override suspend fun createBookNoteV2(
        accessToken: String,
        bookId: String,
        cfi: String,
        noteContent: String,
        chapterTitle: String,
        selectedText: String?,
        color: String?,
    ): GrimmoryBookNoteV2Dto {
        createdNoteContent = noteContent
        return GrimmoryBookNoteV2Dto(id = createdNoteId, noteContent = noteContent, chapterTitle = chapterTitle)
    }
    override suspend fun deleteBookNoteV2(accessToken: String, noteId: String) {
        deletedNoteIds += noteId
    }
}


private fun readableItem(
    sourceType: SourceType,
    remoteId: String,
    format: FormatType = FormatType.CBZ,
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:$remoteId",
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:1",
    remoteId = remoteId,
    title = "Item $remoteId",
    format = format,
    kind = if (format == FormatType.CBZ || format == FormatType.CBR) ReadableItemKind.COMIC else ReadableItemKind.BOOK,
    pageCount = 20,
)

private fun journalEntry(
    item: ReadableItem,
    rating: Int?,
    reviewText: String,
): ItemJournalEntry = ItemJournalEntry(
    itemId = item.id,
    sourceType = item.sourceType,
    libraryId = item.libraryId,
    itemTitle = item.title,
    coverUrl = item.coverUrl,
    itemKind = item.kind,
    format = item.format,
    rating = rating,
    reviewText = reviewText,
    reviewedAtEpochMillis = 100L,
    updatedAtEpochMillis = 200L,
    syncState = SyncState.DIRTY,
)
