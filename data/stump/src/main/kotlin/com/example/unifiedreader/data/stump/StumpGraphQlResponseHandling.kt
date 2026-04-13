package com.example.unifiedreader.data.stump

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class StumpGraphQlErrorDetail(
    val message: String,
    val path: List<String> = emptyList(),
    val extensionCode: String? = null,
) {
    val displayMessage: String
        get() = path.takeIf(List<String>::isNotEmpty)
            ?.joinToString(".")
            ?.let { path -> "$path: $message" }
            ?: message
}

class StumpGraphQlException(
    val errors: List<StumpGraphQlErrorDetail>,
) : RuntimeException(
    errors.firstOrNull()?.displayMessage ?: "Stump GraphQL request failed.",
)

internal data class StumpGraphQlDecodedResponse(
    val data: JsonObject,
    val errors: List<StumpGraphQlErrorDetail>,
)

internal fun StumpGraphQlResponseDto.requireGraphQlData(
    allowPartialData: Boolean = false,
): StumpGraphQlDecodedResponse {
    val details = errors.orEmpty()
        .mapNotNull(StumpGraphQlErrorDto::toDetail)
    details.firstOrNull(StumpGraphQlErrorDetail::isUnauthorized)
        ?.let {
            throw StumpUnauthorizedException()
        }
    if (details.isNotEmpty() && !allowPartialData) {
        throw StumpGraphQlException(details)
    }
    val responseData = data
    if (responseData != null) {
        return StumpGraphQlDecodedResponse(
            data = responseData,
            errors = details,
        )
    }
    if (details.isNotEmpty()) {
        throw StumpGraphQlException(details)
    }
    error("Stump GraphQL response did not include data.")
}

private fun StumpGraphQlErrorDto.toDetail(): StumpGraphQlErrorDetail? {
    val resolvedMessage = message?.takeIf(String::isNotBlank) ?: return null
    return StumpGraphQlErrorDetail(
        message = resolvedMessage,
        path = path.orEmpty().map { element ->
            when (element) {
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        },
        extensionCode = extensions?.get("code")?.let { element ->
            when (element) {
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        }?.takeIf(String::isNotBlank),
    )
}

private val StumpGraphQlErrorDetail.isUnauthorized: Boolean
    get() = message.isUnauthorizedGraphQlMessage() ||
        extensionCode?.isUnauthorizedGraphQlExtensionCode() == true

internal fun String.isUnauthorizedGraphQlMessage(): Boolean = contains(
    other = "unauthorized",
    ignoreCase = true,
) || contains(
    other = "not authenticated",
    ignoreCase = true,
) || contains(
    other = "invalid token",
    ignoreCase = true,
)

internal fun String.isUnauthorizedGraphQlExtensionCode(): Boolean {
    val normalized = trim()
        .replace('-', '_')
        .uppercase()
    return normalized == "UNAUTHENTICATED" ||
        normalized == "UNAUTHORIZED" ||
        normalized == "INVALID_TOKEN" ||
        normalized == "TOKEN_EXPIRED" ||
        normalized == "JWT_EXPIRED" ||
        normalized == "AUTHENTICATION_ERROR"
}
