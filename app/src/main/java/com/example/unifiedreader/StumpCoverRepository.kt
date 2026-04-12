package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import javax.inject.Inject

interface StumpCoverDataSource : RemoteCoverDataSource

class StumpCoverRepository @Inject constructor(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val coverStore: PersistentRemoteCoverStore,
) : StumpCoverDataSource {
    override val sourceType: SourceType = SourceType.STUMP
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverStore.cachedCoverFile(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            fileName = item.stumpCacheFileName("cover"),
            legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
        )?.let { cachedCover ->
            CachedRemoteCover(
                uri = Uri.fromFile(cachedCover.file),
                isPersistent = cachedCover.isPersistent,
            )
        }
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val fileName = item.stumpCacheFileName("cover")
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            fileName = fileName,
        )
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return Uri.fromFile(cachedFile)
        }
        var resolvedUri: Uri? = null
        coverStore.withStorageLock(STUMP_COVER_DIRECTORY_NAME) {
            coverStore.moveLegacyCoverIfPresent(
                storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
                fileName = fileName,
                legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
            )?.let { migratedFile ->
                if (migratedFile.isFile && migratedFile.length() > 0L) {
                    resolvedUri = Uri.fromFile(migratedFile)
                    return@withStorageLock
                }
            }
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                return@withStorageLock
            }
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
                    writeCachedFile(
                        destination = cachedFile,
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
            resolvedUri = Uri.fromFile(cachedFile)
        }
        return resolvedUri ?: Uri.fromFile(cachedFile)
    }

    override suspend fun clearCachedCovers() {
        coverStore.clearStorageDirectory(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
        )
    }
}

internal fun ReadableItem.stumpCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
).joinToString(separator = "-").plus(".$extension")

private const val STUMP_COVER_DIRECTORY_NAME = "stump-covers"
private const val STUMP_LEGACY_COVER_DIRECTORY_NAME = "stump-covers"
