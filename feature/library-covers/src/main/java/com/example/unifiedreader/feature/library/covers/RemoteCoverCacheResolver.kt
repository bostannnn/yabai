package com.example.unifiedreader.feature.library.covers

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CachedCoverAsset(
    val file: File,
    val isPersistent: Boolean,
    val matchesRequestedVariant: Boolean,
)

@Singleton
class RemoteCoverCacheResolver @Inject constructor(
    private val coverStore: PersistentRemoteCoverStore,
    private val thumbnailGenerator: LibraryCoverThumbnailGenerator,
) {
    fun cachedCover(
        storage: RemoteCoverStorageDirectory,
        baseFileName: String,
        variant: LibraryCoverVariant,
        legacyBaseFileNames: List<String> = emptyList(),
    ): CachedCoverAsset? {
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = storage.storageDirectoryName,
            variant = variant,
        )
        val thumbnailName = thumbnailFileName(
            baseFileName = baseFileName,
            variant = variant,
        )
        coverStore.cachedThumbnailFile(thumbnailDirectory, thumbnailName)?.let { thumbnailFile ->
            return CachedCoverAsset(
                file = thumbnailFile,
                isPersistent = true,
                matchesRequestedVariant = true,
            )
        }
        return coverStore.cachedCoverFile(
            storageDirectoryName = storage.storageDirectoryName,
            fileName = baseFileName,
            legacyDirectoryName = storage.legacyDirectoryName,
        )?.let { cachedCover ->
            CachedCoverAsset(
                file = cachedCover.file,
                isPersistent = cachedCover.isPersistent,
                matchesRequestedVariant = false,
            )
        } ?: legacyBaseFileNames
            .asSequence()
            .filterNot { legacyBaseFileName -> legacyBaseFileName == baseFileName }
            .mapNotNull { legacyBaseFileName ->
                coverStore.cachedCoverFile(
                    storageDirectoryName = storage.storageDirectoryName,
                    fileName = legacyBaseFileName,
                    legacyDirectoryName = storage.legacyDirectoryName,
                )
            }
            .firstOrNull()
            ?.let { cachedCover ->
                CachedCoverAsset(
                    file = cachedCover.file,
                    isPersistent = cachedCover.isPersistent,
                    matchesRequestedVariant = false,
                )
        }
    }

    suspend fun resolveCoverFile(
        storage: RemoteCoverStorageDirectory,
        baseFileName: String,
        variant: LibraryCoverVariant,
        legacyBaseFileNames: List<String> = emptyList(),
        downloadOriginal: suspend (File) -> Unit,
    ): File {
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = storage.storageDirectoryName,
            fileName = baseFileName,
        )
        val thumbnailDirectory = thumbnailDirectoryName(
            storageDirectoryName = storage.storageDirectoryName,
            variant = variant,
        )
        val thumbnailName = thumbnailFileName(
            baseFileName = baseFileName,
            variant = variant,
        )
        val thumbnailFile = coverStore.thumbnailCoverFile(
            thumbnailDirectoryName = thumbnailDirectory,
            fileName = thumbnailName,
        )
        coverStore.cachedThumbnailFile(thumbnailDirectory, thumbnailName)?.let { existingThumbnail ->
            return existingThumbnail
        }
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return ensureThumbnailOrOriginal(
                sourceFile = cachedFile,
                thumbnailDirectory = thumbnailDirectory,
                thumbnailName = thumbnailName,
                destinationThumbnailFile = thumbnailFile,
                variant = variant,
            )
        }
        findLegacyCachedCover(
            storage = storage,
            baseFileName = baseFileName,
            legacyBaseFileNames = legacyBaseFileNames,
        )?.let { legacyCachedCover ->
            return ensureThumbnailOrOriginal(
                sourceFile = legacyCachedCover.file,
                thumbnailDirectory = thumbnailDirectory,
                thumbnailName = thumbnailName,
                destinationThumbnailFile = thumbnailFile,
                variant = variant,
            )
        }
        var resolvedFile: File? = null
        coverStore.withStorageLock(storage.storageDirectoryName) {
            val migratedFile = storage.legacyDirectoryName?.let { legacyDirectoryName ->
                coverStore.moveLegacyCoverIfPresent(
                    storageDirectoryName = storage.storageDirectoryName,
                    fileName = baseFileName,
                    legacyDirectoryName = legacyDirectoryName,
                )
            }
            if (migratedFile?.isFile == true && migratedFile.length() > 0L) {
                resolvedFile = ensureThumbnailOrOriginal(
                    sourceFile = migratedFile,
                    thumbnailDirectory = thumbnailDirectory,
                    thumbnailName = thumbnailName,
                    destinationThumbnailFile = thumbnailFile,
                    variant = variant,
                )
                return@withStorageLock
            }
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                resolvedFile = ensureThumbnailOrOriginal(
                    sourceFile = cachedFile,
                    thumbnailDirectory = thumbnailDirectory,
                    thumbnailName = thumbnailName,
                    destinationThumbnailFile = thumbnailFile,
                    variant = variant,
                )
                return@withStorageLock
            }
            findLegacyCachedCover(
                storage = storage,
                baseFileName = baseFileName,
                legacyBaseFileNames = legacyBaseFileNames,
            )?.let { legacyCachedCover ->
                resolvedFile = ensureThumbnailOrOriginal(
                    sourceFile = legacyCachedCover.file,
                    thumbnailDirectory = thumbnailDirectory,
                    thumbnailName = thumbnailName,
                    destinationThumbnailFile = thumbnailFile,
                    variant = variant,
                )
                return@withStorageLock
            }
            downloadOriginal(cachedFile)
            coverStore.recordPersistentCoverFile(
                storageDirectoryName = storage.storageDirectoryName,
                fileName = baseFileName,
                file = cachedFile,
            )
            resolvedFile = ensureThumbnailOrOriginal(
                sourceFile = cachedFile,
                thumbnailDirectory = thumbnailDirectory,
                thumbnailName = thumbnailName,
                destinationThumbnailFile = thumbnailFile,
                variant = variant,
            )
        }
        return resolvedFile ?: cachedFile
    }

    suspend fun warmCacheIndexes(
        storage: RemoteCoverStorageDirectory,
        variants: Iterable<LibraryCoverVariant> = LibraryCoverVariant.entries,
    ) {
        coverStore.warmStorageDirectoryIndex(
            storageDirectoryName = storage.storageDirectoryName,
            legacyDirectoryName = storage.legacyDirectoryName,
        )
        variants
            .map { variant ->
                thumbnailDirectoryName(
                    storageDirectoryName = storage.storageDirectoryName,
                    variant = variant,
                )
            }
            .distinct()
            .forEach { directoryName ->
                coverStore.warmStorageDirectoryIndex(directoryName)
            }
    }

    suspend fun clearCachedCovers(
        storage: RemoteCoverStorageDirectory,
        variants: Iterable<LibraryCoverVariant> = LibraryCoverVariant.entries,
    ) {
        coverStore.clearStorageDirectory(
            storageDirectoryName = storage.storageDirectoryName,
            legacyDirectoryName = storage.legacyDirectoryName,
            additionalStorageDirectories = variants
                .map { variant ->
                    thumbnailDirectoryName(
                        storageDirectoryName = storage.storageDirectoryName,
                        variant = variant,
                    )
                }
                .distinct(),
        )
    }

    private suspend fun ensureThumbnailOrOriginal(
        sourceFile: File,
        thumbnailDirectory: String,
        thumbnailName: String,
        destinationThumbnailFile: File,
        variant: LibraryCoverVariant,
    ): File {
        val generatedThumbnail = thumbnailGenerator.ensureThumbnail(
            sourceFile = sourceFile,
            destinationFile = destinationThumbnailFile,
            variant = variant,
        )
        generatedThumbnail?.let {
            coverStore.recordThumbnailFile(
                thumbnailDirectoryName = thumbnailDirectory,
                fileName = thumbnailName,
                file = it,
            )
        }
        return generatedThumbnail ?: sourceFile
    }

    private fun findLegacyCachedCover(
        storage: RemoteCoverStorageDirectory,
        baseFileName: String,
        legacyBaseFileNames: List<String>,
    ): CachedRemoteCoverFile? = legacyBaseFileNames
        .asSequence()
        .filterNot { legacyBaseFileName -> legacyBaseFileName == baseFileName }
        .mapNotNull { legacyBaseFileName ->
            coverStore.cachedCoverFile(
                storageDirectoryName = storage.storageDirectoryName,
                fileName = legacyBaseFileName,
                legacyDirectoryName = storage.legacyDirectoryName,
            )
        }
        .firstOrNull()
}
