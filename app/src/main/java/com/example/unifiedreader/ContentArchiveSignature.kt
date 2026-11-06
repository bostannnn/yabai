package com.example.unifiedreader

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** What [queryArchiveSignature] returns when the provider tells nothing about the file. */
internal const val UNKNOWN_ARCHIVE_SIGNATURE = "unknown"

/**
 * "size:lastModified" for a content URI, so a cache built from the file can tell when it changed; either part is
 * -1 when the provider leaves it out. [UNKNOWN_ARCHIVE_SIGNATURE] for any other URI, or when the provider gives
 * neither part or cannot be asked.
 */
internal fun ContentResolver.queryArchiveSignature(uri: Uri): String {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return UNKNOWN_ARCHIVE_SIGNATURE
    val projection = arrayOf(
        OpenableColumns.SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
    return runCatching {
        query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use UNKNOWN_ARCHIVE_SIGNATURE
            val size = cursor.getLongOrNull(OpenableColumns.SIZE) ?: -1L
            val lastModified = cursor.getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: -1L
            if (size < 0L && lastModified < 0L) UNKNOWN_ARCHIVE_SIGNATURE else "$size:$lastModified"
        } ?: UNKNOWN_ARCHIVE_SIGNATURE
    }.getOrDefault(UNKNOWN_ARCHIVE_SIGNATURE)
}

/** The column's value, or null when the cursor has no such column or the value is null. */
internal fun Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}
