package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.CachedCoverAsset
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory
import com.example.unifiedreader.feature.library.covers.STUMP_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.STUMP_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.writeCoverCacheFile
import javax.inject.Inject

interface StumpCoverDataSource : RemoteCoverDataSource

class StumpCoverRepository @Inject constructor(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val coverCacheResolver: RemoteCoverCacheResolver,
) : StumpCoverDataSource {
    override val sourceType: SourceType = SourceType.STUMP
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverCacheResolver.cachedCover(
            storage = stumpCoverStorage,
            baseFileName = item.stumpCacheFileName("cover"),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.stumpLegacyCacheFileNames("cover"),
        )?.toCachedRemoteCover()
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val resolvedFile = coverCacheResolver.resolveCoverFile(
            storage = stumpCoverStorage,
            baseFileName = item.stumpCacheFileName("cover"),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.stumpLegacyCacheFileNames("cover"),
        ) { destination ->
            val session = stumpCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Stump server before loading remote covers.")
            runWithStoredSessionAddressFailover(
                sessionStore = stumpCredentialStore,
                session = session,
                createApi = stumpApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                sessionRequestRunner.run(
                    api = api,
                    session = activeSession,
                ) { accessToken ->
                    writeCoverCacheFile(
                        destination = destination,
                        targetDescription = "cached Stump cover for ${item.title}",
                    ) { temporaryFile ->
                        val coverBytes = when {
                            item.isStumpSeriesContainer() -> {
                                val seriesId = item.remoteFileId
                                    ?: error("Unable to resolve Stump series id.")
                                api.downloadSeriesThumbnail(
                                    accessToken = accessToken,
                                    seriesId = seriesId,
                                )
                            }
                            !item.coverUrl.isNullOrBlank() -> {
                                runCatching {
                                    api.downloadUrl(
                                        accessToken = accessToken,
                                        url = requireNotNull(item.coverUrl),
                                    )
                                }.getOrElse {
                                    val mediaId = item.remoteId
                                        ?: error("Unable to resolve Stump media id.")
                                    api.downloadThumbnail(
                                        accessToken = accessToken,
                                        mediaId = mediaId,
                                    )
                                }
                            }
                            else -> {
                                val mediaId = item.remoteId
                                    ?: error("Unable to resolve Stump media id.")
                                api.downloadThumbnail(
                                    accessToken = accessToken,
                                    mediaId = mediaId,
                                )
                            }
                        }
                        temporaryFile.writeBytes(coverBytes)
                    }
                }
            }
        }
        return Uri.fromFile(resolvedFile)
    }

    override suspend fun warmCacheIndexes() {
        coverCacheResolver.warmCacheIndexes(stumpCoverStorage)
    }

    override suspend fun clearCachedCovers() {
        coverCacheResolver.clearCachedCovers(stumpCoverStorage)
    }
}

internal fun ReadableItem.stumpCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
    coverIdentityFileSegment(),
).joinToString(separator = "-").plus(".$extension")

internal fun ReadableItem.stumpLegacyCacheFileNames(
    extension: String,
): List<String> {
    if (coverIdentityFileSegment() != null) return emptyList()
    return listOf(
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

private val stumpCoverStorage = RemoteCoverStorageDirectory(
    storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
    legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
)
