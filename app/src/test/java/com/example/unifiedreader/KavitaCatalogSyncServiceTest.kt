package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaReadingHistoryItemDto
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaSeriesUpdateEntry
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.kavita.KavitaUserReviewDto
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaCatalogSyncServiceTest {
    @Test
    fun `sync reuses saved api key for kavita covers`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                kavitaSeriesEntry(
                    id = "12",
                    libraryId = "5",
                ),
            ),
        )
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = RecordingKavitaRemoteCatalogWriter(),
        )

        service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
                apiKey = "saved-api-key",
            ),
        )

        assertEquals(listOf("saved-api-key"), api.seriesApiKeys)
    }

    @Test
    fun `sync emits determinate kavita progress updates`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                kavitaSeriesEntry(id = "12", libraryId = "5"),
                kavitaSeriesEntry(id = "13", libraryId = "5"),
                kavitaSeriesEntry(id = "14", libraryId = "5"),
            ),
        )
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = RecordingKavitaRemoteCatalogWriter(),
        )
        val progressUpdates = mutableListOf<KavitaSyncProgress>()

        service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                apiKey = "saved-api-key",
            ),
            onProgress = progressUpdates::add,
        )

        assertTrue(progressUpdates.any { update -> update.message == "Loading Kavita libraries..." })
        assertTrue(progressUpdates.any { update -> update.message.startsWith("Loading Kavita series 3/3") })
        assertTrue(progressUpdates.any { update -> update.message == "Saving Kavita catalog..." })
        assertTrue(progressUpdates.any { update -> update.message == "Applying Kavita progress..." })
    }

    @Test
    fun `sync skips unsupported kavita formats`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                kavitaSeriesEntry(
                    id = "12",
                    libraryId = "5",
                    format = FormatType.PDF,
                ),
            ),
        )
        val writer = RecordingKavitaRemoteCatalogWriter()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        val result = service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                apiKey = "saved-api-key",
            ),
        )

        assertTrue(writer.lastItems.isEmpty())
        assertEquals(0, result.itemCount)
    }

    @Test
    fun `sync retries with refreshed api key after token refresh`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                kavitaSeriesEntry(
                    id = "12",
                    libraryId = "5",
                ),
            ),
            unauthorizedSeriesRequests = 1,
            refreshedSession = KavitaSession(
                accessToken = "refreshed-access-token",
                refreshToken = "refreshed-refresh-token",
                apiKey = "refreshed-api-key",
            ),
        )
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = RecordingKavitaRemoteCatalogWriter(),
        )

        service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
                apiKey = null,
            ),
        )

        assertEquals(listOf(null, "refreshed-api-key"), api.seriesApiKeys)
    }

    @Test
    fun `sync refreshes saved token when journal pull is unauthorized`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5")),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
            currentUserId = 1,
            unauthorizedJournalTokens = setOf("expired-access-token"),
            refreshedSession = KavitaSession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
                apiKey = "fresh-api-key",
            ),
        )
        val journalRepository = KavitaRecordingJournalRepository()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = RecordingKavitaRemoteCatalogWriter(),
            journalRepository = journalRepository,
        )

        val result = service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "expired-access-token",
                refreshToken = "saved-refresh-token",
                apiKey = "expired-api-key",
            ),
        )

        assertEquals(listOf("expired-access-token", "fresh-access-token"), api.currentUserAccessTokens)
        assertEquals(listOf("saved-refresh-token"), api.refreshTokens)
        assertEquals("fresh-access-token", result.session?.accessToken)
        assertEquals(1, journalRepository.replaceCount)
    }

    @Test
    fun `sync maps Kavita zero rating to unrated journal entry`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5")),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
            currentUserId = 1,
            reviews = listOf(
                KavitaUserReviewDto(
                    id = 42,
                    chapterId = 99,
                    rating = 0f,
                    createdUtc = "2026-04-07T06:00:00Z",
                ),
            ),
        )
        val journalRepository = KavitaRecordingJournalRepository()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = RecordingKavitaRemoteCatalogWriter(),
            journalRepository = journalRepository,
        )

        service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
                apiKey = "saved-api-key",
            ),
        )

        assertEquals(null, journalRepository.lastEntries.single().rating)
    }

    @Test
    fun `fast sync preserves unchanged kavita series without refetching detail`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5", addedAtEpochMillis = 500L)
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        )
        val writer = RecordingKavitaRemoteCatalogWriter()
        val readerSessionStore = KavitaTestReaderSessionStore()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        service.sync(request = request)
        service.sync(request = request)

        assertEquals(listOf("12"), api.detailRequests)
        assertEquals(2, api.recentlyUpdatedRequests)
        assertTrue(api.progressRequests.isEmpty())
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { it.id })
        assertEquals(500L, writer.lastItems.single().addedAtEpochMillis)
        assertEquals(3, readerSessionStore.progressByItemIdFlow.value[SourceType.KAVITA.remoteItemId("99")]?.page)
    }

    @Test
    fun `full sync refetches unchanged kavita series details`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        )
        val writer = RecordingKavitaRemoteCatalogWriter()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
            syncMode = KavitaCatalogSyncMode.FULL,
        )

        service.sync(request = request)
        service.sync(request = request)

        assertEquals(listOf("12", "12"), api.detailRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { it.id })
    }

    @Test
    fun `full sync does not require kavita recent update markers`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            recentUpdatesFailure = IllegalStateException("recent updates unavailable"),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        )
        val writer = RecordingKavitaRemoteCatalogWriter()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                apiKey = "saved-api-key",
                syncMode = KavitaCatalogSyncMode.FULL,
            ),
        )

        assertEquals(0, api.recentlyUpdatedRequests)
        assertEquals(listOf("12"), api.detailRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { it.id })
    }

    @Test
    fun `fast sync falls back to detail refresh when recent update markers fail`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            recentUpdatesFailure = IllegalStateException("recent updates unavailable"),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        )
        val writer = RecordingKavitaRemoteCatalogWriter()
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        service.sync(
            request = KavitaCatalogSyncRequest(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                apiKey = "saved-api-key",
            ),
        )

        assertEquals(1, api.recentlyUpdatedRequests)
        assertEquals(listOf("12"), api.detailRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { it.id })
    }

    @Test
    fun `sync refetches changed kavita series`() = runBlocking {
        val writer = RecordingKavitaRemoteCatalogWriter()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )
        val firstApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5", lastChapterAddedAtEpochMillis = 100L)),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        )
        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> firstApi },
            remoteCatalogWriter = writer,
        ).sync(request = request)
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5", lastChapterAddedAtEpochMillis = 101L)),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
        ).sync(request = request)

        assertEquals(listOf("12"), secondApi.detailRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("100")), writer.lastItems.map { it.id })
    }

    @Test
    fun `sync refetches kavita series when update marker changes`() = runBlocking {
        val writer = RecordingKavitaRemoteCatalogWriter()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )
        val series = kavitaSeriesEntry(id = "12", libraryId = "5", lastChapterAddedAtEpochMillis = 100L)
        val firstApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            recentlyUpdatedSeries = listOf(kavitaSeriesUpdateEntry(seriesId = "12", chapterId = "99", createdAt = 100L)),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        )
        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> firstApi },
            remoteCatalogWriter = writer,
        ).sync(request = request)
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            recentlyUpdatedSeries = listOf(kavitaSeriesUpdateEntry(seriesId = "12", chapterId = "100", createdAt = 101L)),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
        ).sync(request = request)

        assertEquals(listOf("12"), secondApi.detailRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("100")), writer.lastItems.map { it.id })
    }

    @Test
    fun `sync removes missing kavita series`() = runBlocking {
        val writer = RecordingKavitaRemoteCatalogWriter()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5")),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).sync(request = request)
        }

        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = emptyList(),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).sync(request = request)
        }

        assertTrue(writer.lastItems.isEmpty())
    }
}

