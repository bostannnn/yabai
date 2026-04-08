package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import java.util.Locale

internal fun deletionMessage(
    item: ReadableItem,
    outcome: ImportedItemDeletionOutcome,
): String = when (outcome) {
    ImportedItemDeletionOutcome.Removed -> "Removed ${item.libraryDisplayLabel()}."
    ImportedItemDeletionOutcome.NotImportedItem -> "Only imported files can be removed from the library."
    ImportedItemDeletionOutcome.NotFound -> "Unable to remove ${item.libraryDisplayLabel()}."
}

internal fun normalizeServerBaseUrl(
    baseUrl: String,
): String? {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase(Locale.US)
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    return trimmed
}

internal fun GrimmoryConnectionUiState.withStoredSession(
    session: GrimmoryStoredSession?,
): GrimmoryConnectionUiState = if (session == null) {
    copy(hasStoredSession = false)
} else {
    copy(
        displayName = session.displayName,
        baseUrl = session.baseUrl,
        username = session.username,
        password = "",
        hasStoredSession = true,
        isError = false,
    )
}

internal fun GrimmorySession.toStoredSession(
    displayName: String,
    baseUrl: String,
    username: String,
): GrimmoryStoredSession = GrimmoryStoredSession(
    displayName = displayName,
    baseUrl = baseUrl,
    username = username,
    accessToken = accessToken,
    refreshToken = refreshToken,
)

internal fun Throwable.userFacingMessage(): String =
    message?.takeIf(String::isNotBlank) ?: this::class.simpleName ?: "unknown error"

internal inline fun <T, R> Iterable<T>.associateNotNull(
    transform: (T) -> Pair<String, R>?,
): Map<String, R> = buildMap {
    for (element in this@associateNotNull) {
        val pair = transform(element) ?: continue
        put(pair.first, pair.second)
    }
}
