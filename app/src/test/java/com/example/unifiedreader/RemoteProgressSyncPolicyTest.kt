package com.example.unifiedreader

import com.example.unifiedreader.core.model.LocatorType
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
}
