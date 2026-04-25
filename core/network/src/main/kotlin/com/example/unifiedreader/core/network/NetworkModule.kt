package com.example.unifiedreader.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun buildHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = DEFAULT_SOCKET_TIMEOUT_MILLIS
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
