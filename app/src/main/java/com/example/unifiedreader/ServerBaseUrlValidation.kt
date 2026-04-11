package com.example.unifiedreader

import java.net.URI
import java.util.Locale

internal const val SERVER_URL_SCHEME_MESSAGE = "Enter a server URL that starts with http:// or https://."
internal const val HTTP_WARNING_MESSAGE =
    "HTTP is not encrypted. Use HTTPS when possible."

internal fun normalizeServerBaseUrl(
    baseUrl: String,
): String? = validateServerBaseUrl(baseUrl).normalizedBaseUrlOrNull()

internal fun validateServerBaseUrl(
    baseUrl: String,
): ServerBaseUrlValidation {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) {
        return ServerBaseUrlValidation.Invalid(SERVER_URL_SCHEME_MESSAGE)
    }
    val lower = trimmed.lowercase(Locale.US)
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return ServerBaseUrlValidation.Invalid(SERVER_URL_SCHEME_MESSAGE)
    }
    val host = runCatching { URI(trimmed).host }.getOrNull()
    if (host.isNullOrBlank()) {
        return ServerBaseUrlValidation.Invalid(SERVER_URL_SCHEME_MESSAGE)
    }
    return ServerBaseUrlValidation.Valid(
        normalizedBaseUrl = trimmed,
        cleartextWarningMessage = if (lower.startsWith("http://")) HTTP_WARNING_MESSAGE else null,
    )
}

internal sealed interface ServerBaseUrlValidation {
    data class Valid(
        val normalizedBaseUrl: String,
        val cleartextWarningMessage: String?,
    ) : ServerBaseUrlValidation

    data class Invalid(
        val message: String,
    ) : ServerBaseUrlValidation
}

private fun ServerBaseUrlValidation.normalizedBaseUrlOrNull(): String? = when (this) {
    is ServerBaseUrlValidation.Valid -> normalizedBaseUrl
    is ServerBaseUrlValidation.Invalid -> null
}
