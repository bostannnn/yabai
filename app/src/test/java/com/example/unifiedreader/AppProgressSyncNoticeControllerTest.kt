package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProgressSyncNoticeControllerTest {
    @Test
    fun `offline queued sync failure does not show unable to sync notice`() {
        val controller = AppProgressSyncNoticeController()
        var state = AppUiState()

        controller.reportFailures(
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = SourceType.KAVITA,
                    throwable = RemoteSourceUnavailableException(SourceType.KAVITA),
                    artifact = RemoteSyncArtifact.PROGRESS,
                ),
            ),
            updateState = { transform -> state = transform(state) },
        )

        assertNull(state.progressSyncNotice)
    }

    @Test
    fun `real online attempt failure shows error notice`() {
        val controller = AppProgressSyncNoticeController()
        var state = AppUiState()

        controller.reportFailures(
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = SourceType.KAVITA,
                    throwable = IllegalStateException("Progress rejected"),
                    artifact = RemoteSyncArtifact.PROGRESS,
                ),
            ),
            updateState = { transform -> state = transform(state) },
        )

        assertEquals(
            "Unable to sync Kavita progress: Progress rejected",
            state.progressSyncNotice?.message,
        )
        assertTrue(state.progressSyncNotice?.isError == true)
    }
}
