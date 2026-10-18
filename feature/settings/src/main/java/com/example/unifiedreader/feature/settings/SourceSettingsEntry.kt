package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState

/** What the settings UI needs to know about one remote server's connection. */
interface RemoteSourceConnectionUiState {
    val displayName: String
    val baseUrl: String
    val backupBaseUrl: String
    val username: String
    val password: String
    val hasStoredSession: Boolean
    val isConnecting: Boolean
    val activeAction: ServerConnectionAction?
    val syncProgressFraction: Float?
    val syncProgressLabel: String?
    val coverPreload: CoverPreloadUiState
    val message: String?
    val isError: Boolean
    val canSubmit: Boolean
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
)

/** The one-line status under a source's name on the hub. */
fun remoteSourceStatusLabel(
    connection: RemoteSourceConnectionUiState,
    connectivity: SourceConnectivityState,
): String = when {
    connection.isConnecting -> when (connection.activeAction) {
        ServerConnectionAction.Connect, null -> "Connecting"
        ServerConnectionAction.Sync,
        ServerConnectionAction.FastSync,
        ServerConnectionAction.FullSync -> "Syncing"
    }
    !connection.hasStoredSession -> "Not connected"
    else -> when (connectivity) {
        SourceConnectivityState.Offline -> "Connected · offline"
        SourceConnectivityState.Checking -> "Connected · checking"
        SourceConnectivityState.Online,
        SourceConnectivityState.Unknown -> "Connected"
    }
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
