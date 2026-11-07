package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLibraryAutoRefreshCoordinatorTest {
    private var now = 10 * HOUR
    private val syncedAt = mutableMapOf<SourceType, Long>()
    private val coordinator = AppLibraryAutoRefreshCoordinator(
        lastSyncedAt = { source -> syncedAt[source] },
        nowEpochMillis = { now },
    )
    private var refreshes = 0

    private fun show(state: AppUiState) = coordinator.refreshIfDue(state) { refreshes += 1 }

    @Test
    fun `refreshes once the server last synced 15 minutes ago or more`() {
        syncedAt[SourceType.GRIMMORY] = now - 14 * MINUTE
        show(grimmory())
        assertEquals(0, refreshes)

        now += 2 * MINUTE
        show(grimmory())
        assertEquals(1, refreshes)
    }

    @Test
    fun `a library never synced here refreshes, and not again until the interval passed`() {
        show(kavita())
        show(kavita())
        assertEquals(1, refreshes)

        // That try failed (no sync time was written): the next one waits the same 15 minutes.
        now += 10 * MINUTE
        show(kavita())
        assertEquals(1, refreshes)
        now += 6 * MINUTE
        show(kavita())
        assertEquals(2, refreshes)
    }

    @Test
    fun `a sync after the try restarts the wait`() {
        show(grimmory())
        now += 20 * MINUTE
        syncedAt[SourceType.GRIMMORY] = now - MINUTE
        show(grimmory())
        assertEquals(1, refreshes)
    }

    @Test
    fun `stump and local wait for a tap`() {
        show(AppUiState(selectedSource = SourceType.STUMP, stumpConnection = RemoteSourceConnectionUiState(
            source = SourceType.STUMP,
            hasStoredSession = true,
        )))
        show(AppUiState(selectedSource = SourceType.LOCAL))
        assertEquals(0, refreshes)
    }

    @Test
    fun `an offline, unconnected or busy server is left alone`() {
        show(grimmory().copy(sourceConnectivityBySource = mapOf(SourceType.GRIMMORY to SourceConnectivityState.Offline)))
        show(grimmory().copy(grimmoryConnection = RemoteSourceConnectionUiState(source = SourceType.GRIMMORY, hasStoredSession = false)))
        show(
            grimmory().copy(
                grimmoryConnection = RemoteSourceConnectionUiState(
                    source = SourceType.GRIMMORY,
                    hasStoredSession = true,
                    isConnecting = true,
                    activeAction = ServerConnectionAction.Sync,
                ),
            ),
        )
        assertEquals(0, refreshes)

        // Leaving it alone is not a try: it refreshes as soon as it can.
        show(grimmory())
        assertEquals(1, refreshes)
    }

    @Test
    fun `each server keeps its own wait`() {
        show(grimmory())
        show(kavita())
        assertEquals(2, refreshes)
    }

    private fun grimmory() = AppUiState(
        selectedSource = SourceType.GRIMMORY,
        grimmoryConnection = RemoteSourceConnectionUiState(source = SourceType.GRIMMORY, hasStoredSession = true),
    )

    private fun kavita() = AppUiState(
        selectedSource = SourceType.KAVITA,
        kavitaConnection = RemoteSourceConnectionUiState(source = SourceType.KAVITA, hasStoredSession = true),
    )

    private companion object {
        const val MINUTE = 60 * 1000L
        const val HOUR = 60 * MINUTE
    }
}
