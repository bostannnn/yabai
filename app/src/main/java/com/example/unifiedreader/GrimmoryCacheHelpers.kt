package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun String.toGrimmoryFileSegment(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

internal fun ReadableItem.grimmoryCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toGrimmoryFileSegment(),
    remoteFileId?.toGrimmoryFileSegment(),
).joinToString(separator = "-").plus(".$extension")

internal suspend fun writeGrimmoryCacheFile(
    destination: File,
    targetDescription: String,
    writeTemporaryFile: suspend (File) -> Unit,
) = withContext(Dispatchers.IO) {
    destination.parentFile?.mkdirs()
    val temporaryFile = File(destination.parentFile, "${destination.name}.tmp")
    temporaryFile.delete()
    try {
        writeTemporaryFile(temporaryFile)
        if (destination.exists() && !destination.delete()) {
            error("Unable to replace $targetDescription.")
        }
        if (!temporaryFile.renameTo(destination)) {
            error("Unable to finalize $targetDescription.")
        }
    } catch (throwable: Throwable) {
        temporaryFile.delete()
        throw throwable
    }
}
