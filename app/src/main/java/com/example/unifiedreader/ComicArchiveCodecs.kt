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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipFile
import kotlin.concurrent.withLock

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
            val archiveFile = context.ensureReadableArchiveFile(
                uri = uri,
                cacheDirectoryName = ZIP_ARCHIVE_CACHE_DIRECTORY,
                suffix = if (matchesFileName(uri.toString())) "cbz" else "archive",
            )
            ZipFile(archiveFile).use { zipFile ->
                buildList {
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory) {
                            add(entry.name)
                        }
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    override suspend fun readEntryBytes(
        context: Context,
        uri: Uri,
        entryName: String,
    ): ByteArray? = runCatching {
        withContext(Dispatchers.IO) {
            val archiveFile = context.ensureReadableArchiveFile(
                uri = uri,
                cacheDirectoryName = ZIP_ARCHIVE_CACHE_DIRECTORY,
                suffix = if (matchesFileName(uri.toString())) "cbz" else "archive",
            )
            ZipFile(archiveFile).use { zipFile ->
                val entry = zipFile.getEntry(entryName)
                    ?.takeUnless { it.isDirectory }
                    ?: return@withContext null
                zipFile.getInputStream(entry).use { input ->
                    input.readBytes()
                }
            }
        }
    }.getOrNull()

    fun clearLocalArchiveCache(
        context: Context,
    ) {
        context.clearArchiveCache(ZIP_ARCHIVE_CACHE_DIRECTORY)
    }
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
    ): File = context.ensureReadableArchiveFile(
        uri = uri,
        cacheDirectoryName = RAR_ARCHIVE_CACHE_DIRECTORY,
        suffix = if (matchesFileName(uri.toString())) "cbr" else "archive",
    )

    private fun archiveEntryName(
        header: FileHeader,
    ): String = header.fileName?.takeIf { it.isNotBlank() }
        ?: header.fileNameString

    companion object {
        fun clearLocalArchiveCache(
            context: Context,
        ) {
            context.clearArchiveCache(RAR_ARCHIVE_CACHE_DIRECTORY)
        }
    }
}

private val archiveCacheLocks = ConcurrentHashMap<String, ReentrantLock>()

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

private suspend fun Context.ensureReadableArchiveFile(
    uri: Uri,
    cacheDirectoryName: String,
    suffix: String,
): File = withContext(Dispatchers.IO) {
    uri.directFileOrNull()?.let { file -> return@withContext file }
    val cacheDirectory = File(cacheDir, cacheDirectoryName).apply {
        mkdirs()
    }
    val localFile = File(
        cacheDirectory,
        "${uri.archiveCacheKey()}.$suffix",
    )
    val signatureFile = File(cacheDirectory, "${localFile.name}.sig")
    val currentSignature = contentResolver.queryArchiveSignature(uri)
    if (!shouldRefreshArchiveCache(
            localFile = localFile,
            signatureFile = signatureFile,
            currentSignature = currentSignature,
            nowEpochMillis = System.currentTimeMillis(),
        )
    ) {
        return@withContext localFile
    }

    archiveCacheLockFor(cacheDirectory).withLock {
        val lockedCurrentSignature = contentResolver.queryArchiveSignature(uri)
        if (!shouldRefreshArchiveCache(
                localFile = localFile,
                signatureFile = signatureFile,
                currentSignature = lockedCurrentSignature,
                nowEpochMillis = System.currentTimeMillis(),
            )
        ) {
            return@withLock localFile
        }

        refreshArchiveCacheFile(
            uri = uri,
            localFile = localFile,
            signatureFile = signatureFile,
            currentSignature = lockedCurrentSignature,
        )
        localFile
    }
}

private fun Context.clearArchiveCache(
    cacheDirectoryName: String,
) {
    val cacheDirectory = File(cacheDir, cacheDirectoryName)
    archiveCacheLockFor(cacheDirectory).withLock {
        cacheDirectory.deleteRecursively()
    }
}

private fun archiveCacheLockFor(
    cacheDirectory: File,
): ReentrantLock = archiveCacheLocks.computeIfAbsent(cacheDirectory.absolutePath) {
    ReentrantLock()
}

private fun shouldRefreshArchiveCache(
    localFile: File,
    signatureFile: File,
    currentSignature: String,
    nowEpochMillis: Long,
): Boolean {
    val cachedSignature = signatureFile.takeIf(File::exists)
        ?.let { file -> runCatching { file.readText() }.getOrNull() }
    return shouldRefreshArchiveCache(
        localFileExists = localFile.exists(),
        localFileLength = localFile.length(),
        currentSignature = currentSignature,
        cachedSignature = cachedSignature,
        localFileLastModified = localFile.lastModified(),
        nowEpochMillis = nowEpochMillis,
    )
}

private fun Context.refreshArchiveCacheFile(
    uri: Uri,
    localFile: File,
    signatureFile: File,
    currentSignature: String,
) {
    val cacheDirectory = localFile.parentFile ?: return
    var tempFile: File? = null
    try {
        val refreshedFile = File.createTempFile("${localFile.name}.", ".tmp", cacheDirectory)
        tempFile = refreshedFile
        val refreshed = contentResolver.openInputStream(uri)?.use { input ->
            refreshedFile.outputStream().use { output ->
                input.copyTo(output)
            }
            refreshedFile.length() > 0L
        } ?: false
        if (!refreshed) {
            return
        }
        refreshedFile.moveReplacing(localFile)
        runCatching {
            signatureFile.writeTextAtomically(currentSignature)
        }
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        if (localFile.hasReadableContent()) return
        throw exception
    } finally {
        tempFile?.delete()
    }
}

private fun Uri.directFileOrNull(): File? {
    if (scheme != "file") return null
    return path
        ?.let(::File)
        ?.takeIf(File::isFile)
}

private fun Uri.archiveCacheKey(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(Charsets.UTF_8))
    return digest.toLowerHexString()
}

private fun ByteArray.toLowerHexString(): String = buildString(size * 2) {
    this@toLowerHexString.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0f])
    }
}

private fun File.hasReadableContent(): Boolean = exists() && length() > 0L

private fun File.writeTextAtomically(
    text: String,
) {
    val targetDirectory = parentFile ?: return writeText(text)
    targetDirectory.mkdirs()
    val tempFile = File.createTempFile("$name.", ".tmp", targetDirectory)
    try {
        tempFile.writeText(text)
        tempFile.moveReplacing(this)
    } finally {
        tempFile.delete()
    }
}

private fun File.moveReplacing(
    targetFile: File,
) {
    try {
        Files.move(
            toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: Exception) {
        Files.move(
            toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
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
private const val ZIP_ARCHIVE_CACHE_DIRECTORY = "comic-zip-archives"
private const val RAR_ARCHIVE_CACHE_DIRECTORY = "comic-archives"
private const val HEX_DIGITS = "0123456789abcdef"

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object ComicArchiveCodecsTestAccess {
    fun zipArchiveCacheDirectory(
        context: Context,
    ): File = File(context.cacheDir, ZIP_ARCHIVE_CACHE_DIRECTORY)

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
