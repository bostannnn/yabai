package com.example.unifiedreader

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

    fun cachedCoverFile(
        storageDirectoryName: String,
        fileName: String,
        legacyDirectoryName: String? = null,
    ): CachedRemoteCoverFile? {
        val persistentFile = File(existingStorageDirectory(storageDirectoryName), fileName)
        if (persistentFile.isFile && persistentFile.length() > 0L) {
            return CachedRemoteCoverFile(
                file = persistentFile,
                isPersistent = true,
            )
        }
        val legacyFile = legacyDirectoryName
            ?.let { directoryName -> File(legacyStorageDirectory(directoryName), fileName) }
            ?.takeIf { file -> file.isFile && file.length() > 0L }
        return legacyFile?.let { file ->
            CachedRemoteCoverFile(
                file = file,
                isPersistent = false,
            )
        }
    }

    fun persistentCoverFile(
        storageDirectoryName: String,
        fileName: String,
    ): File = File(storageDirectory(storageDirectoryName), fileName)

    suspend fun <T> withStorageLock(
        storageDirectoryName: String,
        block: suspend () -> T,
    ): T = storageMutex(storageDirectoryName).withLock {
        block()
    }

    suspend fun moveLegacyCoverIfPresent(
        storageDirectoryName: String,
        fileName: String,
        legacyDirectoryName: String,
    ): File? = withContext(Dispatchers.IO) {
        val destination = persistentCoverFile(storageDirectoryName, fileName)
        if (destination.isFile && destination.length() > 0L) {
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
            destination
        } else {
            legacyFile.takeIf { file -> file.isFile && file.length() > 0L }
        }
    }

    suspend fun clearStorageDirectory(
        storageDirectoryName: String,
        legacyDirectoryName: String? = null,
    ) {
        withStorageLock(storageDirectoryName) {
            withContext(Dispatchers.IO) {
                storageDirectory(storageDirectoryName).deleteRecursively()
                legacyDirectoryName?.let(::legacyStorageDirectory)?.deleteRecursively()
            }
        }
    }

    private fun storageMutex(
        storageDirectoryName: String,
    ): Mutex = storageMutexes.computeIfAbsent(storageDirectoryName) { Mutex() }

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
