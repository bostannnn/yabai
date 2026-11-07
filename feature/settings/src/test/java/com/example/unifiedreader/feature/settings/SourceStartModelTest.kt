package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.core.model.SourceType
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceStartModelTest {
    private val now = Calendar.getInstance(Locale.US).apply { set(2026, Calendar.OCTOBER, 23, 15, 0) }.timeInMillis

    @Test
    fun `a running sync shows the step it is on`() {
        val connection = RemoteSourceConnectionUiState(
            source = SourceType.KAVITA,
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
        val connection = RemoteSourceConnectionUiState(source = SourceType.KAVITA, hasStoredSession = true)

        assertEquals(
            FirstSyncState(title = "Getting your library", detail = null, running = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = null, startingSync = true, nowEpochMillis = now),
        )
    }

    @Test
    fun `signed in, never synced and nothing running offers the sync instead of a bar that never fills`() {
        // The app was closed before the first sync, or after one that failed: the error didn't survive.
        val connection = RemoteSourceConnectionUiState(source = SourceType.KAVITA, hasStoredSession = true)

        assertEquals(
            FirstSyncState(title = "Your library hasn't synced yet", detail = null, running = false, notStarted = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = null, nowEpochMillis = now),
        )
    }

    @Test
    fun `a failed sync shows the server's answer`() {
        val connection = RemoteSourceConnectionUiState(
            source = SourceType.STUMP,
            hasStoredSession = true,
            isError = true,
            message = "Stump sync failed: timeout",
        )

        assertEquals(
            FirstSyncState(title = "The library didn't sync", detail = "Stump sync failed: timeout", running = false, failed = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = null, nowEpochMillis = now),
        )
    }

    @Test
    fun `a sync that saved the library but couldn't upload everything reads as here, with the warning`() {
        val syncedAt = Calendar.getInstance(Locale.US).apply { set(2026, Calendar.OCTOBER, 23, 14, 58) }.timeInMillis
        val connection = RemoteSourceConnectionUiState(
            source = SourceType.GRIMMORY,
            hasStoredSession = true,
            isError = true,
            message = "Grimmory library synced. Some changes didn't upload to Grimmory and will go with the next sync (timeout).",
        )

        assertEquals(
            FirstSyncState(title = "Your library is here", detail = connection.message, running = false, warning = true),
            firstSyncState(connection, lastSyncedAtEpochMillis = syncedAt, nowEpochMillis = now),
        )
    }

    @Test
    fun `a finished sync says when`() {
        val syncedAt = Calendar.getInstance(Locale.US).apply { set(2026, Calendar.OCTOBER, 23, 10, 42) }.timeInMillis

        assertEquals(
            FirstSyncState(title = "Your library is here", detail = "Synced today at 10:42", running = false),
            firstSyncState(RemoteSourceConnectionUiState(
                source = SourceType.GRIMMORY,
                hasStoredSession = true,
            ), lastSyncedAtEpochMillis = syncedAt, nowEpochMillis = now),
        )
    }
}
