package com.example.unifiedreader.core.model

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes [destination] through a temporary file beside it, so a reader never sees half a file.
 *
 * [writeTemporaryFile] fills the temporary file; it then replaces [destination] in one move. On any failure the
 * temporary file is removed, [destination] keeps its old content, and the error is rethrown. [targetDescription]
 * names the file in the error ("Unable to finalize the cover.").
 */
suspend fun writeFileAtomically(
    destination: File,
    targetDescription: String,
    writeTemporaryFile: suspend (File) -> Unit,
) = withContext(Dispatchers.IO) {
    destination.parentFile?.mkdirs()
    val temporaryFile = File(destination.parentFile, "${destination.name}.tmp")
    temporaryFile.delete()
    try {
        writeTemporaryFile(temporaryFile)
        try {
            temporaryFile.moveReplacing(destination)
        } catch (exception: IOException) {
            throw IllegalStateException("Unable to finalize $targetDescription.", exception)
        }
    } finally {
        temporaryFile.delete()
    }
}

/**
 * Moves this file onto [target], replacing whatever is there: atomically where the file system allows, as a
 * plain replace where it does not. Throws [IOException] when the move fails.
 */
fun File.moveReplacing(target: File) {
    try {
        Files.move(toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: IOException) {
        // AtomicMoveNotSupportedException, or a file system that refuses the atomic replace: try a plain one.
        Files.move(toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
