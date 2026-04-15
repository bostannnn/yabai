package com.example.unifiedreader.feature.library.covers

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CachedRemoteCoverFile(
    val file: File,
    val isPersistent: Boolean,
)

@Singleton
class PersistentRemoteCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val storageMutexes = ConcurrentHashMap<String, Mutex>()
    private val indexedPersistentFilesByDirectory =
        ConcurrentHashMap<String, ConcurrentHashMap<String, File>>()
    private val indexedLegacyFilesByDirectory =
        ConcurrentHashMap<String, ConcurrentHashMap<String, File>>()
    private val warmedStorageDirectories = ConcurrentHashMap.newKeySet<String>()
    private val warmedLegacyDirectories = ConcurrentHashMap.newKeySet<String>()

    fun cachedCoverFile(
        storageDirectoryName: String,
        fileName: String,
        legacyDirectoryName: String? = null,
    ): CachedRemoteCoverFile? {
        indexedPersistentFilesByDirectory[storageDirectoryName]
            ?.get(fileName)
            ?.takeIf { file -> file.isFile && file.length() > 0L }
            ?.let { persistentFile ->
                return CachedRemoteCoverFile(
                    file = persistentFile,
                    isPersistent = true,
                )
            }
        if (storageDirectoryName !in warmedStorageDirectories) {
            val persistentFile = File(existingStorageDirectory(storageDirectoryName), fileName)
            if (persistentFile.isFile && persistentFile.length() > 0L) {
                return CachedRemoteCoverFile(
                    file = persistentFile,
                    isPersistent = true,
                )
            }
        }
        val legacyFile = legacyDirectoryName
            ?.let { directoryName ->
                indexedLegacyFilesByDirectory[directoryName]
                    ?.get(fileName)
                    ?.takeIf { file -> file.isFile && file.length() > 0L }
                    ?: if (directoryName !in warmedLegacyDirectories) {
                        File(legacyStorageDirectory(directoryName), fileName)
                            .takeIf { file -> file.isFile && file.length() > 0L }
                    } else {
                        null
                    }
            }
        return legacyFile?.let { file ->
            CachedRemoteCoverFile(
                file = file,
                isPersistent = false,
            )
        }
    }

    fun cachedThumbnailFile(
        thumbnailDirectoryName: String,
        fileName: String,
    ): File? = indexedPersistentFilesByDirectory[thumbnailDirectoryName]
        ?.get(fileName)
        ?.takeIf { file -> file.isFile && file.length() > 0L }
        ?: if (thumbnailDirectoryName !in warmedStorageDirectories) {
            File(existingStorageDirectory(thumbnailDirectoryName), fileName)
                .takeIf { file -> file.isFile && file.length() > 0L }
        } else {
            null
        }

    fun persistentCoverFile(
        storageDirectoryName: String,
        fileName: String,
    ): File = File(storageDirectory(storageDirectoryName), fileName)

    fun thumbnailCoverFile(
        thumbnailDirectoryName: String,
        fileName: String,
    ): File = File(storageDirectory(thumbnailDirectoryName), fileName)

    suspend fun <T> withStorageLock(
        storageDirectoryName: String,
        block: suspend () -> T,
    ): T = storageMutex(storageDirectoryName).withLock {
        block()
    }

    suspend fun warmStorageDirectoryIndex(
        storageDirectoryName: String,
        legacyDirectoryName: String? = null,
    ) {
        withStorageLock(storageDirectoryName) {
            withContext(Dispatchers.IO) {
                indexedPersistentFilesByDirectory[storageDirectoryName] =
                    indexFiles(existingStorageDirectory(storageDirectoryName))
                warmedStorageDirectories += storageDirectoryName
                legacyDirectoryName?.let { directoryName ->
                    indexedLegacyFilesByDirectory[directoryName] =
                        indexFiles(legacyStorageDirectory(directoryName))
                    warmedLegacyDirectories += directoryName
                }
            }
        }
    }

    suspend fun moveLegacyCoverIfPresent(
        storageDirectoryName: String,
        fileName: String,
        legacyDirectoryName: String,
    ): File? = withContext(Dispatchers.IO) {
        val destination = persistentCoverFile(storageDirectoryName, fileName)
        if (destination.isFile && destination.length() > 0L) {
            recordPersistentCoverFile(
                storageDirectoryName = storageDirectoryName,
                fileName = fileName,
                file = destination,
            )
            return@withContext destination
        }
        val legacyFile = File(legacyStorageDirectory(legacyDirectoryName), fileName)
        if (!legacyFile.isFile || legacyFile.length() <= 0L) {
            return@withContext null
        }
        destination.parentFile?.mkdirs()
        val moved = legacyFile.renameTo(destination) || runCatching {
            legacyFile.copyTo(destination, overwrite = true)
            legacyFile.delete()
            true
        }.getOrDefault(false)
        if (moved && destination.isFile && destination.length() > 0L) {
            recordPersistentCoverFile(
                storageDirectoryName = storageDirectoryName,
                fileName = fileName,
                file = destination,
            )
            indexedLegacyFilesByDirectory[legacyDirectoryName]?.remove(fileName)
            destination
        } else {
            legacyFile.takeIf { file -> file.isFile && file.length() > 0L }?.also { existingLegacyFile ->
                indexedLegacyFilesByDirectory
                    .getOrPut(legacyDirectoryName) { ConcurrentHashMap() }[fileName] = existingLegacyFile
            }
        }
    }

    fun recordPersistentCoverFile(
        storageDirectoryName: String,
        fileName: String,
        file: File,
    ) {
        if (!file.isFile || file.length() <= 0L) return
        indexedPersistentFilesByDirectory
            .getOrPut(storageDirectoryName) { ConcurrentHashMap() }[fileName] = file
    }

    fun recordThumbnailFile(
        thumbnailDirectoryName: String,
        fileName: String,
        file: File,
    ) = recordPersistentCoverFile(
        storageDirectoryName = thumbnailDirectoryName,
        fileName = fileName,
        file = file,
    )

    suspend fun clearStorageDirectory(
        storageDirectoryName: String,
        legacyDirectoryName: String? = null,
        additionalStorageDirectories: List<String> = emptyList(),
    ) {
        withStorageLock(storageDirectoryName) {
            withContext(Dispatchers.IO) {
                storageDirectory(storageDirectoryName).deleteRecursively()
                legacyDirectoryName?.let(::legacyStorageDirectory)?.deleteRecursively()
                additionalStorageDirectories.forEach { directoryName ->
                    storageDirectory(directoryName).deleteRecursively()
                    indexedPersistentFilesByDirectory[directoryName] = ConcurrentHashMap()
                    warmedStorageDirectories += directoryName
                }
                indexedPersistentFilesByDirectory[storageDirectoryName] = ConcurrentHashMap()
                warmedStorageDirectories += storageDirectoryName
                legacyDirectoryName?.let { directoryName ->
                    indexedLegacyFilesByDirectory[directoryName] = ConcurrentHashMap()
                    warmedLegacyDirectories += directoryName
                }
            }
        }
    }

    private fun storageMutex(
        storageDirectoryName: String,
    ): Mutex = storageMutexes.computeIfAbsent(storageDirectoryName) { Mutex() }

    private fun indexFiles(
        directory: File,
    ): ConcurrentHashMap<String, File> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { file -> file.isFile && file.length() > 0L }
        .associateByTo(ConcurrentHashMap(), File::getName)

    private fun storageDirectory(
        storageDirectoryName: String,
    ): File = File(
        context.applicationContext.filesDir,
        storageDirectoryName,
    ).apply {
        mkdirs()
    }

    private fun existingStorageDirectory(
        storageDirectoryName: String,
    ): File = File(
        context.applicationContext.filesDir,
        storageDirectoryName,
    )

    private fun legacyStorageDirectory(
        storageDirectoryName: String,
    ): File = File(
        context.applicationContext.cacheDir,
        storageDirectoryName,
    )
}
