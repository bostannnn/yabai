package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHubLabelsTest {
    @Test
    fun `source status follows the stored session and connectivity`() {
        assertEquals(
            "Not connected",
            remoteSourceStatusLabel(KavitaConnectionUiState(), SourceConnectivityState.Online),
        )
        assertEquals(
            "Connected",
            remoteSourceStatusLabel(KavitaConnectionUiState(hasStoredSession = true), SourceConnectivityState.Online),
        )
        assertEquals(
            "Connected",
            remoteSourceStatusLabel(KavitaConnectionUiState(hasStoredSession = true), SourceConnectivityState.Unknown),
        )
        assertEquals(
            "Connected · offline",
            remoteSourceStatusLabel(KavitaConnectionUiState(hasStoredSession = true), SourceConnectivityState.Offline),
        )
    }

    @Test
    fun `an active action wins over the stored state`() {
        assertEquals(
            "Connecting",
            remoteSourceStatusLabel(
                StumpConnectionUiState(isConnecting = true, activeAction = ServerConnectionAction.Connect),
                SourceConnectivityState.Online,
            ),
        )
        assertEquals(
            "Syncing",
            remoteSourceStatusLabel(
                StumpConnectionUiState(hasStoredSession = true, isConnecting = true, activeAction = ServerConnectionAction.FastSync),
                SourceConnectivityState.Online,
            ),
        )
    }

    @Test
    fun `sync summary reads as a status line`() {
        assertEquals("Up to date", SyncSettingsUiState().summaryLabel())
        assertEquals("1 item waiting to upload", SyncSettingsUiState(pendingCount = 1, queuedCount = 1).summaryLabel())
        assertEquals("3 items waiting to upload", SyncSettingsUiState(pendingCount = 3, failedCount = 1, queuedCount = 2).summaryLabel())
        assertEquals("1 failed, 2 queued", SyncSettingsUiState(pendingCount = 3, failedCount = 1, queuedCount = 2).detailLabel())
    }

    @Test
    fun `drawer order summary names the startup source`() {
        assertEquals("Stump opens on launch", drawerOrderSummary(listOf(SourceType.STUMP, SourceType.KAVITA)))
        assertEquals("Sources in the drawer", drawerOrderSummary(emptyList()))
    }
}
