package com.example.unifiedreader

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.unifiedreader.core.model.FormatType
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

internal interface ComicArchiveCodec {
    val format: FormatType

    fun matchesFileName(fileName: String): Boolean

    suspend fun listEntries(
        context: Context,
        uri: Uri,
    ): List<String>

    suspend fun readEntryBytes(
        context: Context,
        uri: Uri,
        entryName: String,
    ): ByteArray?

    suspend fun listImageEntries(
        context: Context,
        uri: Uri,
    ): List<String> = listEntries(
        context = context,
        uri = uri,
    ).filter(String::isImageEntryName).sortedWith(NaturalPageOrderComparator)

    suspend fun decodePageImage(
        context: Context,
        uri: Uri,
        entryName: String,
    ): ImageBitmap? = readEntryBytes(
        context = context,
        uri = uri,
        entryName = entryName,
    )?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
}

internal object ZipComicArchiveCodec : ComicArchiveCodec {
    override val format: FormatType = FormatType.CBZ

    override fun matchesFileName(fileName: String): Boolean = fileName.endsWith(".cbz", ignoreCase = true)

    override suspend fun listEntries(
        context: Context,
        uri: Uri,
    ): List<String> = runCatching {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zipInputStream ->
                    buildList {
                        while (true) {
                            val entry = zipInputStream.nextEntry ?: break
                            if (!entry.isDirectory) {
                                add(entry.name)
                            }
                        }
                    }
                }
            }.orEmpty()
        }
    }.getOrDefault(emptyList())

    override suspend fun readEntryBytes(
        context: Context,
        uri: Uri,
        entryName: String,
    ): ByteArray? = runCatching {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zipInputStream ->
                    while (true) {
                        val entry = zipInputStream.nextEntry ?: break
                        if (!entry.isDirectory && entry.name == entryName) {
                            return@withContext ByteArrayOutputStream().use { output ->
                                zipInputStream.copyTo(output)
                                output.toByteArray()
                            }
                        }
                    }
                    null
                }
            }
        }
    }.getOrNull()
}

internal class RarComicArchiveCodec(
    private val context: Context,
) : ComicArchiveCodec {
    override val format: FormatType = FormatType.CBR

    override fun matchesFileName(fileName: String): Boolean = fileName.endsWith(".cbr", ignoreCase = true)

    override suspend fun listEntries(
        context: Context,
        uri: Uri,
    ): List<String> = runCatching {
        withContext(Dispatchers.IO) {
            readArchiveHeaders(uri)
                .map(::archiveEntryName)
        }
    }.getOrDefault(emptyList())

    override suspend fun readEntryBytes(
        context: Context,
        uri: Uri,
        entryName: String,
    ): ByteArray? = runCatching {
        withContext(Dispatchers.IO) {
            val archiveFile = ensureLocalArchiveFile(uri)
            val archive = Archive(archiveFile)
            try {
                val matchingHeader = archive.fileHeaders.firstOrNull { header ->
                    !header.isDirectory && archiveEntryName(header) == entryName
                } ?: return@withContext null

                ByteArrayOutputStream().use { output ->
                    archive.extractFile(matchingHeader, output)
                    output.toByteArray()
                }
            } finally {
                archive.close()
            }
        }
    }.getOrNull()

    private suspend fun readArchiveHeaders(
        uri: Uri,
    ): List<FileHeader> = withContext(Dispatchers.IO) {
        val archiveFile = ensureLocalArchiveFile(uri)
        val archive = Archive(archiveFile)
        try {
            archive.fileHeaders.filterNot { it.isDirectory }
        } finally {
            archive.close()
        }
    }

    private suspend fun ensureLocalArchiveFile(
        uri: Uri,
    ): File = withContext(Dispatchers.IO) {
        val cacheDirectory = File(context.cacheDir, RAR_ARCHIVE_CACHE_DIRECTORY).apply {
            mkdirs()
        }
        val suffix = if (matchesFileName(uri.toString())) "cbr" else "archive"
        val localFile = File(
            cacheDirectory,
            "${uri.toString().hashCode().toUInt().toString(16)}.$suffix",
        )
        val signatureFile = File(cacheDirectory, "${localFile.name}.sig")
        val currentSignature = context.contentResolver.queryArchiveSignature(uri)
        val cachedSignature = signatureFile.takeIf(File::exists)?.readText()
        val shouldRefresh = shouldRefreshArchiveCache(
            localFileExists = localFile.exists(),
            localFileLength = localFile.length(),
            currentSignature = currentSignature,
            cachedSignature = cachedSignature,
            localFileLastModified = localFile.lastModified(),
            nowEpochMillis = System.currentTimeMillis(),
        )
        if (shouldRefresh) {
            val refreshed = context.contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (refreshed) {
                signatureFile.writeText(currentSignature)
            }
        }
        localFile
    }

    private fun archiveEntryName(
        header: FileHeader,
    ): String = header.fileName?.takeIf { it.isNotBlank() }
        ?: header.fileNameString

    companion object {
        fun clearLocalArchiveCache(
            context: Context,
        ) {
            File(context.cacheDir, RAR_ARCHIVE_CACHE_DIRECTORY).deleteRecursively()
        }
    }
}

private fun android.content.ContentResolver.queryArchiveSignature(
    uri: Uri,
): String {
    val projection = arrayOf(
        OpenableColumns.SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
    return runCatching {
        query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use UNKNOWN_ARCHIVE_SIGNATURE
            val size = cursor.getLongOrNull(OpenableColumns.SIZE) ?: -1L
            val lastModified = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: -1L
            if (size < 0L && lastModified < 0L) {
                return@use UNKNOWN_ARCHIVE_SIGNATURE
            }
            "$size:$lastModified"
        } ?: UNKNOWN_ARCHIVE_SIGNATURE
    }.getOrDefault(UNKNOWN_ARCHIVE_SIGNATURE)
}

private fun Cursor.getLongOrNull(
    columnName: String,
): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

private fun shouldRefreshArchiveCache(
    localFileExists: Boolean,
    localFileLength: Long,
    currentSignature: String,
    cachedSignature: String?,
    localFileLastModified: Long,
    nowEpochMillis: Long,
): Boolean {
    val unknownSignatureExpired = currentSignature == UNKNOWN_ARCHIVE_SIGNATURE &&
        (!localFileExists || nowEpochMillis - localFileLastModified >= UNKNOWN_SIGNATURE_CACHE_TTL_MS)
    return !localFileExists ||
        localFileLength == 0L ||
        unknownSignatureExpired ||
        currentSignature != cachedSignature
}

private const val UNKNOWN_ARCHIVE_SIGNATURE = "unknown"
private const val UNKNOWN_SIGNATURE_CACHE_TTL_MS = 60_000L
private const val RAR_ARCHIVE_CACHE_DIRECTORY = "comic-archives"

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object ComicArchiveCodecsTestAccess {
    fun shouldRefreshArchiveCache(
        localFileExists: Boolean,
        localFileLength: Long,
        currentSignature: String,
        cachedSignature: String?,
        localFileLastModified: Long,
        nowEpochMillis: Long,
    ): Boolean = com.example.unifiedreader.shouldRefreshArchiveCache(
        localFileExists = localFileExists,
        localFileLength = localFileLength,
        currentSignature = currentSignature,
        cachedSignature = cachedSignature,
        localFileLastModified = localFileLastModified,
        nowEpochMillis = nowEpochMillis,
    )
}
