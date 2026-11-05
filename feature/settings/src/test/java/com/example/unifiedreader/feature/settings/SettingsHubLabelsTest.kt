package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.ui.components.AppStatusTone
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHubLabelsTest {
    @Test
    fun `source status follows the stored session and connectivity`() {
        val connected = KavitaConnectionUiState(hasStoredSession = true)
        assertEquals(
            SourceHubStatus("Not connected", AppStatusTone.Neutral),
            remoteSourceHubStatus(KavitaConnectionUiState(), SourceConnectivityState.Online, lastSyncedAtEpochMillis = null),
        )
        assertEquals(
            SourceHubStatus("Connected", AppStatusTone.Good),
            remoteSourceHubStatus(connected, SourceConnectivityState.Unknown, lastSyncedAtEpochMillis = null),
        )
        assertEquals(
            SourceHubStatus("Offline", AppStatusTone.Neutral),
            remoteSourceHubStatus(connected, SourceConnectivityState.Offline, lastSyncedAtEpochMillis = NOW),
        )
        assertEquals(
            SourceHubStatus("Checking", AppStatusTone.Neutral),
            remoteSourceHubStatus(connected, SourceConnectivityState.Checking, lastSyncedAtEpochMillis = NOW),
        )
    }

    @Test
    fun `a connected source says when it last synced`() {
        val connected = KavitaConnectionUiState(hasStoredSession = true)
        val today = Calendar.getInstance(Locale.US).apply { timeInMillis = NOW; set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 12) }
        assertEquals(
            SourceHubStatus("Synced 09:12", AppStatusTone.Good),
            remoteSourceHubStatus(connected, SourceConnectivityState.Online, today.timeInMillis, nowEpochMillis = NOW),
        )
        assertEquals(
            "Synced yesterday",
            remoteSourceHubStatus(connected, SourceConnectivityState.Online, today.timeInMillis - DAY, nowEpochMillis = NOW).label,
        )
    }

    @Test
    fun `an active action wins over the stored state`() {
        assertEquals(
            "Connecting",
            remoteSourceHubStatus(
                StumpConnectionUiState(isConnecting = true, activeAction = ServerConnectionAction.Connect),
                SourceConnectivityState.Online,
                lastSyncedAtEpochMillis = null,
            ).label,
        )
        assertEquals(
            "Syncing",
            remoteSourceHubStatus(
                StumpConnectionUiState(hasStoredSession = true, isConnecting = true, activeAction = ServerConnectionAction.FastSync),
                SourceConnectivityState.Online,
                lastSyncedAtEpochMillis = NOW,
            ).label,
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
    fun `books no longer in the library show only when nothing waits`() {
        val gone = listOf(
            goneItem("grimmory:item:2", "Moths of Lisbon", "Progress"),
            goneItem("grimmory:item:2", "Moths of Lisbon", "Reading session"),
            goneItem("grimmory:item:5", "A Field Guide to Fog", "Journal entry"),
        )
        assertEquals("2 books no longer in your library", SyncSettingsUiState(goneItems = gone).summaryLabel())
        assertEquals("1 book no longer in your library", SyncSettingsUiState(goneItems = gone.take(2)).summaryLabel())
        assertEquals("1 item waiting to upload", SyncSettingsUiState(pendingCount = 1, queuedCount = 1, goneItems = gone).summaryLabel())
    }

    @Test
    fun `a gone book is one row with what it held`() {
        val rows = SyncSettingsUiState(
            goneItems = listOf(
                goneItem("grimmory:item:2", "Moths of Lisbon", "Progress"),
                goneItem("grimmory:item:2", "Moths of Lisbon", "Reading session"),
                goneItem("grimmory:item:2", "Moths of Lisbon", "Reading session"),
                goneItem("grimmory:item:5", "A Field Guide to Fog", "Journal entry"),
            ),
        ).goneRows()

        assertEquals(
            listOf(
                SyncQueueRow("Moths of Lisbon", "Grimmory · Progress, 2 reading sessions", failed = false),
                SyncQueueRow("A Field Guide to Fog", "Grimmory · Journal entry", failed = false),
            ),
            rows,
        )
    }

    @Test
    fun `drawer order summary is the order itself`() {
        assertEquals("Stump, Kavita", drawerOrderSummary(listOf(SourceType.STUMP, SourceType.KAVITA)))
        assertEquals("The order of your sources", drawerOrderSummary(emptyList()))
    }

    @Test
    fun `filter chips summary counts each library's filters in the sources' order`() {
        val kavitaDefault = resolveLibraryFilterPanels(SourceType.KAVITA, preferredPanels = null).size
        val local = resolveLibraryFilterPanels(SourceType.LOCAL, preferredPanels = null).take(2)
        assertEquals(
            "$kavitaDefault in Kavita, 2 in Local",
            filterChipsSummary(listOf(SourceType.KAVITA, SourceType.LOCAL), mapOf(SourceType.LOCAL to local)),
        )
    }

    @Test
    fun `failed covers read as one row`() {
        assertEquals("1 cover didn't download", failedCoversTitle(1))
        assertEquals("3 covers didn't download", failedCoversTitle(3))
        assertEquals("Star Courier #2", failedCoversLine(listOf("Star Courier #2", "Star Courier #2")))
        assertEquals(
            "Star Courier #2, Iron Garden #3 and 1 more",
            failedCoversLine(listOf("Star Courier #2", "Iron Garden #3", "Harbor Lights #2")),
        )
    }

    @Test
    fun `the server row names the account and the backup address`() {
        assertEquals(
            "andrew · backup 100.84.32.88:5000",
            serverAccountLine(KavitaConnectionUiState(username = "andrew", backupBaseUrl = "http://100.84.32.88:5000/")),
        )
        assertEquals(null, serverAccountLine(KavitaConnectionUiState(username = "", backupBaseUrl = "")))
    }
}

private const val DAY = 24L * 60 * 60 * 1000

// A fixed noon, so "today" and "yesterday" never straddle midnight.
private val NOW = Calendar.getInstance(Locale.US).apply {
    set(2026, Calendar.NOVEMBER, 6, 12, 0, 0)
}.timeInMillis

private fun goneItem(bookKey: String, title: String, artifact: String) = SyncSettingsGoneItem(
    bookKey = bookKey,
    title = title,
    sourceLabel = "Grimmory",
    artifactLabel = artifact,
)
