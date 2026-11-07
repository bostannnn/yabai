package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.feature.library.covers.GRIMMORY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.GRIMMORY_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory
import javax.inject.Inject

interface GrimmoryCoverDataSource : RemoteCoverDataSource {
    override val usesRemoteDisplayFallback: Boolean
        get() = false
}

class GrimmoryCoverRepository @Inject constructor(
    grimmoryApiFactory: GrimmoryApiFactory,
    grimmoryCredentialStore: GrimmoryCredentialStore,
    coverCacheResolver: RemoteCoverCacheResolver,
) : SourceCoverRepository<GrimmoryStoredSession, GrimmoryApi>(
    sourceType = SourceType.GRIMMORY,
    storage = RemoteCoverStorageDirectory(
        storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
        legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
    ),
    credentialStore = grimmoryCredentialStore,
    createApi = grimmoryApiFactory::create,
    coverCacheResolver = coverCacheResolver,
),
    GrimmoryCoverDataSource {
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override fun ReadableItem.coverCacheFileName(): String = grimmoryCacheFileName("cover")

    override fun ReadableItem.legacyCoverCacheFileNames(session: GrimmoryStoredSession?): List<String> =
        grimmoryLegacyCacheFileNames(extension = "cover", session = session)

    override suspend fun downloadCover(api: GrimmoryApi, session: GrimmoryStoredSession, item: ReadableItem): ByteArray =
        sessionRequestRunner.run(api = api, session = session) { accessToken ->
            api.downloadBookCover(
                accessToken = accessToken,
                bookId = item.remoteId ?: error("Unable to resolve Grimmory book id."),
                coverUrl = item.coverUrl,
            )
        }
}
