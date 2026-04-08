package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
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
): KavitaSeriesEntry = KavitaSeriesEntry(
    id = id,
    libraryId = libraryId,
    title = "Series $id",
    format = format,
    addedAtEpochMillis = null,
    coverUrl = "https://kavita.example/api/Image/series-cover?seriesId=$id&apiKey=seed",
)

private class RecordingKavitaRemoteCatalogWriter : RemoteCatalogWriter {
    var lastItems: List<ReadableItemEntity> = emptyList()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = emptyList()

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) {
        lastItems = items
    }
}

private class FakeKavitaApi(
    private val libraries: List<Library>,
    private val series: List<KavitaSeriesEntry>,
    private val entriesBySeriesId: Map<String, List<KavitaChapterCatalogEntry>> = emptyMap(),
    private val unauthorizedSeriesRequests: Int = 0,
    private val refreshedSession: KavitaSession = KavitaSession(
        accessToken = "refreshed-access-token",
        refreshToken = "refreshed-refresh-token",
        apiKey = "refreshed-api-key",
    ),
) : KavitaApi {
    val seriesApiKeys = mutableListOf<String?>()
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
    ): KavitaSession = refreshedSession

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

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> = entriesBySeriesId[series.id].orEmpty()

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto = error("Not needed in test.")

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto = error("Not needed in test.")

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = ByteArray(0)
}
