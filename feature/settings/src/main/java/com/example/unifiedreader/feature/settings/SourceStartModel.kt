package com.example.unifiedreader.feature.settings

/** What the connect page says about a server's first sync. */
internal data class FirstSyncState(
    val title: String,
    val detail: String?,
    val running: Boolean,
    val failed: Boolean = false,
    // The library is here, but the last sync left something to say: what didn't upload, or what went wrong.
    val warning: Boolean = false,
)

/**
 * The first sync after signing in: running with the step it is on, failed with the server's
 * answer, or done and when. Signed in with no sync yet counts as running, since the page starts
 * one right away. Once a sync has saved the library it is here, even when that sync, or a later one,
 * reported something (changes that didn't upload): the message shows as a warning, not as a failure.
 */
internal fun firstSyncState(
    connection: RemoteSourceConnectionUiState,
    lastSyncedAtEpochMillis: Long?,
    nowEpochMillis: Long = System.currentTimeMillis(),
): FirstSyncState {
    val syncing = connection.isConnecting && connection.activeAction != ServerConnectionAction.Connect
    return when {
        syncing -> FirstSyncState(title = "Getting your library", detail = connection.syncProgressLabel, running = true)
        lastSyncedAtEpochMillis == null && connection.isError && !connection.message.isNullOrBlank() ->
            FirstSyncState(title = "The library didn't sync", detail = connection.message, running = false, failed = true)
        lastSyncedAtEpochMillis == null -> FirstSyncState(title = "Getting your library", detail = null, running = true)
        connection.isError && !connection.message.isNullOrBlank() ->
            FirstSyncState(title = "Your library is here", detail = connection.message, running = false, warning = true)
        else -> FirstSyncState(
            title = "Your library is here",
            detail = "Synced ${formatSettingsMoment(lastSyncedAtEpochMillis, nowEpochMillis)}",
            running = false,
        )
    }
}
