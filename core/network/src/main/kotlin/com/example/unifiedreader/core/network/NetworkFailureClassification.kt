package com.example.unifiedreader.core.network

import java.io.IOException

fun Throwable.isRetryableNetworkFailure(): Boolean = generateSequence(this) { error ->
    error.cause
}.any { error ->
    error is IOException ||
        error::class.simpleName in setOf(
            "ConnectTimeoutException",
            "HttpRequestTimeoutException",
            "SocketTimeoutException",
            "UnresolvedAddressException",
        )
}
