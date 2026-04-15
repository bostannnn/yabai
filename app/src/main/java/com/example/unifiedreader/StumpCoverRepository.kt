package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.STUMP_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.STUMP_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.thumbnailDirectoryName
import com.example.unifiedreader.feature.library.covers.thumbnailFileName
import com.example.unifiedreader.feature.library.covers.writeCoverCacheFile
import javax.inject.Inject

interface StumpCoverDataSource : RemoteCoverDataSource

class StumpCoverRepository @Inject constructor(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val coverStore: PersistentRemoteCoverStore,
    private val thumbnailGenerator: LibraryCoverThumbnailGenerator,
) : StumpCoverDataSource {
    override val sourceType: SourceType = SourceType.STUMP
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        val baseFileName = item.stumpCacheFileName("cover")
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            variant = variant.toCoverAssetVariant(),
        )
        val thumbnailName = thumbnailFileName(
            baseFileName = baseFileName,
            variant = variant.toCoverAssetVariant(),
        )
        coverStore.cachedThumbnailFile(thumbnailDirectory, thumbnailName)?.let { thumbnailFile ->
            return CachedRemoteCover(
                uri = Uri.fromFile(thumbnailFile),
                isPersistent = true,
                matchesRequestedVariant = true,
            )
        }
        return coverStore.cachedCoverFile(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            fileName = baseFileName,
            legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
        )?.let { cachedCover ->
            CachedRemoteCover(
                uri = Uri.fromFile(cachedCover.file),
                isPersistent = cachedCover.isPersistent,
                matchesRequestedVariant = false,
            )
        }
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val fileName = item.stumpCacheFileName("cover")
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            fileName = fileName,
        )
        val thumbnailVariant = variant.toCoverAssetVariant()
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            variant = thumbnailVariant,
        )
        val thumbnailName = thumbnailFileName(
            baseFileName = fileName,
            variant = thumbnailVariant,
        )
        val thumbnailFile = coverStore.thumbnailCoverFile(
            thumbnailDirectoryName = thumbnailDirectory,
            fileName = thumbnailName,
        )
        coverStore.cachedThumbnailFile(thumbnailDirectory, thumbnailName)?.let { existingThumbnail ->
            return Uri.fromFile(existingThumbnail)
        }
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            val generatedThumbnail = thumbnailGenerator.ensureThumbnail(
                sourceFile = cachedFile,
                destinationFile = thumbnailFile,
                variant = thumbnailVariant,
            )
            generatedThumbnail?.let {
                coverStore.recordThumbnailFile(
                    thumbnailDirectoryName = thumbnailDirectory,
                    fileName = thumbnailName,
                    file = it,
                )
                return Uri.fromFile(it)
            }
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
                    val generatedThumbnail = thumbnailGenerator.ensureThumbnail(
                        sourceFile = migratedFile,
                        destinationFile = thumbnailFile,
                        variant = thumbnailVariant,
                    )
                    generatedThumbnail?.let {
                        coverStore.recordThumbnailFile(
                            thumbnailDirectoryName = thumbnailDirectory,
                            fileName = thumbnailName,
                            file = it,
                        )
                    }
                    resolvedUri = Uri.fromFile(generatedThumbnail ?: migratedFile)
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
                    writeCoverCacheFile(
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
            coverStore.recordPersistentCoverFile(
                storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
                fileName = fileName,
                file = cachedFile,
            )
            val generatedThumbnail = thumbnailGenerator.ensureThumbnail(
                sourceFile = cachedFile,
                destinationFile = thumbnailFile,
                variant = thumbnailVariant,
            )
            generatedThumbnail?.let {
                coverStore.recordThumbnailFile(
                    thumbnailDirectoryName = thumbnailDirectory,
                    fileName = thumbnailName,
                    file = it,
                )
            }
            resolvedUri = Uri.fromFile(generatedThumbnail ?: cachedFile)
        }
        return resolvedUri ?: Uri.fromFile(cachedFile)
    }

    override suspend fun warmCacheIndexes() {
        coverStore.warmStorageDirectoryIndex(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
        )
        LibraryCoverImageVariant.entries
            .map { variant ->
                thumbnailDirectoryName(
                    storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
                    variant = variant.toCoverAssetVariant(),
                )
            }
            .distinct()
            .forEach { directoryName ->
                coverStore.warmStorageDirectoryIndex(directoryName)
            }
    }

    override suspend fun clearCachedCovers() {
        coverStore.clearStorageDirectory(
            storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
            legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
            additionalStorageDirectories = LibraryCoverImageVariant.entries
                .map { variant ->
                    thumbnailDirectoryName(
                        storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
                        variant = variant.toCoverAssetVariant(),
                    )
                }
                .distinct(),
        )
    }
}

internal fun ReadableItem.stumpCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
).joinToString(separator = "-").plus(".$extension")
