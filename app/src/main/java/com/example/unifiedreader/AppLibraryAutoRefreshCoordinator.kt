package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import javax.inject.Inject

/**
 * Refreshes the library on screen by itself, so books added on the server show up without a trip
 * to Settings. The app calls [refreshIfDue] each time the library shows (the app opening or coming
 * back, another source picked, a reader or a sheet closed); it refreshes once the server last
 * synced [LIBRARY_AUTO_REFRESH_AFTER_MILLIS] ago or more, and not sooner after a try that failed.
 *
 * Kavita and Grimmory only: their refresh saves just the books that changed. Stump's loads every
 * series again, so it waits for Refresh library. A server that is offline, not connected or
 * already syncing is left alone.
 */
class AppLibraryAutoRefreshCoordinator internal constructor(
    private val lastSyncedAt: (SourceType) -> Long?,
    private val nowEpochMillis: () -> Long,
) {
    @Inject constructor(sourceSyncTimes: SourceSyncTimeStore) : this(
        lastSyncedAt = sourceSyncTimes::lastSyncedAt,
        nowEpochMillis = System::currentTimeMillis,
    )

    private val lastTriedAt = mutableMapOf<SourceType, Long>()

    fun refreshIfDue(
        state: AppUiState,
        refresh: () -> Unit,
    ) {
        val source = state.selectedSource
        if (source !in AutoRefreshedSources) return
        val connection = state.remoteConnection(source) ?: return
        if (!connection.hasStoredSession || connection.isConnecting) return
        if (state.selectedSourceConnectivity == SourceConnectivityState.Offline) return
        val now = nowEpochMillis()
        val lastActivity = listOfNotNull(lastSyncedAt(source), lastTriedAt[source]).maxOrNull()
        if (lastActivity != null && now - lastActivity < LIBRARY_AUTO_REFRESH_AFTER_MILLIS) return
        lastTriedAt[source] = now
        refresh()
    }
}

/** How long after a sync the library refreshes again by itself. */
internal const val LIBRARY_AUTO_REFRESH_AFTER_MILLIS = 15 * 60 * 1000L

private val AutoRefreshedSources = setOf(SourceType.KAVITA, SourceType.GRIMMORY)
