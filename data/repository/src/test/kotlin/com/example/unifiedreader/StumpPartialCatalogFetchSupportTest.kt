package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.data.stump.StumpGraphQlErrorDetail
import com.example.unifiedreader.data.stump.StumpMediaCatalogEntry
import com.example.unifiedreader.data.stump.StumpSeriesDetailResult
import com.example.unifiedreader.data.stump.StumpSeriesEntry
import com.example.unifiedreader.data.stump.StumpSession
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StumpPartialCatalogFetchSupportTest {
    @Test
    fun `partial series detail failures preserve cached entries for affected stump series`() = runTest {
        val existingFailedSeriesItem = stumpItem(
            id = "failed-old",
            seriesId = "series-failed",
        )
        val existingIncompleteSeriesItem = stumpItem(
            id = "partial-old",
            seriesId = "series-partial",
        )
        val existingUnrelatedItem = stumpItem(
            id = "unrelated-old",
            seriesId = "series-unrelated",
        )
        val writer = StumpPartialFetchTestWriter(
            existingItems = listOf(
                existingFailedSeriesItem,
                existingIncompleteSeriesItem,
                existingUnrelatedItem,
            ),
        )
        val api = StumpPartialFetchApi(
            seriesByLibraryId = mapOf(
                "library-1" to listOf(
                    stumpSeries("series-ok"),
                    stumpSeries("series-failed"),
                    stumpSeries("series-partial"),
                ),
            ),
            detailResultsBySeriesId = mapOf(
                "series-ok" to StumpSeriesDetailResult(
                    entries = listOf(stumpEntry("ok-new", "series-ok")),
                ),
                "series-partial" to StumpSeriesDetailResult(
                    entries = listOf(
                        stumpEntry(
                            id = "partial-new",
                            seriesId = "series-partial",
                            isExplicitUnreadReset = true,
                        ),
                    ),
                    errors = listOf(
                        StumpGraphQlErrorDetail(
                            message = "read history failed",
                            path = listOf("seriesById", "media", "readHistory"),
                        ),
                    ),
                ),
            ),
            failedSeriesIds = setOf("series-failed"),
        )

        val result = fetchStumpCatalogWithPartialSeriesRecovery(
            api = api,
            accessToken = "access-token",
            libraries = listOf(stumpLibrary("library-1")),
            remoteCatalogWriter = writer,
            onProgress = { _, _ -> },
        )

        assertEquals(
            listOf(
                "stump:item:ok-new",
                "stump:item:partial-new",
                "stump:item:failed-old",
                "stump:item:partial-old",
            ),
            result.fetch.entries.map { entry -> entry.item.id },
        )
        assertFalse(result.fetch.entries.first { entry -> entry.item.id == "stump:item:partial-new" }.isExplicitUnreadReset)
        assertEquals(1, result.warning?.failedSeriesCount)
        assertEquals(1, result.warning?.incompleteSeriesCount)
        assertEquals(2, result.warning?.preservedItemCount)
        assertEquals("seriesById.media.readHistory: read history failed", result.warning?.firstGraphQlMessage)
        assertNull(result.fetch.entries.first { entry -> entry.item.id == "stump:item:failed-old" }.progress)
    }
}

private fun stumpLibrary(
    remoteId: String,
): Library = Library(
    id = SourceType.STUMP.remoteLibraryId(remoteId),
    sourceType = SourceType.STUMP,
    title = "Library $remoteId",
    remoteId = remoteId,
    contentType = LibraryContentType.COMICS,
)

private fun stumpSeries(
    id: String,
): StumpSeriesEntry = StumpSeriesEntry(
    id = id,
    libraryId = "library-1",
    title = "Series $id",
    coverUrl = null,
)

private fun stumpEntry(
    id: String,
    seriesId: String,
    isExplicitUnreadReset: Boolean = false,
): StumpMediaCatalogEntry = StumpMediaCatalogEntry(
    item = stumpItem(
        id = id,
        seriesId = seriesId,
    ),
    progress = null,
    isExplicitUnreadReset = isExplicitUnreadReset,
)

private fun stumpItem(
    id: String,
    seriesId: String,
): ReadableItem = ReadableItem(
    id = SourceType.STUMP.remoteItemId(id),
    sourceType = SourceType.STUMP,
    libraryId = SourceType.STUMP.remoteLibraryId("library-1"),
    remoteId = id,
    remoteFileType = seriesId,
    title = "Issue $id",
    format = FormatType.CBZ,
    pageCount = 24,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = null,
    comicMetadata = null,
)

private class StumpPartialFetchTestWriter(
    private val existingItems: List<ReadableItem>,
) : RemoteCatalogWriter {
    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = existingItems

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
        syncGroups: List<RemoteCatalogSyncGroupEntity>,
    ) = error("Not needed in test.")

    override suspend fun applySourceCatalogDelta(
        delta: RemoteCatalogDelta,
    ) = error("Not needed in test.")
}

private class StumpPartialFetchApi(
    private val seriesByLibraryId: Map<String, List<StumpSeriesEntry>>,
    private val detailResultsBySeriesId: Map<String, StumpSeriesDetailResult>,
    private val failedSeriesIds: Set<String>,
) : StumpApi {
    override suspend fun login(
        username: String,
        password: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession = error("Not needed in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = error("Not needed in test.")

    override suspend fun getSeries(
        accessToken: String,
        libraryId: String,
    ): List<StumpSeriesEntry> = seriesByLibraryId[libraryId].orEmpty()

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: StumpSeriesEntry,
    ): List<StumpMediaCatalogEntry> = getSeriesDetailResult(
        accessToken = accessToken,
        series = series,
    ).entries

    override suspend fun getSeriesDetailResult(
        accessToken: String,
        series: StumpSeriesEntry,
    ): StumpSeriesDetailResult {
        if (series.id in failedSeriesIds) {
            error("detail failed for ${series.id}")
        }
        return detailResultsBySeriesId[series.id] ?: StumpSeriesDetailResult(entries = emptyList())
    }

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
    ) = error("Not needed in test.")

    override suspend fun markMediaComplete(
        accessToken: String,
        mediaId: String,
        isComplete: Boolean,
        pageIndex: Int?,
    ) = error("Not needed in test.")

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

    override suspend fun downloadMediaFile(
        accessToken: String,
        mediaId: String,
        destination: File,
    ) = error("Not needed in test.")
}
