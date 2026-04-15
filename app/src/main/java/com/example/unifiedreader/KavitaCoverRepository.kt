package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.thumbnailDirectoryName
import com.example.unifiedreader.feature.library.covers.thumbnailFileName
import com.example.unifiedreader.feature.library.covers.writeCoverCacheFile
import javax.inject.Inject

interface KavitaCoverDataSource : RemoteCoverDataSource {
    override val usesRemoteDisplayFallback: Boolean
        get() = false
}

class KavitaCoverRepository @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val coverStore: PersistentRemoteCoverStore,
    private val thumbnailGenerator: LibraryCoverThumbnailGenerator,
) : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        val baseFileName = item.kavitaCacheFileName("cover")
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
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
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
            fileName = baseFileName,
            legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
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
        val fileName = item.kavitaCacheFileName("cover")
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
            fileName = fileName,
        )
        val thumbnailVariant = variant.toCoverAssetVariant()
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
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
        coverStore.withStorageLock(KAVITA_COVER_DIRECTORY_NAME) {
            coverStore.moveLegacyCoverIfPresent(
                storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
                fileName = fileName,
                legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
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
                    val coverUrl = item.coverUrl?.takeIf(String::isNotBlank)
                        ?: error("Unable to resolve Kavita cover URL.")
                    writeCoverCacheFile(
                        destination = cachedFile,
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
            coverStore.recordPersistentCoverFile(
                storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
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
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
            legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
        )
        LibraryCoverImageVariant.entries
            .map { variant ->
                thumbnailDirectoryName(
                    storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
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
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
            legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
            additionalStorageDirectories = LibraryCoverImageVariant.entries
                .map { variant ->
                    thumbnailDirectoryName(
                        storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
                        variant = variant.toCoverAssetVariant(),
                    )
                }
                .distinct(),
        )
    }
}

internal fun ReadableItem.kavitaCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
).joinToString(separator = "-").plus(".$extension")
