package com.example.unifiedreader.core.network

import kotlinx.serialization.json.Json

/**
 * The one way the app reads a server's JSON: fields it does not know are skipped, and a missing nullable field
 * reads as null. The shared HTTP client and every source's hand-decoded bodies use it.
 */
val LenientJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
