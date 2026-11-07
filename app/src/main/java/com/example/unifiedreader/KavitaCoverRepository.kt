package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory
import javax.inject.Inject

interface KavitaCoverDataSource : RemoteCoverDataSource {
    override val usesRemoteDisplayFallback: Boolean
        get() = false
}

class KavitaCoverRepository @Inject constructor(
    kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    coverCacheResolver: RemoteCoverCacheResolver,
) : SourceCoverRepository<KavitaStoredSession, KavitaApi>(
    sourceType = SourceType.KAVITA,
    storage = RemoteCoverStorageDirectory(
        storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
        legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
    ),
    credentialStore = kavitaCredentialStore,
    createApi = kavitaApiFactory::create,
    coverCacheResolver = coverCacheResolver,
),
    KavitaCoverDataSource {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override fun ReadableItem.coverCacheFileName(): String = remoteCacheFileName("cover")

    override fun ReadableItem.legacyCoverCacheFileNames(session: KavitaStoredSession?): List<String> =
        remoteLegacyCacheFileNames(extension = "cover", session = session)

    override fun KavitaStoredSession.withActiveBaseUrl(activeBaseUrl: String) = copy(activeBaseUrl = activeBaseUrl)

    override suspend fun downloadCover(api: KavitaApi, session: KavitaStoredSession, item: ReadableItem): ByteArray =
        sessionRequestRunner.run(api = api, session = session) { accessToken, apiKey ->
            val coverUrl = rewriteRemoteAssetUrlForActiveServer(url = item.coverUrl, session = session)
                ?.takeIf(String::isNotBlank)
                ?: error("Unable to resolve Kavita cover URL.")
            api.downloadCover(accessToken = accessToken, coverUrl = coverUrl, apiKey = apiKey)
        }
}
