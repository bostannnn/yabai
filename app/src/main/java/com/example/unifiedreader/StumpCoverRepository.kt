package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory
import com.example.unifiedreader.feature.library.covers.STUMP_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.STUMP_LEGACY_COVER_DIRECTORY_NAME
import javax.inject.Inject

interface StumpCoverDataSource : RemoteCoverDataSource

class StumpCoverRepository @Inject constructor(
    stumpApiFactory: StumpApiFactory,
    stumpCredentialStore: StumpCredentialStore,
    coverCacheResolver: RemoteCoverCacheResolver,
) : SourceCoverRepository<StumpStoredSession, StumpApi>(
    sourceType = SourceType.STUMP,
    storage = RemoteCoverStorageDirectory(
        storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
        legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
    ),
    credentialStore = stumpCredentialStore,
    createApi = stumpApiFactory::create,
    coverCacheResolver = coverCacheResolver,
),
    StumpCoverDataSource {
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override fun ReadableItem.coverCacheFileName(): String = remoteCacheFileName("cover")

    override fun ReadableItem.legacyCoverCacheFileNames(session: StumpStoredSession?): List<String> =
        remoteLegacyCacheFileNames(extension = "cover", session = session)

    /** A series uses its own thumbnail; a book its cover link, or its thumbnail when the link fails or is missing. */
    override suspend fun downloadCover(api: StumpApi, session: StumpStoredSession, item: ReadableItem): ByteArray =
        sessionRequestRunner.run(api = api, session = session) { accessToken ->
            val coverUrl = item.coverUrl?.takeIf(String::isNotBlank)
            when {
                item.isStumpSeriesContainer() -> api.downloadSeriesThumbnail(
                    accessToken = accessToken,
                    seriesId = item.remoteFileId ?: error("Unable to resolve Stump series id."),
                )
                coverUrl != null -> runSuspendCatching { api.downloadUrl(accessToken = accessToken, url = coverUrl) }
                    .getOrElse { api.downloadMediaThumbnail(accessToken, item) }
                else -> api.downloadMediaThumbnail(accessToken, item)
            }
        }
}

private suspend fun StumpApi.downloadMediaThumbnail(accessToken: String, item: ReadableItem): ByteArray =
    downloadThumbnail(
        accessToken = accessToken,
        mediaId = item.remoteId ?: error("Unable to resolve Stump media id."),
    )
