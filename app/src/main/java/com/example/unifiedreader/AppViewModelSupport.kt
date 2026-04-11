package com.example.unifiedreader

import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.stump.StumpSession
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.StumpConnectionUiState

internal typealias AppUiStateProvider = () -> AppUiState
internal typealias AppUiStateUpdater = ((AppUiState) -> AppUiState) -> Unit

internal fun GrimmoryConnectionUiState.withStoredSession(
    session: GrimmoryStoredSession?,
): GrimmoryConnectionUiState = if (session == null) {
    copy(hasStoredSession = false)
} else {
    copy(
        displayName = session.displayName,
        baseUrl = session.baseUrl,
        backupBaseUrl = session.backupBaseUrl.orEmpty(),
        username = session.username,
        password = "",
        hasStoredSession = true,
        isError = false,
    )
}

internal fun KavitaConnectionUiState.withStoredSession(
    session: KavitaStoredSession?,
): KavitaConnectionUiState = if (session == null) {
    copy(
        availableLibraries = emptyList(),
        hasStoredSession = false,
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
        syncProgressFraction = null,
        syncProgressLabel = null,
        isError = false,
    )
}

internal fun StumpConnectionUiState.withStoredSession(
    session: StumpStoredSession?,
): StumpConnectionUiState = if (session == null) {
    copy(
        availableLibraries = emptyList(),
        hasStoredSession = false,
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
        syncProgressFraction = null,
        syncProgressLabel = null,
        isError = false,
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
