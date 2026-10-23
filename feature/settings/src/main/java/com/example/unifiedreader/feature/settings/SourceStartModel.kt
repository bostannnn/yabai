package com.example.unifiedreader.feature.settings

/** What the connect page says about a server's first sync. */
internal data class FirstSyncState(
    val title: String,
    val detail: String?,
    val running: Boolean,
    val failed: Boolean = false,
)

/**
 * The first sync after signing in: running with the step it is on, failed with the server's
 * answer, or done and when. Signed in with no sync yet counts as running, since the page starts
 * one right away.
 */
internal fun firstSyncState(
    connection: RemoteSourceConnectionUiState,
    lastSyncedAtEpochMillis: Long?,
    nowEpochMillis: Long = System.currentTimeMillis(),
): FirstSyncState {
    val syncing = connection.isConnecting && connection.activeAction != ServerConnectionAction.Connect
    return when {
        syncing -> FirstSyncState(title = "Getting your library", detail = connection.syncProgressLabel, running = true)
        connection.isError && !connection.message.isNullOrBlank() ->
            FirstSyncState(title = "The library didn't sync", detail = connection.message, running = false, failed = true)
        lastSyncedAtEpochMillis == null -> FirstSyncState(title = "Getting your library", detail = null, running = true)
        else -> FirstSyncState(
            title = "Your library is here",
            detail = "Synced ${formatSettingsMoment(lastSyncedAtEpochMillis, nowEpochMillis)}",
            running = false,
        )
    }
}
