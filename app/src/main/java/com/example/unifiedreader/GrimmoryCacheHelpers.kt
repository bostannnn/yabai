package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun String.toSafeFileSegment(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

internal fun String.toGrimmoryFileSegment(): String = toSafeFileSegment()

internal fun String.toStableCacheHashSegment(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun ReadableItem.coverIdentityFileSegment(): String? = coverUrl
    ?.takeIf(String::isNotBlank)
    ?.toCoverCacheIdentity()
    ?.toStableCacheHashSegment()
    ?.let { hash -> "cover-$hash" }

private fun String.toCoverCacheIdentity(): String = runCatching {
    val uri = URI(this)
    val filteredQuery = uri.query
        ?.split("&")
        ?.filterNot { queryPart ->
            queryPart.substringBefore("=").equals("apiKey", ignoreCase = true)
        }
        ?.joinToString("&")
        ?.takeIf(String::isNotBlank)
    URI(
        uri.scheme,
        uri.userInfo,
        uri.host,
        uri.port,
        uri.path,
        filteredQuery,
        uri.fragment,
    ).toString()
}.getOrDefault(this)

internal fun ReadableItem.grimmoryCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileId?.toSafeFileSegment(),
    coverIdentityFileSegment(),
).joinToString(separator = "-").plus(".$extension")

internal fun ReadableItem.grimmoryLegacyCacheFileNames(
    extension: String,
): List<String> {
    if (coverIdentityFileSegment() != null) return emptyList()
    return listOf(
        listOfNotNull(
            id.toSafeFileSegment(),
            remoteFileId?.toSafeFileSegment(),
        ).joinToString(separator = "-").plus(".$extension"),
    )
}

internal suspend fun writeCachedFile(
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

internal suspend fun writeGrimmoryCacheFile(
    destination: File,
    targetDescription: String,
    writeTemporaryFile: suspend (File) -> Unit,
) = writeCachedFile(
    destination = destination,
    targetDescription = targetDescription,
    writeTemporaryFile = writeTemporaryFile,
)
