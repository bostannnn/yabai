package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun prepareImportedEpubFile(
    context: Context,
    item: ReadableItem,
    documentUri: Uri,
): File = withContext(Dispatchers.IO) {
    val outputDirectory = File(context.cacheDir, "epub-imports").apply { mkdirs() }
    val outputFile = File(outputDirectory, "${item.id}.epub")
    context.contentResolver.openInputStream(documentUri)?.use { input ->
        FileOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    } ?: error("Unable to open imported EPUB file.")
    outputFile
}
