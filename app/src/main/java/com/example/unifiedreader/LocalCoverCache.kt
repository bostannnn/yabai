package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import java.io.File

internal class LocalCoverCache(
    private val context: Context,
    private val comicArchiveCodecs: List<ComicArchiveCodec>,
) {
    fun resolvePersistedCoverUri(
        itemId: String,
        coverFileName: String?,
    ): String? = coverFileName
        ?.takeIf { coverFile(it).isValidCoverFileFor(itemId) }
        ?.let(::coverUriForFileName)

    suspend fun attach(
        record: LocalDocumentRecord,
        forceRefresh: Boolean = false,
    ): LocalDocumentRecord {
        val existingCoverFileName = existingCoverFileName(record)
        val coverFileName = runCatching {
            when (record.format) {
                FormatType.CBZ,
                FormatType.CBR -> cacheComicCover(record, forceRefresh)
                FormatType.EPUB -> cacheEpubCover(record, forceRefresh)
                FormatType.PDF -> null
            }
        }.getOrNull() ?: existingCoverFileName
        return record.copy(
            coverFileName = coverFileName,
            coverUri = coverFileName?.let(::coverUriForFileName),
        )
    }

    fun clear(itemId: String) {
        coverStorageDirectory()
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("$itemId.") }
            ?.forEach(File::delete)
    }

    private suspend fun cacheComicCover(
        record: LocalDocumentRecord,
        forceRefresh: Boolean,
    ): String? {
        if (!forceRefresh) {
            existingCoverFileName(record)?.let { return it }
        }
        val coverEntryName = record.pageEntryNames.firstOrNull() ?: return null
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return null
        val bytes = codec.readEntryBytes(
            context = context,
            uri = Uri.parse(record.uri),
            entryName = coverEntryName,
        ) ?: return null
        val extension = coverEntryName.substringAfterLast('.', "jpg")
        return cacheCoverBytes(record.id, extension, bytes)
    }

    private suspend fun cacheEpubCover(
        record: LocalDocumentRecord,
        forceRefresh: Boolean,
    ): String? {
        if (!forceRefresh) {
            existingCoverFileName(record)?.let { return it }
        }
        val coverEntryName = record.epubCoverEntryName ?: extractEpubMetadata(
            context = context,
            uri = Uri.parse(record.uri),
        )?.coverEntryName ?: return null
        val bytes = readZipEntryBytes(
            context = context,
            uri = Uri.parse(record.uri),
            entryName = coverEntryName,
        ) ?: return null
        val extension = coverEntryName.substringAfterLast('.', "jpg")
        return cacheCoverBytes(record.id, extension, bytes)
    }

    private fun existingCoverFileName(
        record: LocalDocumentRecord,
    ): String? = record.coverFileName
        ?.takeIf { coverFile(it).isValidCoverFileFor(record.id) }
        ?: coverStorageDirectory()
            .listFiles()
            ?.firstOrNull { it.isValidCoverFileFor(record.id) }
            ?.name

    private fun cacheCoverBytes(
        itemId: String,
        extension: String,
        bytes: ByteArray,
    ): String? = runCatching {
        val normalizedExtension = extension.lowercase().ifBlank { "jpg" }
        val coverDirectory = coverStorageDirectory()
        val coverFile = File(coverDirectory, "$itemId.${System.nanoTime()}.$normalizedExtension")
        val tempFile = File(coverDirectory, "${coverFile.name}.tmp")
        tempFile.writeBytes(bytes)
        if (tempFile.length() <= 0L) {
            tempFile.delete()
            return@runCatching null
        }
        val moved = tempFile.renameTo(coverFile) || runCatching {
            tempFile.copyTo(coverFile, overwrite = false)
            tempFile.delete()
            true
        }.getOrDefault(false)
        if (!moved || coverFile.length() <= 0L) {
            tempFile.delete()
            return@runCatching null
        }
        coverDirectory.listFiles()
            ?.filter { it.isValidCoverFileFor(itemId) && it.name != coverFile.name }
            ?.forEach(File::delete)
        coverFile.name
    }.getOrNull()

    private fun coverStorageDirectory(): File = File(context.filesDir, "imported-cover-cache").apply {
        mkdirs()
    }

    private fun coverFile(
        coverFileName: String,
    ): File = File(coverStorageDirectory(), coverFileName)

    private fun coverUriForFileName(
        coverFileName: String,
    ): String = Uri.fromFile(coverFile(coverFileName)).toString()
}

private fun File.isValidCoverFileFor(
    itemId: String,
): Boolean = isFile &&
    name.startsWith("$itemId.") &&
    !name.endsWith(".tmp") &&
    length() > 0L
