package com.example.unifiedreader

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteProgressSyncPolicyTest {
    @Test
    fun `local position that refines the remote one is kept even when the remote is newer`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.CFI,
            cfi = "{}",
            syncState = SyncState.CLEAN,
            updatedAtEpochMillis = 10L,
        )
        val remote = ReadingProgress(
            itemId = "book-1",
            locatorType = LocatorType.PAGE,
            page = 3,
            updatedAtEpochMillis = 20L,
        )

        val refined = decideRemoteProgressSync(
            local = local,
            remote = remote,
            nowEpochMillis = 30L,
            localRefinesRemote = { _, _ -> true },
        )
        val cleared = decideRemoteProgressSync(
            local = local,
            remote = null,
            nowEpochMillis = 30L,
            missingRemoteClearsLocal = true,
            localRefinesRemote = { _, _ -> true },
        )

        assertEquals(RemoteProgressSyncDecision.KeepLocal, refined)
        assertEquals(RemoteProgressSyncDecision.KeepLocal, cleared)
    }

    @Test
    fun `a pending local save gives way only to a server copy newer by more than the clock tolerance`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.PAGE,
            page = 12,
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 1_000_000L,
        )
        fun remoteAt(updatedAtEpochMillis: Long) = ReadingProgress(
            itemId = "book-1",
            locatorType = LocatorType.PAGE,
            page = 30,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

        // The server's stamp of this device's own last push, a minute ahead of the next save.
        val ownPush = decideRemoteProgressSync(local, remoteAt(1_000_000L + 60_000L), nowEpochMillis = 0L)
        val readElsewhere = decideRemoteProgressSync(
            local = local,
            remote = remoteAt(1_000_000L + PENDING_LOCAL_PROGRESS_CLOCK_TOLERANCE_MILLIS + 1L),
            nowEpochMillis = 0L,
        )
        val overCleanLocal = decideRemoteProgressSync(
            local = local.copy(syncState = SyncState.CLEAN),
            remote = remoteAt(1_000_000L + 60_000L),
            nowEpochMillis = 0L,
        )

        assertEquals(RemoteProgressSyncDecision.KeepLocal, ownPush)
        assertEquals(30, (readElsewhere as RemoteProgressSyncDecision.ApplyRemote).locator.page)
        assertEquals(30, (overCleanLocal as RemoteProgressSyncDecision.ApplyRemote).locator.page)
    }

    @Test
    fun `missing remote progress keeps dirty local progress by default`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.55,
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 10L,
        )

        val decision = decideRemoteProgressSync(
            local = local,
            remote = null,
            nowEpochMillis = 20L,
        )

        assertEquals(RemoteProgressSyncDecision.KeepLocal, decision)
    }

    @Test
    fun `missing remote progress clears clean local progress only when explicitly requested`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.55,
            syncState = SyncState.CLEAN,
            updatedAtEpochMillis = 10L,
        )

        val decision = decideRemoteProgressSync(
            local = local,
            remote = null,
            nowEpochMillis = 20L,
            missingRemoteClearsLocal = true,
        )

        assertEquals(RemoteProgressSyncDecision.ClearLocal, decision)
    }

    @Test
    fun `explicit remote reset keeps dirty local progress when remote clear override is disabled`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.55,
            syncState = SyncState.DIRTY,
            updatedAtEpochMillis = 10L,
        )

        val decision = decideRemoteProgressSync(
            local = local,
            remote = null,
            nowEpochMillis = 20L,
            missingRemoteClearsLocal = true,
            remoteClearOverridesLocal = false,
        )

        assertEquals(RemoteProgressSyncDecision.KeepLocal, decision)
    }

    @Test
    fun `older remote progress does not overwrite clean local progress`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.80,
            syncState = SyncState.CLEAN,
            updatedAtEpochMillis = 200L,
        )
        val remote = ReadingProgress(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.35,
            updatedAtEpochMillis = 100L,
        )

        val decision = decideRemoteProgressSync(
            local = local,
            remote = remote,
            nowEpochMillis = 250L,
        )

        assertEquals(RemoteProgressSyncDecision.KeepLocal, decision)
    }

    @Test
    fun `newer remote progress overwrites clean local progress`() {
        val local = ProgressSyncSnapshot(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.35,
            syncState = SyncState.CLEAN,
            updatedAtEpochMillis = 100L,
        )
        val remote = ReadingProgress(
            itemId = "book-1",
            locatorType = LocatorType.PERCENT,
            percent = 0.80,
            updatedAtEpochMillis = 200L,
        )

        val decision = decideRemoteProgressSync(
            local = local,
            remote = remote,
            nowEpochMillis = 250L,
        )

        assertEquals(
            RemoteProgressSyncDecision.ApplyRemote(
                locator = remote.toProgressSyncLocator(),
                isFinished = false,
                updatedAtEpochMillis = 200L,
            ),
            decision,
        )
    }
}
