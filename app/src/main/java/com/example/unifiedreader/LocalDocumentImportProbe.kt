package com.example.unifiedreader

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.unifiedreader.core.model.FormatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

internal class LocalDocumentImportProbe(
    private val context: Context,
    private val comicArchiveCodecs: List<ComicArchiveCodec>,
) {
    suspend fun probe(uri: Uri): LocalDocumentRecord? {
        val persistedUri = uri.toString()
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Imported Document"
        val contentType = queryContentType(uri)
        return resolveImportedRecord(
            displayName = displayName,
            contentType = contentType,
            uri = uri,
            persistedUri = persistedUri,
        )
    }

    private suspend fun resolveImportedRecord(
        displayName: String,
        contentType: String?,
        uri: Uri,
        persistedUri: String,
    ): LocalDocumentRecord? {
        if (isEpubDocument(displayName, contentType) && isValidEpubDocument(uri)) {
            val metadata = extractEpubMetadata(
                context = context,
                uri = uri,
            )
            return LocalDocumentRecord(
                id = buildItemId(uri, FormatType.EPUB),
                uri = persistedUri,
                displayName = displayName,
                title = metadata?.title?.takeIf(String::isNotBlank)
                    ?: displayName.removeSuffixIgnoreCase(".epub"),
                format = FormatType.EPUB,
                pageCount = 0,
                pageEntryNames = emptyList(),
                author = metadata?.author,
                epubCoverEntryName = metadata?.coverEntryName,
            )
        }
        if (isPdfDocument(displayName, contentType) && isValidPdfDocument(uri)) {
            return LocalDocumentRecord(
                id = buildItemId(uri, FormatType.PDF),
                uri = persistedUri,
                displayName = displayName,
                title = displayName.removeSuffixIgnoreCase(".pdf"),
                format = FormatType.PDF,
                pageCount = 0,
                pageEntryNames = emptyList(),
            )
        }
        val archive = resolveComicArchive(displayName, uri) ?: return null
        val pageCount = archive.pageEntryNames.size
        if (pageCount <= 0) return null
        return LocalDocumentRecord(
            id = buildItemId(uri, archive.codec.format),
            uri = persistedUri,
            displayName = displayName,
            title = displayName.comicArchiveTitle(),
            format = archive.codec.format,
            pageCount = pageCount,
            pageEntryNames = archive.pageEntryNames,
            series = archive.comicMetadata?.series,
            issueTitle = archive.comicMetadata?.issueTitle,
            volume = archive.comicMetadata?.volume,
            issueNumber = archive.comicMetadata?.issueNumber,
            author = archive.comicMetadata?.writer,
        )
    }

    private suspend fun resolveComicArchive(
        displayName: String,
        uri: Uri,
    ): ResolvedComicArchive? {
        val prioritizedCodecs = comicArchiveCodecs.sortedByDescending { codec ->
            if (codec.matchesFileName(displayName)) 1 else 0
        }
        for (codec in prioritizedCodecs) {
            val resolvedArchive = runCatching {
                val entryNames = codec.listEntries(context, uri)
                val pageEntryNames = entryNames.filter(String::isImageEntryName)
                    .sortedWith(NaturalPageOrderComparator)
                if (pageEntryNames.isEmpty()) {
                    null
                } else {
                    ResolvedComicArchive(
                        codec = codec,
                        pageEntryNames = pageEntryNames,
                        comicMetadata = entryNames
                            .firstOrNull(::isComicInfoEntryName)
                            ?.let { metadataEntryName ->
                                codec.readEntryBytes(
                                    context = context,
                                    uri = uri,
                                    entryName = metadataEntryName,
                                )
                            }
                            ?.let(::parseComicInfoMetadata),
                    )
                }
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Failed to probe ${codec.format.name} archive for $displayName", throwable)
            }.getOrNull()
            if (resolvedArchive != null) {
                return resolvedArchive
            }
        }
        return null
    }

    private fun isValidEpubDocument(
        uri: Uri,
    ): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var hasContainerXml = false
                var hasOpf = false
                var mimetypeMatches = false
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        "mimetype" -> {
                            val value = zip.readBytes().decodeToString().trim()
                            mimetypeMatches = value == IMPORTED_EPUB_MIMETYPE
                        }

                        "META-INF/container.xml" -> hasContainerXml = true
                        else -> if (entry.name.endsWith(".opf", ignoreCase = true)) {
                            hasOpf = true
                        }
                    }
                    zip.closeEntry()
                }
                mimetypeMatches && hasContainerXml && hasOpf
            }
        } ?: false
    }.getOrElse { throwable ->
        Log.w(LOG_TAG, "Failed to validate EPUB import for $uri", throwable)
        false
    }

    private fun isValidPdfDocument(
        uri: Uri,
    ): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val signature = ByteArray(PDF_SIGNATURE.size)
            val bytesRead = input.read(signature)
            bytesRead == PDF_SIGNATURE.size && signature.contentEquals(PDF_SIGNATURE)
        } ?: false
    }.getOrElse { throwable ->
        Log.w(LOG_TAG, "Failed to validate PDF import for $uri", throwable)
        false
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
            }
    }

    private fun queryContentType(uri: Uri): String? = context.contentResolver.getType(uri)

    private fun buildItemId(
        uri: Uri,
        format: FormatType,
    ): String = "${format.localImportFormatSpec().idPrefix}-${uri.toString().hashCode().toUInt().toString(16)}"

    private data class ResolvedComicArchive(
        val codec: ComicArchiveCodec,
        val pageEntryNames: List<String>,
        val comicMetadata: ImportedComicMetadata? = null,
    )

    private companion object {
        const val LOG_TAG = "ImportedComicArchive"
    }
}

private fun Cursor.getStringOrNull(
    columnName: String,
): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0) getString(index) else null
}
