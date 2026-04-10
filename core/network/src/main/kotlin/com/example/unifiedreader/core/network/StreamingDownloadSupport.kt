package com.example.unifiedreader.core.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.File

suspend fun HttpResponse.writeBodyTo(
    destination: File,
) {
    val channel = bodyAsChannel()
    val buffer = ByteArray(DEFAULT_DOWNLOAD_BUFFER_SIZE)
    destination.outputStream().use { output ->
        while (true) {
            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
            if (bytesRead == -1) break
            if (bytesRead > 0) {
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}

private const val DEFAULT_DOWNLOAD_BUFFER_SIZE = 8 * 1024
