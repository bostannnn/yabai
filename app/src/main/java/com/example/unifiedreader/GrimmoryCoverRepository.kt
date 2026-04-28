package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.CachedCoverAsset
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
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val coverCacheResolver: RemoteCoverCacheResolver,
) : GrimmoryCoverDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverCacheResolver.cachedCover(
            storage = grimmoryCoverStorage,
            baseFileName = item.grimmoryCacheFileName("cover"),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.grimmoryLegacyCacheFileNames("cover"),
        )?.toCachedRemoteCover()
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val resolvedFile = coverCacheResolver.resolveCoverFile(
            storage = grimmoryCoverStorage,
            baseFileName = item.grimmoryCacheFileName("cover"),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.grimmoryLegacyCacheFileNames("cover"),
        ) { destination ->
            val session = grimmoryCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Grimmory server before loading remote covers.")
            runWithStoredSessionAddressFailover(
                sessionStore = grimmoryCredentialStore,
                session = session,
                createApi = grimmoryApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                downloadWithTokenRefresh(
                    api = api,
                    session = activeSession,
                    item = item,
                    destination = destination,
                )
            }
        }
        return Uri.fromFile(resolvedFile)
    }

    override suspend fun warmCacheIndexes() {
        coverCacheResolver.warmCacheIndexes(grimmoryCoverStorage)
    }

    override suspend fun clearCachedCovers() {
        coverCacheResolver.clearCachedCovers(grimmoryCoverStorage)
    }

    private suspend fun downloadWithTokenRefresh(
        session: GrimmoryStoredSession,
        item: ReadableItem,
        destination: java.io.File,
        api: com.example.unifiedreader.data.grimmory.GrimmoryApi,
    ) {
        sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            download(
                api = api,
                accessToken = accessToken,
                item = item,
                destination = destination,
            )
        }
    }

    private suspend fun download(
        api: com.example.unifiedreader.data.grimmory.GrimmoryApi,
        accessToken: String,
        item: ReadableItem,
        destination: java.io.File,
    ) = writeGrimmoryCacheFile(
        destination = destination,
        targetDescription = "cached Grimmory cover for ${item.title}",
    ) { temporaryFile ->
        val remoteItemId = item.remoteId ?: error("Unable to resolve Grimmory book id.")
        val bytes = api.downloadBookCover(
            accessToken = accessToken,
            bookId = remoteItemId,
            coverUrl = item.coverUrl,
        )
        temporaryFile.writeBytes(bytes)
    }

}

private fun CachedCoverAsset.toCachedRemoteCover(): CachedRemoteCover = CachedRemoteCover(
    uri = Uri.fromFile(file),
    isPersistent = isPersistent,
    matchesRequestedVariant = matchesRequestedVariant,
)

private val grimmoryCoverStorage = RemoteCoverStorageDirectory(
    storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
    legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
)
