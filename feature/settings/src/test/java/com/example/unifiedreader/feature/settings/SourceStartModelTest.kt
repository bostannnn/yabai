package com.example.unifiedreader.feature.settings

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceStartModelTest {
    private val now = Calendar.getInstance(Locale.US).apply { set(2026, Calendar.OCTOBER, 23, 15, 0) }.timeInMillis

    @Test
    fun `a running sync shows the step it is on`() {
        val connection = KavitaConnectionUiState(
            hasStoredSession = true,
            isConnecting = true,
            activeAction = ServerConnectionAction.Sync,
            syncProgressLabel = "Loading Kavita series...",
        )

        assertEquals(
            FirstSyncState(title = "Getting your library", detail = "Loading Kavita series...", running = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = null, nowEpochMillis = now),
        )
    }

    @Test
    fun `signed in before the sync starts already reads as getting the library`() {
        val connection = KavitaConnectionUiState(hasStoredSession = true)

        assertEquals(
            FirstSyncState(title = "Getting your library", detail = null, running = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = null, nowEpochMillis = now),
        )
    }

    @Test
    fun `a failed sync shows the server's answer`() {
        val connection = StumpConnectionUiState(hasStoredSession = true, isError = true, message = "Stump sync failed: timeout")

        assertEquals(
            FirstSyncState(title = "The library didn't sync", detail = "Stump sync failed: timeout", running = false, failed = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = null, nowEpochMillis = now),
        )
    }

    @Test
    fun `a finished sync says when`() {
        val syncedAt = Calendar.getInstance(Locale.US).apply { set(2026, Calendar.OCTOBER, 23, 10, 42) }.timeInMillis

        assertEquals(
            FirstSyncState(title = "Your library is here", detail = "Synced today at 10:42", running = false),
            firstSyncState(GrimmoryConnectionUiState(hasStoredSession = true), lastSyncedAtEpochMillis = syncedAt, nowEpochMillis = now),
        )
    }
}
