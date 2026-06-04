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
import com.example.unifiedreader.data.kavita.KavitaSeriesUpdateEntry
import com.example.unifiedreader.data.kavita.KavitaSession
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun `sync fetches kavita series details serially`() = runBlocking {
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                kavitaSeriesEntry(id = "12", libraryId = "5"),
                kavitaSeriesEntry(id = "13", libraryId = "5"),
                kavitaSeriesEntry(id = "14", libraryId = "5"),
            ),
            detailDelayMillis = 10L,
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
                apiKey = "saved-api-key",
            ),
        )

        assertEquals(1, api.maxConcurrentDetailRequests.get())
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
    fun `sync reports kavita journal push failures`() = runBlocking {
        val chapter = kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")
        val api = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5")),
            entriesBySeriesId = mapOf("12" to listOf(chapter)),
        )
        val service = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> api },
            remoteCatalogWriter = RecordingKavitaRemoteCatalogWriter(),
            journalRepository = KavitaRecordingJournalRepository(
                dirtyEntries = listOf(journalEntry(chapter.item, rating = 8)),
            ),
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

        assertEquals(1, result.progressPushFailures.size)
        assertEquals(
            "Not needed in test.",
            result.progressPushFailures.single().message,
        )
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
    fun `sync refetches unchanged kavita series details`() = runBlocking {
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

        assertEquals(listOf("12", "12"), api.detailRequests)
        assertEquals(0, api.recentlyUpdatedRequests)
        assertTrue(api.progressRequests.isEmpty())
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { it.id })
        assertEquals(1, writer.syncGroups.size)
        assertEquals(3, readerSessionStore.progressByItemIdFlow.value[SourceType.KAVITA.remoteItemId("99")]?.page)
    }

    @Test
    fun `fast sync skips unchanged kavita series details after full sync`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5", addedAtEpochMillis = 500L)
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
            series = listOf(series),
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
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
        ).fastSync(request = request)

        assertEquals(listOf("12"), firstApi.detailRequests)
        assertTrue(secondApi.detailRequests.isEmpty())
        assertEquals(1, secondApi.recentlyUpdatedRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { it.id })
    }

    @Test
    fun `fast sync refetches changed kavita series and removes stale chapters`() = runBlocking {
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
        ).fastSync(request = request)
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(kavitaSeriesEntry(id = "12", libraryId = "5", lastChapterAddedAtEpochMillis = 101L)),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
        )

        val result = KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
        ).fastSync(request = request)

        assertEquals(listOf("12"), secondApi.detailRequests)
        assertEquals(1, result.itemCount)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("100")), writer.lastItems.map { it.id })
    }

    @Test
    fun `fast sync refetches recently updated kavita series`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5", addedAtEpochMillis = 500L)
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
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
        }
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            recentUpdates = listOf(
                kavitaRecentUpdateEntry(
                    seriesId = "12",
                    libraryId = "5",
                    chapterId = "100",
                ),
            ),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
        ).fastSync(request = request)

        assertEquals(listOf("12"), secondApi.detailRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("100")), writer.lastItems.map { it.id })
    }

    @Test
    fun `fast sync does not repeatedly refetch stable kavita recent updates`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val recentUpdates = listOf(
            kavitaRecentUpdateEntry(
                seriesId = "12",
                libraryId = "5",
                chapterId = "100",
            ),
        )
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
            series = listOf(series),
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
            series = listOf(series),
            recentUpdates = recentUpdates,
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
            assertEquals(listOf("12"), api.detailRequests)
        }
        val repeatedApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            recentUpdates = recentUpdates,
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "101", libraryId = "5", seriesId = "12")),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> repeatedApi },
            remoteCatalogWriter = writer,
        ).fastSync(request = request)

        assertTrue(repeatedApi.detailRequests.isEmpty())
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("100")), writer.lastItems.map { it.id })
    }

    @Test
    fun `fast sync only refetches summary changes when kavita recent feed is stable`() = runBlocking {
        val firstSeries = kavitaSeriesEntry(id = "12", libraryId = "5")
        val secondSeries = kavitaSeriesEntry(id = "13", libraryId = "5")
        val recentUpdates = listOf(
            kavitaRecentUpdateEntry(seriesId = "12", libraryId = "5", chapterId = "100"),
            kavitaRecentUpdateEntry(seriesId = "13", libraryId = "5", chapterId = "200"),
        )
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
            series = listOf(firstSeries, secondSeries),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
                "13" to listOf(kavitaChapterEntry(chapterId = "199", libraryId = "5", seriesId = "13")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).sync(request = request)
        }
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(firstSeries, secondSeries),
            recentUpdates = recentUpdates,
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
                "13" to listOf(kavitaChapterEntry(chapterId = "200", libraryId = "5", seriesId = "13")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
            assertEquals(listOf("12", "13"), api.detailRequests)
        }
        val changedApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                firstSeries.copy(title = "Retitled Series"),
                secondSeries,
            ),
            recentUpdates = recentUpdates,
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "101", libraryId = "5", seriesId = "12")),
                "13" to listOf(kavitaChapterEntry(chapterId = "201", libraryId = "5", seriesId = "13")),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> changedApi },
            remoteCatalogWriter = writer,
        ).fastSync(request = request)

        assertEquals(listOf("12"), changedApi.detailRequests)
        assertEquals(
            listOf(
                SourceType.KAVITA.remoteItemId("200"),
                SourceType.KAVITA.remoteItemId("101"),
            ),
            writer.lastItems.map { it.id },
        )
    }

    @Test
    fun `fast sync removes missing kavita series`() = runBlocking {
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
            ).fastSync(request = request)
        }

        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = emptyList(),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
            assertTrue(api.detailRequests.isEmpty())
        }

        assertTrue(writer.lastItems.isEmpty())
        assertTrue(writer.syncGroups.isEmpty())
    }

    @Test
    fun `fast sync pulls kavita journal for unchanged entries`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val writer = RecordingKavitaRemoteCatalogWriter()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
            pullJournalDuringFastSync = true,
        )
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
        }
        val journalRepository = KavitaRecordingJournalRepository()
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            currentUserId = 1,
            reviews = listOf(
                KavitaUserReviewDto(
                    id = 42,
                    chapterId = 99,
                    rating = 4f,
                    body = "Worth keeping",
                    createdUtc = "2026-04-07T06:00:00Z",
                ),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
            journalRepository = journalRepository,
        ).fastSync(request = request)

        assertTrue(secondApi.detailRequests.isEmpty())
        assertEquals(1, journalRepository.replaceCount)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), journalRepository.lastEntries.map { entry -> entry.itemId })
    }

    @Test
    fun `fast sync skips kavita journal pull by default`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
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
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
        }
        val journalRepository = KavitaRecordingJournalRepository()
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            currentUserId = 1,
            reviews = listOf(
                KavitaUserReviewDto(
                    id = 42,
                    chapterId = 99,
                    rating = 4f,
                    body = "Worth keeping",
                    createdUtc = "2026-04-07T06:00:00Z",
                ),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
            journalRepository = journalRepository,
        ).fastSync(request = request)

        assertTrue(secondApi.detailRequests.isEmpty())
        assertTrue(secondApi.currentUserAccessTokens.isEmpty())
        assertEquals(0, journalRepository.replaceCount)
    }

    @Test
    fun `fast sync pulls kavita progress for unchanged entries`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val writer = RecordingKavitaRemoteCatalogWriter()
        val readerSessionStore = KavitaTestReaderSessionStore()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
            pullUnchangedProgressDuringFastSync = true,
            pullJournalDuringFastSync = true,
        )
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
                readerSessionStore = readerSessionStore,
            ).fastSync(request = request)
        }
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            progressByChapterId = mapOf(
                "99" to KavitaProgressDto(
                    chapterId = 99,
                    pageNum = 10,
                    lastModifiedUtc = "2026-04-08T06:00:00Z",
                ),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        ).fastSync(request = request)

        val item = writer.getItemsForSourceType(SourceType.KAVITA).single()
        val progress = readerSessionStore.getProgress(item)
        assertTrue(secondApi.detailRequests.isEmpty())
        assertEquals(listOf("99"), secondApi.progressRequests)
        assertEquals(10, progress?.page)
        assertEquals(SyncState.CLEAN, progress?.syncState)
    }

    @Test
    fun `fast sync skips unchanged progress pull by default`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val writer = RecordingKavitaRemoteCatalogWriter()
        val readerSessionStore = KavitaTestReaderSessionStore()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
                readerSessionStore = readerSessionStore,
            ).fastSync(request = request)
        }
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            progressByChapterId = mapOf(
                "99" to KavitaProgressDto(
                    chapterId = 99,
                    pageNum = 10,
                    lastModifiedUtc = "2026-04-08T06:00:00Z",
                ),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        ).fastSync(request = request)

        assertTrue(secondApi.detailRequests.isEmpty())
        assertTrue(secondApi.progressRequests.isEmpty())
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { item -> item.id })
    }

    @Test
    fun `fast sync leaves catalog unchanged when unchanged progress pull fails`() = runBlocking {
        val changedSeries = kavitaSeriesEntry(id = "12", libraryId = "5", lastChapterAddedAtEpochMillis = 100L)
        val unchangedSeries = kavitaSeriesEntry(id = "13", libraryId = "5")
        val writer = RecordingKavitaRemoteCatalogWriter()
        val readerSessionStore = KavitaTestReaderSessionStore()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
            pullUnchangedProgressDuringFastSync = true,
        )
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(changedSeries, unchangedSeries),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
                "13" to listOf(kavitaChapterEntry(chapterId = "199", libraryId = "5", seriesId = "13")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
                readerSessionStore = readerSessionStore,
            ).sync(request = request)
        }
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(
                changedSeries.copy(lastChapterAddedAtEpochMillis = 101L),
                unchangedSeries,
            ),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
            ),
            progressFailuresByChapterId = mapOf(
                "199" to IllegalStateException("progress unavailable"),
            ),
        )

        try {
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
                remoteCatalogWriter = writer,
                readerSessionStore = readerSessionStore,
            ).fastSync(request = request)
            fail("Expected unchanged progress pull failure.")
        } catch (exception: IllegalStateException) {
            assertEquals("progress unavailable", exception.message)
        }

        assertEquals(listOf("12"), secondApi.detailRequests)
        assertEquals(listOf("199"), secondApi.progressRequests)
        assertEquals(
            listOf(
                SourceType.KAVITA.remoteItemId("99"),
                SourceType.KAVITA.remoteItemId("199"),
            ),
            writer.lastItems.map { item -> item.id },
        )
    }

    @Test
    fun `fast sync keeps progress journal events for unchanged entries`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
        val writer = RecordingKavitaRemoteCatalogWriter()
        val request = KavitaCatalogSyncRequest(
            displayName = "Kavita",
            baseUrl = "https://kavita.example",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
            pullUnchangedProgressDuringFastSync = true,
            pullJournalDuringFastSync = true,
        )
        FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
        }
        val journalRepository = KavitaRecordingJournalRepository()
        val secondApi = FakeKavitaApi(
            libraries = listOf(kavitaLibrary("5")),
            series = listOf(series),
            currentUserId = 1,
            progressByChapterId = mapOf(
                "99" to KavitaProgressDto(
                    chapterId = 99,
                    pageNum = 24,
                    lastModifiedUtc = "2026-04-08T06:00:00Z",
                ),
            ),
        )

        KavitaCatalogSyncService(
            kavitaApiFactory = KavitaApiFactory { _ -> secondApi },
            remoteCatalogWriter = writer,
            journalRepository = journalRepository,
        ).fastSync(request = request)

        assertTrue(secondApi.detailRequests.isEmpty())
        assertEquals(listOf("99"), secondApi.progressRequests)
        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), journalRepository.lastEvents.map { event -> event.itemId })
    }

    @Test
    fun `fast sync fails when kavita recent update markers are unavailable`() = runBlocking {
        val series = kavitaSeriesEntry(id = "12", libraryId = "5")
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
            series = listOf(series),
            entriesBySeriesId = mapOf(
                "12" to listOf(kavitaChapterEntry(chapterId = "99", libraryId = "5", seriesId = "12")),
            ),
        ).also { api ->
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ -> api },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
        }

        try {
            KavitaCatalogSyncService(
                kavitaApiFactory = KavitaApiFactory { _ ->
                    FakeKavitaApi(
                        libraries = listOf(kavitaLibrary("5")),
                        series = listOf(series),
                        recentUpdatesFailure = IllegalStateException("recent markers unavailable"),
                        entriesBySeriesId = mapOf(
                            "12" to listOf(kavitaChapterEntry(chapterId = "100", libraryId = "5", seriesId = "12")),
                        ),
                    )
                },
                remoteCatalogWriter = writer,
            ).fastSync(request = request)
            fail("Expected recent update marker failure.")
        } catch (exception: RuntimeException) {
            assertTrue(exception.message.orEmpty().contains("recent update markers"))
        }

        assertEquals(listOf(SourceType.KAVITA.remoteItemId("99")), writer.lastItems.map { item -> item.id })
    }

    @Test
    fun `sync does not require kavita recent update markers`() = runBlocking {
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

        assertEquals(0, api.recentlyUpdatedRequests)
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
    title: String = "Series $id",
    format: FormatType = FormatType.CBZ,
    addedAtEpochMillis: Long? = null,
    lastChapterAddedAtEpochMillis: Long = 100L,
): KavitaSeriesEntry = KavitaSeriesEntry(
    id = id,
    libraryId = libraryId,
    title = title,
    format = format,
    pages = 24,
    addedAtEpochMillis = addedAtEpochMillis,
    lastChapterAddedAtEpochMillis = lastChapterAddedAtEpochMillis,
    coverUrl = "https://kavita.example/api/Image/series-cover?seriesId=$id&apiKey=seed",
)

