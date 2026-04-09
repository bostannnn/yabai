package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.data.stump.StumpUnauthorizedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StumpCatalogSyncServiceTest {
    @Test
    fun `sync loads series for each stump library`() = runBlocking {
        val api = FakeStumpCatalogApi(
            libraries = listOf(
                stumpLibrary("library-1", title = "Comics A"),
                stumpLibrary("library-2", title = "Comics B"),
            ),
            seriesByLibraryId = mapOf(
                "library-1" to listOf(stumpSeriesEntry(id = "series-1", libraryId = "library-1")),
                "library-2" to listOf(stumpSeriesEntry(id = "series-2", libraryId = "library-2")),
            ),
            entriesBySeriesId = mapOf(
                "series-1" to listOf(stumpMediaEntry(id = "media-1", libraryId = "library-1", seriesId = "series-1")),
                "series-2" to listOf(stumpMediaEntry(id = "media-2", libraryId = "library-2", seriesId = "series-2")),
            ),
        )
        val service = StumpCatalogSyncService(
            stumpApiFactory = StumpApiFactory { _ -> api },
            remoteCatalogWriter = RecordingStumpRemoteCatalogWriter(),
        )

        service.sync(
            request = StumpCatalogSyncRequest(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(listOf("library-1", "library-2"), api.requestedLibraryIds)
    }

    @Test
    fun `sync emits determinate stump progress updates`() = runBlocking {
        val api = FakeStumpCatalogApi(
            libraries = listOf(stumpLibrary("library-1")),
            seriesByLibraryId = mapOf(
                "library-1" to listOf(
                    stumpSeriesEntry(id = "series-1", libraryId = "library-1"),
                    stumpSeriesEntry(id = "series-2", libraryId = "library-1"),
                    stumpSeriesEntry(id = "series-3", libraryId = "library-1"),
                ),
            ),
            entriesBySeriesId = mapOf(
                "series-1" to listOf(stumpMediaEntry(id = "media-1", libraryId = "library-1", seriesId = "series-1")),
                "series-2" to listOf(stumpMediaEntry(id = "media-2", libraryId = "library-1", seriesId = "series-2")),
                "series-3" to listOf(stumpMediaEntry(id = "media-3", libraryId = "library-1", seriesId = "series-3")),
            ),
        )
        val service = StumpCatalogSyncService(
            stumpApiFactory = StumpApiFactory { _ -> api },
            remoteCatalogWriter = RecordingStumpRemoteCatalogWriter(),
        )
        val progressUpdates = mutableListOf<StumpSyncProgress>()

        service.sync(
            request = StumpCatalogSyncRequest(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "saved-access-token",
            ),
            onProgress = progressUpdates::add,
        )

        assertTrue(progressUpdates.any { update -> update.message == "Loading Stump libraries..." })
        assertTrue(progressUpdates.any { update -> update.message.startsWith("Loading Stump series 3/3") })
        assertTrue(progressUpdates.any { update -> update.message == "Saving Stump catalog..." })
        assertTrue(progressUpdates.any { update -> update.message == "Applying Stump progress..." })
    }

    @Test
    fun `sync skips unsupported stump formats`() = runBlocking {
        val api = FakeStumpCatalogApi(
            libraries = listOf(stumpLibrary("library-1")),
            seriesByLibraryId = mapOf(
                "library-1" to listOf(
                    stumpSeriesEntry(id = "series-1", libraryId = "library-1"),
                ),
            ),
            entriesBySeriesId = mapOf(
                "series-1" to listOf(
                    stumpMediaEntry(
                        id = "media-1",
                        libraryId = "library-1",
                        seriesId = "series-1",
                        format = FormatType.PDF,
                    ),
                ),
            ),
        )
        val writer = RecordingStumpRemoteCatalogWriter()
        val service = StumpCatalogSyncService(
            stumpApiFactory = StumpApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        val result = service.sync(
            request = StumpCatalogSyncRequest(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "saved-access-token",
            ),
        )

        assertTrue(writer.lastItems.isEmpty())
        assertEquals(0, result.itemCount)
    }

    @Test
    fun `sync retries with refreshed token after token refresh`() = runBlocking {
        val api = FakeStumpCatalogApi(
            libraries = listOf(stumpLibrary("library-1")),
            seriesByLibraryId = mapOf(
                "library-1" to listOf(stumpSeriesEntry(id = "series-1", libraryId = "library-1")),
            ),
            entriesBySeriesId = mapOf(
                "series-1" to listOf(stumpMediaEntry(id = "media-1", libraryId = "library-1", seriesId = "series-1")),
            ),
            unauthorizedSeriesRequests = 1,
            refreshedSession = StumpSession(
                accessToken = "refreshed-access-token",
                refreshToken = "refreshed-refresh-token",
            ),
        )
        val service = StumpCatalogSyncService(
            stumpApiFactory = StumpApiFactory { _ -> api },
            remoteCatalogWriter = RecordingStumpRemoteCatalogWriter(),
        )

        service.sync(
            request = StumpCatalogSyncRequest(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(
            listOf("saved-access-token", "refreshed-access-token"),
            api.seriesAccessTokens.distinct(),
        )
    }
}

private fun stumpLibrary(
    remoteId: String,
    title: String = "Comics",
): Library = Library(
    id = SourceType.STUMP.remoteLibraryId(remoteId),
    sourceType = SourceType.STUMP,
    title = title,
    remoteId = remoteId,
)

private fun stumpSeriesEntry(
    id: String,
    libraryId: String,
): StumpSeriesEntry = StumpSeriesEntry(
    id = id,
    libraryId = libraryId,
    title = "Series $id",
    coverUrl = "https://stump.example/series/$id/cover",
)

private fun stumpMediaEntry(
    id: String,
    libraryId: String,
    seriesId: String,
    format: FormatType = FormatType.CBZ,
): StumpMediaCatalogEntry = StumpMediaCatalogEntry(
    item = ReadableItem(
        id = SourceType.STUMP.remoteItemId(id),
        sourceType = SourceType.STUMP,
        libraryId = SourceType.STUMP.remoteLibraryId(libraryId),
        remoteId = id,
        remoteFileType = seriesId,
        title = "Issue $id",
        format = format,
        pageCount = 24,
        coverUrl = null,
        description = null,
        addedAtEpochMillis = null,
        comicMetadata = null,
    ),
    progress = null,
)

private class RecordingStumpRemoteCatalogWriter : RemoteCatalogWriter {
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

private class FakeStumpCatalogApi(
    private val libraries: List<Library>,
    private val seriesByLibraryId: Map<String, List<StumpSeriesEntry>>,
    private val entriesBySeriesId: Map<String, List<StumpMediaCatalogEntry>> = emptyMap(),
    private val unauthorizedSeriesRequests: Int = 0,
    private val refreshedSession: StumpSession = StumpSession(
        accessToken = "refreshed-access-token",
        refreshToken = "refreshed-refresh-token",
    ),
) : StumpApi {
    val requestedLibraryIds = mutableListOf<String>()
    val seriesAccessTokens = mutableListOf<String>()
    private var remainingUnauthorizedSeriesRequests = unauthorizedSeriesRequests

    override suspend fun login(
        username: String,
        password: String,
    ): StumpSession = StumpSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
    )

    override suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession = refreshedSession

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = libraries

    override suspend fun getSeries(
        accessToken: String,
        libraryId: String,
    ): List<StumpSeriesEntry> {
        requestedLibraryIds += libraryId
        seriesAccessTokens += accessToken
        if (remainingUnauthorizedSeriesRequests > 0) {
            remainingUnauthorizedSeriesRequests -= 1
            throw StumpUnauthorizedException()
        }
        return seriesByLibraryId[libraryId].orEmpty()
    }

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: StumpSeriesEntry,
    ): List<StumpMediaCatalogEntry> = entriesBySeriesId[series.id].orEmpty()

    override suspend fun getProgress(
        accessToken: String,
        mediaId: String,
        itemId: String,
        pageCount: Int?,
    ) = error("Not needed in test.")

    override suspend fun updatePagedProgress(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ) = Unit

    override suspend fun markMediaComplete(
        accessToken: String,
        mediaId: String,
        isComplete: Boolean,
        pageIndex: Int?,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadThumbnail(
        accessToken: String,
        mediaId: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadSeriesThumbnail(
        accessToken: String,
        seriesId: String,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadUrl(
        accessToken: String,
        url: String,
    ): ByteArray = error("Not needed in test.")
}