private fun kavitaLibrary(
    remoteId: String,
    title: String = "Comics",
): Library = Library(
    id = SourceType.KAVITA.remoteLibraryId(remoteId),
    sourceType = SourceType.KAVITA,
    title = title,
    remoteId = remoteId,
)

private fun kavitaSeriesEntry(
    id: String,
    libraryId: String,
    format: FormatType = FormatType.CBZ,
    addedAtEpochMillis: Long? = null,
    lastChapterAddedAtEpochMillis: Long = 100L,
): KavitaSeriesEntry = KavitaSeriesEntry(
    id = id,
    libraryId = libraryId,
    title = "Series $id",
    format = format,
    pages = 24,
    addedAtEpochMillis = addedAtEpochMillis,
    lastChapterAddedAtEpochMillis = lastChapterAddedAtEpochMillis,
    coverUrl = "https://kavita.example/api/Image/series-cover?seriesId=$id&apiKey=seed",
)

private fun kavitaChapterEntry(
    chapterId: String,
    libraryId: String,
    seriesId: String,
): KavitaChapterCatalogEntry = KavitaChapterCatalogEntry(
    item = ReadableItem(
        id = SourceType.KAVITA.remoteItemId(chapterId),
        sourceType = SourceType.KAVITA,
        libraryId = SourceType.KAVITA.remoteLibraryId(libraryId),
        remoteId = chapterId,
        remoteFileType = seriesId,
        title = "Chapter $chapterId",
        format = FormatType.CBZ,
        pageCount = 24,
    ),
    progress = ReadingProgress(
        itemId = SourceType.KAVITA.remoteItemId(chapterId),
        locatorType = LocatorType.PAGE,
        page = 3,
        percent = 0.125,
        isFinished = false,
        updatedAtEpochMillis = 1775541600000L,
    ),
    isExplicitUnreadReset = false,
)

