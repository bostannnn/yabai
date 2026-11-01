package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction

/** The server connection of [sourceType]; null for Local. */
internal fun AppUiState.remoteConnection(sourceType: SourceType): RemoteSourceConnectionUiState? = when (sourceType) {
    SourceType.KAVITA -> kavitaConnection
    SourceType.STUMP -> stumpConnection
    SourceType.GRIMMORY -> grimmoryConnection
    SourceType.LOCAL -> null
}

/**
 * Refresh library in the bar's "•••": on a connected server, off while it connects or syncs, and
 * the ring on "•••" while any sync of its library runs (the refresh, by hand or by itself, or
 * Settings' Sync now).
 */
internal fun AppUiState.libraryRefreshTopBarState(): LibraryRefreshTopBarState {
    val connection = remoteConnection(selectedSource)
        ?.takeIf(RemoteSourceConnectionUiState::hasStoredSession)
        ?: return LibraryRefreshTopBarState()
    return LibraryRefreshTopBarState(
        isVisible = true,
        isEnabled = !connection.isConnecting,
        isRefreshing = connection.isConnecting && connection.activeAction != null &&
            connection.activeAction != ServerConnectionAction.Connect,
    )
}
