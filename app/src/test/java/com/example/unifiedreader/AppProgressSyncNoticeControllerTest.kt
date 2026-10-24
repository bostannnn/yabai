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
            "Couldn't send your progress to Kavita",
            state.progressSyncNotice?.message,
        )
        assertEquals("Progress rejected", state.progressSyncNotice?.reason)
        assertTrue(state.progressSyncNotice?.canRetry == true)
        assertTrue(state.progressSyncNotice?.isError == true)
    }

    @Test
    fun `failures from several sources name them all and offer a retry`() {
        val controller = AppProgressSyncNoticeController()
        var state = AppUiState()

        controller.reportFailures(
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = SourceType.KAVITA,
                    throwable = IllegalStateException("Progress rejected"),
                    artifact = RemoteSyncArtifact.PROGRESS,
                ),
                SourceProgressSyncFailure(
                    sourceType = SourceType.GRIMMORY,
                    throwable = IllegalStateException("Progress rejected"),
                    artifact = RemoteSyncArtifact.JOURNAL_ENTRY,
                ),
            ),
            updateState = { transform -> state = transform(state) },
        )

        assertEquals("Couldn't send your progress to Kavita and Grimmory", state.progressSyncNotice?.message)
        assertTrue(state.progressSyncNotice?.canRetry == true)
    }

    @Test
    fun `an unreachable source keeps its own message without a retry`() {
        val controller = AppProgressSyncNoticeController()
        var state = AppUiState()

        controller.reportFailures(
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = SourceType.KAVITA,
                    throwable = RemoteSourceUnavailableException(SourceType.KAVITA),
                    artifact = null,
                ),
            ),
            updateState = { transform -> state = transform(state) },
        )

        assertEquals(false, state.progressSyncNotice?.canRetry)
        assertNull(state.progressSyncNotice?.reason)
    }
}
