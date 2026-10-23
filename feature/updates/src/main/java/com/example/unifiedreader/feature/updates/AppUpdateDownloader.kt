package com.example.unifiedreader.feature.updates

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Fetches a release APK to a file. */
fun interface AppUpdateDownloader {
    /**
     * Writes [apk] to [destination], reporting bytes written and the total as it goes, and
     * returns the file's lowercase hex SHA-256. Throws on failure; cancellable between reads.
     */
    suspend fun download(
        apk: AppReleaseApk,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): String
}

/** Streams the asset (GitHub redirects it to its download host) without holding it in memory. */
class KtorAppUpdateDownloader(
    private val httpClient: HttpClient,
) : AppUpdateDownloader {
    override suspend fun download(
        apk: AppReleaseApk,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): String = httpClient.prepareGet(apk.downloadUrl) {
        header(HttpHeaders.Accept, "application/octet-stream")
        header(HttpHeaders.UserAgent, "yabai-android")
    }.execute { response ->
        if (!response.status.isSuccess()) {
            throw IOException("The download answered ${response.status.value}")
        }
        val totalBytes = response.contentLength()?.takeIf { it > 0 } ?: apk.sizeBytes
        val digest = MessageDigest.getInstance("SHA-256")
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var downloadedBytes = 0L
        destination.outputStream().use { output ->
            while (true) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead == -1) break
                if (bytesRead > 0) {
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress(downloadedBytes, totalBytes)
                }
            }
        }
        if (totalBytes > 0 && downloadedBytes < totalBytes) {
            throw IOException("The download stopped at $downloadedBytes of $totalBytes bytes")
        }
        digest.digest().toHex()
    }
}

internal fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
