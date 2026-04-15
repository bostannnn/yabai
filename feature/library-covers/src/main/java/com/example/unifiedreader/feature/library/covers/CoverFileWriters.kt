package com.example.unifiedreader.feature.library.covers

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun writeCoverCacheFile(
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
