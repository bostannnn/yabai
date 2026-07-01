package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.CachedCoverAsset
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory
import com.example.unifiedreader.feature.library.covers.writeCoverCacheFile
import javax.inject.Inject

interface KavitaCoverDataSource : RemoteCoverDataSource {
    override val usesRemoteDisplayFallback: Boolean
        get() = false
}

class KavitaCoverRepository @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val coverCacheResolver: RemoteCoverCacheResolver,
) : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverCacheResolver.cachedCover(
            storage = kavitaCoverStorage,
            baseFileName = item.kavitaCacheFileName("cover"),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.kavitaLegacyCacheFileNames(
                extension = "cover",
                session = kavitaCredentialStore.activeSessionFor(item),
            ),
        )?.toCachedRemoteCover()
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val resolvedFile = coverCacheResolver.resolveCoverFile(
            storage = kavitaCoverStorage,
            baseFileName = item.kavitaCacheFileName("cover"),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.kavitaLegacyCacheFileNames(
                extension = "cover",
                session = kavitaCredentialStore.activeSessionFor(item),
            ),
        ) { destination ->
            val session = kavitaCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Kavita server before loading remote covers.")
            runWithStoredSessionAddressFailover(
                sessionStore = kavitaCredentialStore,
                session = session,
                createApi = kavitaApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                sessionRequestRunner.run(
                    api = api,
                    session = activeSession,
                ) { accessToken, apiKey ->
                    val coverUrl = rewriteRemoteAssetUrlForActiveServer(
                        url = item.coverUrl,
                        session = activeSession,
                    )?.takeIf(String::isNotBlank)
                        ?: error("Unable to resolve Kavita cover URL.")
                    writeCoverCacheFile(
                        destination = destination,
                        targetDescription = "cached Kavita cover for ${item.title}",
                    ) { temporaryFile ->
                        temporaryFile.writeBytes(
                            api.downloadCover(
                                accessToken = accessToken,
                                coverUrl = coverUrl,
                                apiKey = apiKey,
                            ),
                        )
                    }
                }
            }
        }
        return Uri.fromFile(resolvedFile)
    }

    override suspend fun warmCacheIndexes() {
        coverCacheResolver.warmCacheIndexes(kavitaCoverStorage)
    }

    override suspend fun clearCachedCovers() {
        coverCacheResolver.clearCachedCovers(kavitaCoverStorage)
    }
}

internal fun ReadableItem.kavitaCacheFileName(
    extension: String,
    coverUrl: String? = this.coverUrl,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
    coverIdentityFileSegment(coverUrl),
).joinToString(separator = "-").plus(".$extension")

internal fun ReadableItem.kavitaLegacyCacheFileNames(
    extension: String,
    session: StoredSourceSession? = null,
): List<String> {
    val alternateAddressFileNames = alternateAddressCoverCacheFileNames(
        extension = extension,
        session = session,
        cacheFileName = ReadableItem::kavitaCacheFileName,
    )
    if (coverIdentityFileSegment() != null) return alternateAddressFileNames
    return alternateAddressFileNames + listOf(
        listOfNotNull(
            id.toSafeFileSegment(),
            remoteFileType?.toSafeFileSegment(),
        ).joinToString(separator = "-").plus(".$extension"),
    )
}

private fun CachedCoverAsset.toCachedRemoteCover(): CachedRemoteCover = CachedRemoteCover(
    uri = Uri.fromFile(file),
    isPersistent = isPersistent,
    matchesRequestedVariant = matchesRequestedVariant,
)

private val kavitaCoverStorage = RemoteCoverStorageDirectory(
    storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
    legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
)
