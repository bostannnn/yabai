package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.moveReplacing
import com.example.unifiedreader.core.model.sha256Hex
import java.io.File
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
            val moved = runCatching { temporaryFile.moveReplacing(destination) }.isSuccess
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

private const val MAX_PREVIEW_STORE_BYTES = 256L * 1024L * 1024L
