package com.example.unifiedreader

import java.net.URI
import java.util.Locale

internal const val SERVER_URL_SCHEME_MESSAGE = "Enter a server URL that starts with http:// or https://."
internal const val LOCAL_HTTP_ONLY_MESSAGE =
    "Use HTTPS for public server URLs. HTTP is only allowed for local or private network addresses."
internal const val LOCAL_HTTP_WARNING_MESSAGE = "Local HTTP is not encrypted. Use HTTPS outside your private network."

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
    if (lower.startsWith("https://")) {
        return ServerBaseUrlValidation.Valid(
            normalizedBaseUrl = trimmed,
            cleartextWarningMessage = null,
        )
    }

    val host = runCatching { URI(trimmed).host }.getOrNull()
    if (host.isNullOrBlank()) {
        return ServerBaseUrlValidation.Invalid(SERVER_URL_SCHEME_MESSAGE)
    }
    if (!host.isLocalOrPrivateServerHost()) {
        return ServerBaseUrlValidation.Invalid(LOCAL_HTTP_ONLY_MESSAGE)
    }
    return ServerBaseUrlValidation.Valid(
        normalizedBaseUrl = trimmed,
        cleartextWarningMessage = LOCAL_HTTP_WARNING_MESSAGE,
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

private fun String.isLocalOrPrivateServerHost(): Boolean {
    val normalized = trim().trim('.').lowercase(Locale.US)
    if (normalized == "localhost" || normalized.endsWith(".localhost")) return true
    if (normalized.endsWith(".local")) return true
    if (normalized.isNotEmpty() && "." !in normalized && ":" !in normalized) return true
    return normalized.isLocalOrPrivateIpv4Host() || normalized.isLocalOrPrivateIpv6Host()
}

private fun String.isLocalOrPrivateIpv4Host(): Boolean {
    val octets = split(".").map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    return when (octets[0]) {
        0, 10, 127 -> true
        169 -> octets[1] == 254
        172 -> octets[1] in 16..31
        192 -> octets[1] == 168
        else -> false
    }
}

private fun String.isLocalOrPrivateIpv6Host(): Boolean {
    val address = removePrefix("[").removeSuffix("]").substringBefore("%")
    if (address == "::1" || address == "0:0:0:0:0:0:0:1") return true
    val firstHextet = address.substringBefore(":").takeIf(String::isNotBlank) ?: return false
    val firstValue = firstHextet.toIntOrNull(16) ?: return false
    return firstValue in 0xfc00..0xfdff || firstValue in 0xfe80..0xfebf
}
