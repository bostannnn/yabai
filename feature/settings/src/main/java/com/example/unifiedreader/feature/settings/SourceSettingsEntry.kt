package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.ui.components.AppStatusTone

/** What the settings UI needs to know about one remote server's connection. */
interface RemoteSourceConnectionUiState {
    val displayName: String
    val baseUrl: String
    val backupBaseUrl: String
    val username: String
    val password: String
    val hasStoredSession: Boolean
    /** False until the stored session (or its absence) has loaded after launch. */
    val isSessionKnown: Boolean
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
