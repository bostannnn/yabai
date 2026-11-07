package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState

internal typealias AppUiStateProvider = () -> AppUiState
internal typealias AppUiStateUpdater = ((AppUiState) -> AppUiState) -> Unit

// withStoredSession leaves the message and whether it is an error as they were: the session is
// saved again on its own (a token renewal, a new network), and an error that turned plain on that hid a
// failed first sync behind a bar that never filled.
internal fun RemoteSourceConnectionUiState.withStoredSession(
    session: StoredSourceSession?,
): RemoteSourceConnectionUiState = if (session == null) {
    copy(
        availableLibraries = emptyList(),
        hasStoredSession = false,
        isSessionKnown = true,
        activeAction = null,
        syncProgressFraction = null,
        syncProgressLabel = null,
    )
} else {
    copy(
        displayName = session.displayName,
        baseUrl = session.baseUrl,
        backupBaseUrl = session.backupBaseUrl.orEmpty(),
        username = session.username,
        password = "",
        availableLibraries = emptyList(),
        hasStoredSession = true,
        isSessionKnown = true,
        activeAction = null,
        syncProgressFraction = null,
        syncProgressLabel = null,
    )
}

internal fun GrimmorySession.toStoredSession(
    displayName: String,
    baseUrl: String,
    backupBaseUrl: String?,
    activeBaseUrl: String?,
    username: String,
): GrimmoryStoredSession = GrimmoryStoredSession(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    activeBaseUrl = activeBaseUrl,
    username = username,
    accessToken = accessToken,
    refreshToken = refreshToken,
)

internal fun KavitaSession.toStoredSession(
    displayName: String,
    baseUrl: String,
    backupBaseUrl: String?,
    activeBaseUrl: String?,
    username: String,
): KavitaStoredSession = KavitaStoredSession(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    activeBaseUrl = activeBaseUrl,
    username = username,
    accessToken = accessToken,
    refreshToken = refreshToken,
    apiKey = apiKey,
)

internal fun StumpSession.toStoredSession(
    displayName: String,
    baseUrl: String,
    backupBaseUrl: String?,
    activeBaseUrl: String?,
    username: String,
): StumpStoredSession = StumpStoredSession(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    activeBaseUrl = activeBaseUrl,
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