private fun kavitaRecentUpdateEntry(
    seriesId: String,
    libraryId: String? = null,
    createdAtEpochMillis: Long? = null,
    chapterId: String? = null,
    volumeId: String? = null,
    count: Int? = 1,
): KavitaSeriesUpdateEntry = KavitaSeriesUpdateEntry(
    seriesId = seriesId,
    libraryId = libraryId,
    createdAtEpochMillis = createdAtEpochMillis,
    chapterId = chapterId,
    volumeId = volumeId,
    count = count,
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

    override suspend fun applySourceCatalogDelta(
        delta: RemoteCatalogDelta,
    ) {
        val itemsById = linkedMapOf<String, ReadableItemEntity>()
        lastItems
            .filterNot { item ->
                item.sourceType == delta.sourceType.name && item.id in delta.removeItemIds
            }
            .forEach { item -> itemsById[item.id] = item }
        delta.upsertItems.forEach { item -> itemsById[item.id] = item }
        lastItems = itemsById.values.toList()
        val retainedSyncGroups = syncGroups.filterNot { group ->
            group.groupId in delta.removeSyncGroupIds ||
                delta.syncGroups.any { incoming -> incoming.groupId == group.groupId }
        }
        syncGroups = retainedSyncGroups + delta.syncGroups.map { entity ->
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

private class KavitaRecordingJournalRepository(
    private val dirtyEntries: List<ItemJournalEntry> = emptyList(),
) : JournalRepository {
    private val snapshot = MutableStateFlow(JournalSnapshot())
    var replaceCount = 0
    var lastEvents = emptyList<JournalReadingEvent>()
    var lastEntries = emptyList<ItemJournalEntry>()
    var lastSessions = emptyList<JournalReadingSession>()

    override fun observeSnapshot(): Flow<JournalSnapshot> = snapshot

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(item: ReadableItem, rating: Int?, reviewText: String) = Unit

    override suspend fun saveJournalEntry(item: JournalItemRef, rating: Int?, reviewText: String) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit

    override suspend fun getDirtyJournalEntriesForSource(
        sourceType: SourceType,
    ): List<ItemJournalEntry> = dirtyEntries.filter { entry -> entry.sourceType == sourceType }

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
        lastEvents = events
        lastEntries = entries
        lastSessions = sessions
    }
}

private fun journalEntry(
    item: ReadableItem,
    rating: Int? = null,
    reviewText: String = "",
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
    reviewedAtEpochMillis = null,
    updatedAtEpochMillis = 1_000L,
    syncState = SyncState.DIRTY,
)

private class FakeKavitaApi(
    private val libraries: List<Library>,
    private val series: List<KavitaSeriesEntry>,
    private val recentUpdatesFailure: Throwable? = null,
    private val recentUpdates: List<KavitaSeriesUpdateEntry> = emptyList(),
    private val entriesBySeriesId: Map<String, List<KavitaChapterCatalogEntry>> = emptyMap(),
    private val progressByChapterId: Map<String, KavitaProgressDto> = emptyMap(),
    private val progressFailuresByChapterId: Map<String, Throwable> = emptyMap(),
    private val unauthorizedSeriesRequests: Int = 0,
    private val unauthorizedJournalTokens: Set<String> = emptySet(),
    private val currentUserId: Int? = null,
    private val reviews: List<KavitaUserReviewDto> = emptyList(),
    private val detailDelayMillis: Long = 0L,
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
    val maxConcurrentDetailRequests = AtomicInteger(0)
    var recentlyUpdatedRequests = 0
    private var remainingUnauthorizedSeriesRequests = unauthorizedSeriesRequests
    private val inFlightDetailRequests = AtomicInteger(0)

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
        return recentUpdates
    }

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> {
        val inFlight = inFlightDetailRequests.incrementAndGet()
        maxConcurrentDetailRequests.accumulateAndGet(inFlight, ::maxOf)
        try {
            if (detailDelayMillis > 0) {
                delay(detailDelayMillis)
            }
            detailRequests += series.id
            return entriesBySeriesId[series.id].orEmpty()
        } finally {
            inFlightDetailRequests.decrementAndGet()
        }
    }

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto {
        progressRequests += chapterId
        progressFailuresByChapterId[chapterId]?.let { throw it }
        return progressByChapterId[chapterId] ?: KavitaProgressDto(
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
