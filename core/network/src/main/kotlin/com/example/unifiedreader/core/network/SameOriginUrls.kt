package com.example.unifiedreader.core.network

import java.net.URI

/**
 * This URL made absolute against [baseUrl], or null when it points at another server (scheme, host or port
 * differ) or is not a URL. A source checks this before sending its credentials with a URL the server handed it.
 */
fun String.resolveSameOriginUrl(baseUrl: String): String? = runCatching {
    val base = URI.create(baseUrl.trimEnd('/') + "/")
    val candidate = URI.create(this)
    val resolved = if (candidate.isAbsolute) candidate else base.resolve(candidate)
    resolved.toString().takeIf { resolved.hasSameOriginAs(base) }
}.getOrNull()

private fun URI.hasSameOriginAs(other: URI): Boolean =
    scheme?.equals(other.scheme, ignoreCase = true) == true &&
        host?.equals(other.host, ignoreCase = true) == true &&
        effectivePort() == other.effectivePort()

private fun URI.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    scheme.equals("http", ignoreCase = true) -> 80
    else -> -1
}