private fun kavitaSeriesUpdateEntry(
    seriesId: String,
    chapterId: String,
    createdAt: Long,
): KavitaSeriesUpdateEntry = KavitaSeriesUpdateEntry(
    seriesId = seriesId,
    libraryId = "5",
    createdAtEpochMillis = createdAt,
    chapterId = chapterId,
    volumeId = "7",
    count = 1,
)

private class RecordingKavitaRemoteCatalogWriter : RemoteCatalogWriter {
    var lastItems: List<ReadableItemEntity> = emptyList()
    var syncGroups: List<RemoteCatalogSyncGroup> = emptyList()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = lastItems
        .filter { item -> item.sourceType == sourceType.name }
        .map(ReadableItemEntity::toDomain)

    override suspend fun getCatalogSyncGroups(
        sourceType: SourceType,
    ): List<RemoteCatalogSyncGroup> = syncGroups

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
        syncGroups: List<RemoteCatalogSyncGroupEntity>,
    ) {
        lastItems = items
        this.syncGroups = syncGroups.map { entity ->
            RemoteCatalogSyncGroup(
                groupId = entity.groupId,
                libraryId = entity.libraryId,
                fingerprint = entity.fingerprint,
            )
        }
    }
}

private class KavitaTestReaderSessionStore : ReaderSessionStore {
    private val progressByItemId = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    private val sessionByItemId = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressByItemId
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionByItemId

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressByItemId.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressByItemId.value = progressByItemId.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                isExplicitUnreadReset = isExplicitUnreadReset,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
    }

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean {
        val current = progressByItemId.value[expected.itemId] ?: return false
        if (current != expected) return false
        progressByItemId.value = if (replacement == null) {
            progressByItemId.value - expected.itemId
        } else {
            progressByItemId.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionByItemId.value[item.id]

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        sessionByItemId.value = sessionByItemId.value + (
            itemId to ReaderSessionSnapshot(
                itemId = itemId,
                pageCount = pageCount,
                preferences = preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionByItemId.value = sessionByItemId.value - itemId
    }

    override fun restoreItem(itemId: String) = Unit
}

private class KavitaRecordingJournalRepository : JournalRepository {
    private val snapshot = MutableStateFlow(JournalSnapshot())
    var replaceCount = 0
    var lastEntries = emptyList<ItemJournalEntry>()
    var lastSessions = emptyList<JournalReadingSession>()

    override fun observeSnapshot(): Flow<JournalSnapshot> = snapshot

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(item: ReadableItem, rating: Int?, reviewText: String) = Unit

    override suspend fun saveJournalEntry(item: JournalItemRef, rating: Int?, reviewText: String) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit

    override suspend fun replaceCleanSourceJournalCache(
        sourceType: SourceType,
        events: List<JournalReadingEvent>,
        entries: List<ItemJournalEntry>,
        sessions: List<JournalReadingSession>,
        preserveReviewFieldsWhenIncomingBlank: Boolean,
        preserveRatingWhenIncomingBlank: Boolean,
        preserveReadingEventsWhenIncomingIncomplete: Boolean,
        preserveReadingSessionsWhenIncomingIncomplete: Boolean,
        readingEventReplacementItemIds: Set<String>?,
        readingSessionReplacementItemIds: Set<String>?,
        journalEntryReplacementItemIds: Set<String>?,
        preserveRatingOnlyEntriesWhenIncomingMissing: Boolean,
        ratingOnlyPreservationExcludedItemIds: Set<String>?,
    ) {
        replaceCount += 1
        lastEntries = entries
        lastSessions = sessions
    }
}

private class FakeKavitaApi(
    private val libraries: List<Library>,
    private val series: List<KavitaSeriesEntry>,
    private val recentlyUpdatedSeries: List<KavitaSeriesUpdateEntry> = emptyList(),
    private val recentUpdatesFailure: Throwable? = null,
    private val entriesBySeriesId: Map<String, List<KavitaChapterCatalogEntry>> = emptyMap(),
    private val unauthorizedSeriesRequests: Int = 0,
    private val unauthorizedJournalTokens: Set<String> = emptySet(),
    private val currentUserId: Int? = null,
    private val reviews: List<KavitaUserReviewDto> = emptyList(),
    private val refreshedSession: KavitaSession = KavitaSession(
        accessToken = "refreshed-access-token",
        refreshToken = "refreshed-refresh-token",
        apiKey = "refreshed-api-key",
    ),
) : KavitaApi {
    val seriesApiKeys = mutableListOf<String?>()
    val detailRequests = mutableListOf<String>()
    val progressRequests = mutableListOf<String>()
    val currentUserAccessTokens = mutableListOf<String>()
    val refreshTokens = mutableListOf<String>()
    var recentlyUpdatedRequests = 0
    private var remainingUnauthorizedSeriesRequests = unauthorizedSeriesRequests

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession = KavitaSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        apiKey = "api-key",
    )

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession {
        refreshTokens += refreshToken
        return refreshedSession
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = libraries

    override suspend fun getSeries(
        accessToken: String,
        apiKey: String?,
    ): List<KavitaSeriesEntry> {
        seriesApiKeys += apiKey
        if (remainingUnauthorizedSeriesRequests > 0) {
            remainingUnauthorizedSeriesRequests -= 1
            throw KavitaUnauthorizedException()
        }
        return series
    }

    override suspend fun getRecentlyUpdatedSeries(
        accessToken: String,
    ): List<KavitaSeriesUpdateEntry> {
        recentlyUpdatedRequests += 1
        recentUpdatesFailure?.let { throw it }
        return recentlyUpdatedSeries
    }

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> {
        detailRequests += series.id
        return entriesBySeriesId[series.id].orEmpty()
    }

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto {
        progressRequests += chapterId
        return KavitaProgressDto(
            chapterId = chapterId.toIntOrNull(),
            pageNum = 3,
            lastModifiedUtc = "2026-04-07T06:00:00Z",
        )
    }

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto = error("Not needed in test.")

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) = Unit

    override suspend fun getCurrentUserId(
        accessToken: String,
    ): Int? {
        currentUserAccessTokens += accessToken
        if (accessToken in unauthorizedJournalTokens) {
            throw KavitaUnauthorizedException()
        }
        return currentUserId
    }

    override suspend fun getAllReviews(
        accessToken: String,
        userId: Int?,
    ): List<KavitaUserReviewDto> = reviews

    override suspend fun getReadingHistory(
        accessToken: String,
        libraryId: Int,
    ): List<KavitaReadingHistoryItemDto> = emptyList()

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = ByteArray(0)

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray = ByteArray(0)

    override suspend fun downloadCover(
        accessToken: String,
        coverUrl: String,
        apiKey: String?,
    ): ByteArray = ByteArray(0)

    override suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
        destination: File,
    ) {
        destination.writeBytes(ByteArray(0))
    }
}
