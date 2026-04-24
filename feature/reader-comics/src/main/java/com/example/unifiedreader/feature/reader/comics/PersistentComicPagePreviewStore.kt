package com.example.unifiedreader.feature.reader.comics

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PersistentComicPagePreviewStore(
    private val rootDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun readBytes(
        key: String,
    ): ByteArray? = withContext(ioDispatcher) {
        val file = previewFile(key)
        file.takeIf { it.isFile && it.length() > 0L }
            ?.also { it.setLastModified(System.currentTimeMillis()) }
            ?.readBytes()
    }

    suspend fun writeBytes(
        key: String,
        bytes: ByteArray,
    ) = withContext(ioDispatcher) {
        if (bytes.isEmpty()) return@withContext
        rootDirectory.mkdirs()
        val destination = previewFile(key)
        val temporaryFile = File(rootDirectory, "${destination.name}.tmp")
        temporaryFile.delete()
        try {
            temporaryFile.writeBytes(bytes)
            if (temporaryFile.length() <= 0L) return@withContext
            if (destination.exists() && !destination.delete()) return@withContext
            val moved = temporaryFile.renameTo(destination) || runCatching {
                temporaryFile.copyTo(destination, overwrite = true)
                temporaryFile.delete()
                true
            }.getOrDefault(false)
            if (moved && destination.isFile && destination.length() > 0L) {
                trimToMaxBytes()
            }
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun clear() = withContext(ioDispatcher) {
        rootDirectory.deleteRecursively()
        rootDirectory.mkdirs()
    }

    private fun previewFile(
        key: String,
    ): File = File(rootDirectory, "${key.sha256Hex()}.bin")

    private fun trimToMaxBytes() {
        val files = rootDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .sortedBy(File::lastModified)
        var totalBytes = files.sumOf(File::length)
        for (file in files) {
            if (totalBytes <= MAX_PREVIEW_STORE_BYTES) break
            val length = file.length()
            if (file.delete()) {
                totalBytes -= length
            }
        }
    }
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val MAX_PREVIEW_STORE_BYTES = 256L * 1024L * 1024L
