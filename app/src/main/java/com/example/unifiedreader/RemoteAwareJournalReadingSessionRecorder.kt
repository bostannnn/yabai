package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.JournalReadingSessionInput
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import javax.inject.Inject

class RemoteAwareJournalReadingSessionRecorder @Inject constructor(
    private val delegate: JournalReadingSessionRecorder,
    @Suppress("UNUSED_PARAMETER")
    remoteItemLocalAvailability: RemoteItemLocalAvailability,
    @Suppress("UNUSED_PARAMETER")
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) : JournalReadingSessionRecorder {
    override suspend fun recordReadingSession(
        input: JournalReadingSessionInput,
    ) {
        delegate.recordReadingSession(
            input.copy(
                syncState = input.syncState ?: input.remoteReadingSessionSyncState(),
            ),
        )
    }

    private fun JournalReadingSessionInput.remoteReadingSessionSyncState(): SyncState = when (item.sourceType) {
        SourceType.GRIMMORY -> SyncState.DIRTY
        SourceType.KAVITA -> SyncState.CLEAN
        SourceType.STUMP,
        SourceType.LOCAL -> SyncState.CLEAN
    }
}
