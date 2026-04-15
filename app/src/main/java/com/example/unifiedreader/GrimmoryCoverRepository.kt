package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.GRIMMORY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.GRIMMORY_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.thumbnailDirectoryName
import com.example.unifiedreader.feature.library.covers.thumbnailFileName
import java.io.File
import javax.inject.Inject

interface GrimmoryCoverDataSource : RemoteCoverDataSource

class GrimmoryCoverRepository @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val coverStore: PersistentRemoteCoverStore,
    private val thumbnailGenerator: LibraryCoverThumbnailGenerator,
) : GrimmoryCoverDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        val baseFileName = item.grimmoryCacheFileName("cover")
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
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
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            fileName = baseFileName,
            legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
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
        val fileName = item.grimmoryCacheFileName("cover")
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            fileName = fileName,
        )
        val thumbnailVariant = variant.toCoverAssetVariant()
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
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
        coverStore.withStorageLock(GRIMMORY_COVER_DIRECTORY_NAME) {
            coverStore.moveLegacyCoverIfPresent(
                storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
                fileName = fileName,
                legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
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
                    destination = cachedFile,
                )
            }
            coverStore.recordPersistentCoverFile(
                storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
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
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
        )
        LibraryCoverImageVariant.entries
            .map { variant ->
                thumbnailDirectoryName(
                    storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
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
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
            additionalStorageDirectories = LibraryCoverImageVariant.entries
                .map { variant ->
                    thumbnailDirectoryName(
                        storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
                        variant = variant.toCoverAssetVariant(),
                    )
                }
                .distinct(),
        )
    }

    private suspend fun downloadWithTokenRefresh(
        api: GrimmoryApi,
        session: GrimmoryStoredSession,
        item: ReadableItem,
        destination: File,
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
        api: GrimmoryApi,
        accessToken: String,
        item: ReadableItem,
        destination: File,
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
