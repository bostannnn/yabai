package com.example.unifiedreader

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteProgressSyncPolicyTest {
    @Test
    fun `missing remote progress keeps dirty local progress by default`() {
        val local = ReaderProgressSnapshot(
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
        val local = ReaderProgressSnapshot(
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
    fun `older remote progress does not overwrite clean local progress`() {
        val local = ReaderProgressSnapshot(
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
        val local = ReaderProgressSnapshot(
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
                locator = remote.toReaderLocator(),
                isFinished = false,
                updatedAtEpochMillis = 200L,
            ),
            decision,
        )
    }
}
