package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
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
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaJournalSyncSupportTest {
    @Test
    fun `kavita pull maps user scoped reviews and per-library reading history into clean journal cache`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val repository = RecordingJournalRepository()
        val api = FakeKavitaApi(
            currentUserId = 1,
            reviews = listOf(
                KavitaUserReviewDto(
                    id = 9,
                    body = "Strong chapter",
                    chapterId = 42,
                    rating = 3.5f,
                    createdUtc = "2026-04-20T12:00:00Z",
                ),
            ),
            historyByLibrary = mapOf(
                4 to listOf(
                    KavitaReadingHistoryItemDto(
                        sessionId = 123,
                        startTimeUtc = "2026-04-21T10:00:00Z",
                        endTimeUtc = "2026-04-21T10:30:00Z",
                        chapters = listOf(
                            KavitaReadingHistoryChapterItemDto(
                                chapterId = 42,
                                startTimeUtc = "2026-04-21T10:00:00Z",
                                endTimeUtc = "2026-04-21T10:30:00Z",
                                startPage = 1,
                                endPage = 10,
                                totalPages = 20,
                                completed = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        pullKavitaJournalCache(
            api = api,
            accessToken = "token",
            libraries = listOf(
                Library(
                    id = "kavita:library:4",
                    sourceType = SourceType.KAVITA,
                    title = "Library",
                    remoteId = "4",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            entries = listOf(KavitaChapterCatalogEntry(item = item, progress = null)),
            journalRepository = repository,
        )

        assertEquals(SourceType.KAVITA, repository.replacedSourceType)
        assertEquals(listOf(4), api.requestedHistoryLibraries)
        assertEquals(1, api.requestedReviewUserId)
        assertEquals(7, repository.replacedEntries.single().rating)
        assertEquals("Strong chapter", repository.replacedEntries.single().reviewText)
        assertEquals("9", repository.replacedEntries.single().remoteEntryId)
        assertEquals(SyncState.CLEAN, repository.replacedEntries.single().syncState)
        assertEquals(JournalReadingEventType.FINISHED, repository.replacedEvents.single().eventType)
        assertEquals(0.5, repository.replacedEvents.single().toPercent ?: -1.0, 0.0001)
        assertEquals("123", repository.replacedEvents.single().remoteEventId)
    }

    @Test
    fun `kavita dirty push maps internal ten step rating to half star rating`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = 7, reviewText = "Keep it")),
        )
        val api = FakeKavitaApi(chapterInfo = KavitaChapterInfoDto(seriesId = 77))

        pushDirtyKavitaJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(item),
            journalRepository = repository,
        )

        assertEquals(77, api.updatedRatingSeriesId)
        assertEquals(42, api.updatedRatingChapterId)
        assertEquals(3.5f, api.updatedRating ?: -1f, 0.0001f)
        assertEquals("Keep it", api.updatedReviewBody)
        assertEquals(SyncState.CLEAN, repository.markedSyncStates[item.id])
    }

    @Test
    fun `kavita pull falls back to finished progress when history is unavailable`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val repository = RecordingJournalRepository()
        val api = FakeKavitaApi(currentUserId = 1)

        pullKavitaJournalCache(
            api = api,
            accessToken = "token",
            libraries = listOf(
                Library(
                    id = "kavita:library:4",
                    sourceType = SourceType.KAVITA,
                    title = "Library",
                    remoteId = "4",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            entries = listOf(
                KavitaChapterCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PAGE,
                        page = 20,
                        percent = 1.0,
                        isFinished = true,
                        updatedAtEpochMillis = 1_777_400_000_000L,
                    ),
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(JournalReadingEventType.FINISHED, repository.replacedEvents.single().eventType)
        assertEquals(item.id, repository.replacedEvents.single().itemId)
        assertEquals(1.0, repository.replacedEvents.single().toPercent ?: -1.0, 0.0001)
    }

    @Test
    fun `kavita pull preserves cached reviews when review lookup fails`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val repository = RecordingJournalRepository()

        pullKavitaJournalCache(
            api = FakeKavitaApi(
                currentUserId = 1,
                reviewFailure = IllegalStateException("reviews unavailable"),
            ),
            accessToken = "token",
            libraries = emptyList(),
            entries = listOf(KavitaChapterCatalogEntry(item = item, progress = null)),
            journalRepository = repository,
        )

        assertEquals(true, repository.replacedPreserveReviewFieldsWhenIncomingBlank)
    }

    @Test
    fun `kavita pull preserves cached events when any reading history lookup fails`() = runTest {
        val failedItem = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
            .copy(libraryId = "kavita:library:4")
        val successfulItem = readableItem(sourceType = SourceType.KAVITA, remoteId = "43")
            .copy(libraryId = "kavita:library:5")
        val repository = RecordingJournalRepository()

        pullKavitaJournalCache(
            api = FakeKavitaApi(
                currentUserId = 1,
                historyFailuresByLibrary = mapOf(4 to IllegalStateException("history unavailable")),
            ),
            accessToken = "token",
            libraries = listOf(
                Library(
                    id = "kavita:library:4",
                    sourceType = SourceType.KAVITA,
                    title = "Library",
                    remoteId = "4",
                    contentType = LibraryContentType.COMICS,
                ),
                Library(
                    id = "kavita:library:5",
                    sourceType = SourceType.KAVITA,
                    title = "Library 2",
                    remoteId = "5",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            entries = listOf(
                KavitaChapterCatalogEntry(item = failedItem, progress = null),
                KavitaChapterCatalogEntry(
                    item = successfulItem,
                    progress = ReadingProgress(
                        itemId = successfulItem.id,
                        locatorType = LocatorType.PAGE,
                        page = 20,
                        percent = 1.0,
                        isFinished = true,
                        updatedAtEpochMillis = 1_777_400_000_000L,
                    ),
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(true, repository.replacedPreserveReadingEventsWhenIncomingIncomplete)
        assertEquals(setOf(successfulItem.id), repository.replacedReadingEventReplacementItemIds)
        assertEquals(successfulItem.id, repository.replacedEvents.single().itemId)
    }

    @Test
    fun `kavita pull does not insert progress fallback events when history lookup fails`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
            .copy(libraryId = "kavita:library:4")
        val repository = RecordingJournalRepository()

        pullKavitaJournalCache(
            api = FakeKavitaApi(
                currentUserId = 1,
                historyFailuresByLibrary = mapOf(4 to IllegalStateException("history unavailable")),
            ),
            accessToken = "token",
            libraries = listOf(
                Library(
                    id = "kavita:library:4",
                    sourceType = SourceType.KAVITA,
                    title = "Library",
                    remoteId = "4",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            entries = listOf(
                KavitaChapterCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PAGE,
                        page = 20,
                        percent = 1.0,
                        isFinished = true,
                        updatedAtEpochMillis = 1_777_400_000_000L,
                    ),
                ),
            ),
            journalRepository = repository,
        )

        assertEquals(true, repository.replacedPreserveReadingEventsWhenIncomingIncomplete)
        assertEquals(emptySet<String>(), repository.replacedReadingEventReplacementItemIds)
        assertTrue(repository.replacedEvents.isEmpty())
    }

    @Test
    fun `kavita dirty push clears remote rating when local rating is null`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = null, reviewText = "")),
        )
        val api = FakeKavitaApi(chapterInfo = KavitaChapterInfoDto(seriesId = 77))

        pushDirtyKavitaJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(item),
            journalRepository = repository,
        )

        assertEquals(0f, api.updatedRating ?: -1f, 0.0001f)
        assertEquals(42, api.deletedReviewChapterId)
        assertEquals(SyncState.CLEAN, repository.markedSyncStates[item.id])
    }

    @Test
    fun `kavita dirty push marks unresolved catalog entry failed`() = runTest {
        val item = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(journalEntry(item = item, rating = 7, reviewText = "Keep it")),
        )

        pushDirtyKavitaJournalEntries(
            api = FakeKavitaApi(),
            accessToken = "token",
            entries = emptyList(),
            journalRepository = repository,
        )

        assertEquals(SyncState.FAILED, repository.markedSyncStates[item.id])
    }

    @Test
    fun `kavita dirty push marks chapter info failures failed and continues`() = runTest {
        val failingItem = readableItem(sourceType = SourceType.KAVITA, remoteId = "42")
        val successfulItem = readableItem(sourceType = SourceType.KAVITA, remoteId = "43")
        val repository = RecordingJournalRepository(
            dirtyEntries = listOf(
                journalEntry(item = failingItem, rating = 7, reviewText = "Will fail"),
                journalEntry(item = successfulItem, rating = 8, reviewText = "Will push"),
            ),
        )
        val api = FakeKavitaApi(
            chapterInfo = KavitaChapterInfoDto(seriesId = 77),
            chapterInfoFailures = mapOf("42" to IllegalStateException("chapter lookup failed")),
        )

        pushDirtyKavitaJournalEntries(
            api = api,
            accessToken = "token",
            entries = listOf(failingItem, successfulItem),
            journalRepository = repository,
        )

        assertEquals(SyncState.FAILED, repository.markedSyncStates[failingItem.id])
        assertEquals(SyncState.CLEAN, repository.markedSyncStates[successfulItem.id])
        assertEquals(43, api.updatedRatingChapterId)
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


private class FakeKavitaApi(
    private val currentUserId: Int? = null,
    private val reviews: List<KavitaUserReviewDto> = emptyList(),
    private val reviewFailure: Throwable? = null,
    private val historyByLibrary: Map<Int, List<KavitaReadingHistoryItemDto>> = emptyMap(),
    private val historyFailuresByLibrary: Map<Int, Throwable> = emptyMap(),
    private val chapterInfo: KavitaChapterInfoDto = KavitaChapterInfoDto(),
    private val chapterInfoFailures: Map<String, Throwable> = emptyMap(),
) : KavitaApi {
    var requestedReviewUserId: Int? = null
    val requestedHistoryLibraries = mutableListOf<Int>()
    var updatedRatingSeriesId: Int? = null
    var updatedRatingChapterId: Int? = null
    var updatedRating: Float? = null
    var updatedReviewBody: String? = null
    var deletedReviewChapterId: Int? = null

    override suspend fun login(username: String, password: String): KavitaSession = error("Unused")
    override suspend fun refreshSession(refreshToken: String, accessToken: String?, apiKey: String?): KavitaSession = error("Unused")
    override suspend fun getLibraries(accessToken: String): List<Library> = emptyList()
    override suspend fun getCurrentUserId(accessToken: String): Int? = currentUserId
    override suspend fun getSeries(accessToken: String, apiKey: String?): List<KavitaSeriesEntry> = emptyList()
    override suspend fun getSeriesDetail(accessToken: String, series: KavitaSeriesEntry): List<KavitaChapterCatalogEntry> = emptyList()
    override suspend fun getProgress(accessToken: String, chapterId: String): KavitaProgressDto = KavitaProgressDto()
    override suspend fun getChapterInfo(accessToken: String, chapterId: String, includeDimensions: Boolean): KavitaChapterInfoDto {
        chapterInfoFailures[chapterId]?.let { error -> throw error }
        return chapterInfo
    }
    override suspend fun updateProgress(accessToken: String, progress: KavitaProgressUpdate) = Unit
    override suspend fun updateChapterRating(accessToken: String, seriesId: Int, chapterId: Int, rating: Float) {
        updatedRatingSeriesId = seriesId
        updatedRatingChapterId = chapterId
        updatedRating = rating
    }
    override suspend fun updateChapterReview(accessToken: String, seriesId: Int, chapterId: Int, body: String?): KavitaUserReviewDto {
        updatedReviewBody = body
        return KavitaUserReviewDto(seriesId = seriesId, chapterId = chapterId, body = body)
    }
    override suspend fun deleteChapterReview(accessToken: String, chapterId: Int) {
        deletedReviewChapterId = chapterId
        updatedReviewBody = null
    }
    override suspend fun getAllReviews(accessToken: String, userId: Int?): List<KavitaUserReviewDto> {
        requestedReviewUserId = userId
        reviewFailure?.let { throw it }
        return reviews
    }
    override suspend fun getReadingHistory(accessToken: String, libraryId: Int): List<KavitaReadingHistoryItemDto> {
        requestedHistoryLibraries += libraryId
        historyFailuresByLibrary[libraryId]?.let { throw it }
        return historyByLibrary[libraryId].orEmpty()
    }
    override suspend fun downloadPageImage(accessToken: String, chapterId: String, pageIndex: Int, apiKey: String?): ByteArray = ByteArray(0)
    override suspend fun downloadPageThumbnail(accessToken: String, chapterId: String, pageNumber: Int, apiKey: String?): ByteArray = ByteArray(0)
    override suspend fun downloadCover(accessToken: String, coverUrl: String, apiKey: String?): ByteArray = ByteArray(0)
    override suspend fun downloadChapter(accessToken: String, chapterId: String, apiKey: String?, destination: File) = Unit
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
