package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.ui.components.AppStatusTone

/** One remote server's connection as the settings UI shows it: the form, the saved sign-in and any sync under way. */
data class RemoteSourceConnectionUiState(
    val source: SourceType,
    val displayName: String = source.defaultServerName(),
    val baseUrl: String = source.defaultServerUrl(),
    val backupBaseUrl: String = source.defaultBackupServerUrl(),
    val username: String = "",
    val password: String = "",
    /** The server's libraries after a sign-in; Kavita and Stump list them, Grimmory does not. */
    val availableLibraries: List<Library> = emptyList(),
    val hasStoredSession: Boolean = false,
    /** False until the stored session (or its absence) has loaded after launch. */
    val isSessionKnown: Boolean = false,
    val isConnecting: Boolean = false,
    val activeAction: ServerConnectionAction? = null,
    val syncProgressFraction: Float? = null,
    val syncProgressLabel: String? = null,
    val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

private fun SourceType.defaultServerName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.STUMP -> "Stump"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> ""
}

private fun SourceType.defaultServerUrl(): String = when (this) {
    SourceType.KAVITA -> DefaultKavitaServerUrl
    SourceType.STUMP -> DefaultStumpServerUrl
    SourceType.GRIMMORY -> DefaultGrimmoryServerUrl
    SourceType.LOCAL -> ""
}

private fun SourceType.defaultBackupServerUrl(): String = when (this) {
    SourceType.KAVITA -> DefaultKavitaBackupServerUrl
    SourceType.STUMP -> DefaultStumpBackupServerUrl
    SourceType.GRIMMORY -> DefaultGrimmoryBackupServerUrl
    SourceType.LOCAL -> ""
}

/** Everything a source page can do, bundled so the app wires one object per source. */
data class RemoteSourceSettingsActions(
    val onDisplayNameChanged: (String) -> Unit,
    val onBaseUrlChanged: (String) -> Unit,
    val onBackupBaseUrlChanged: (String) -> Unit,
    val onUsernameChanged: (String) -> Unit,
    val onPasswordChanged: (String) -> Unit,
    val onConnect: () -> Unit,
    val onSync: () -> Unit,
    val onFastSync: (() -> Unit)? = null,
    val onPreloadCovers: () -> Unit,
    val onRetryFailedCovers: () -> Unit,
    val onCancelCoverPreload: () -> Unit,
    val onClearCoverCache: () -> Unit,
)

/** One remote source as the settings hub and its page see it. */
data class SourceSettingsEntry(
    val sourceType: SourceType,
    val connection: RemoteSourceConnectionUiState,
    val actions: RemoteSourceSettingsActions,
    val connectivity: SourceConnectivityState = SourceConnectivityState.Unknown,
    val readingDirectionLibraries: List<ReadingDirectionLibraryUiState> = emptyList(),
    val readerModeLibraries: List<ReaderModeLibraryUiState> = emptyList(),
    /** When its library last synced on this device, if it has. */
    val lastSyncedAtEpochMillis: Long? = null,
)

/** A source's line on the hub and the tone of the dot before it. */
data class SourceHubStatus(
    val label: String,
    val tone: AppStatusTone,
)

/**
 * The line under a source's name on the hub: what it is doing, or when it last synced. Connected
 * and reachable is the good dot; everything else (signed out, offline, busy) the neutral one.
 */
fun remoteSourceHubStatus(
    connection: RemoteSourceConnectionUiState,
    connectivity: SourceConnectivityState,
    lastSyncedAtEpochMillis: Long?,
    nowEpochMillis: Long = System.currentTimeMillis(),
): SourceHubStatus = when {
    connection.isConnecting -> when (connection.activeAction) {
        ServerConnectionAction.Connect, null -> SourceHubStatus("Connecting", AppStatusTone.Neutral)
        ServerConnectionAction.Sync,
        ServerConnectionAction.FastSync,
        ServerConnectionAction.FullSync -> SourceHubStatus("Syncing", AppStatusTone.Neutral)
    }
    !connection.hasStoredSession -> SourceHubStatus("Not connected", AppStatusTone.Neutral)
    connectivity == SourceConnectivityState.Offline -> SourceHubStatus("Offline", AppStatusTone.Neutral)
    connectivity == SourceConnectivityState.Checking -> SourceHubStatus("Checking", AppStatusTone.Neutral)
    lastSyncedAtEpochMillis != null -> SourceHubStatus(
        "Synced ${formatSettingsShortMoment(lastSyncedAtEpochMillis, nowEpochMillis)}",
        AppStatusTone.Good,
    )
    else -> SourceHubStatus("Connected", AppStatusTone.Good)
}

internal fun RemoteSourceConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    activeAction = activeAction,
    syncProgressFraction = syncProgressFraction,
    syncProgressLabel = syncProgressLabel,
    message = message,
    isError = isError,
    canConnect = canSubmit,
)
