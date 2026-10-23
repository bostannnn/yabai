package com.example.unifiedreader.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The shared client. Its short default timeouts suit the home servers on the LAN; a client for
 * the internet (the GitHub updater) passes longer ones.
 */
fun buildHttpClient(
    connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
): HttpClient =
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 2_000L
private const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 5_000L
