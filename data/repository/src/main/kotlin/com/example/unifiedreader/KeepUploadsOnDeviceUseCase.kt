package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject

/**
 * Stops the uploads waiting for books their server no longer has, and keeps what they hold on this
 * device. Nothing is deleted: the progress stays (as uploaded, so nothing retries it), and the
 * journal entries and reading sessions turn [SyncState.LOCAL_ONLY], which no upload picks up and no
 * server refresh replaces, so the journal keeps them.
 */
class KeepUploadsOnDeviceUseCase @Inject constructor(
    private val progressSyncStore: ProgressSyncStore,
    private val journalRepository: JournalRepository,
) {
    suspend operator fun invoke(
        itemIds: Set<String>,
    ) {
        if (itemIds.isEmpty()) return
        progressSyncStore.currentProgressByItemId().values
            .filter { progress -> progress.itemId in itemIds && progress.syncState.waitsToUpload() }
            .forEach { progress ->
                progressSyncStore.replaceProgressIfCurrent(
                    expected = progress,
                    replacement = progress.copy(syncState = SyncState.CLEAN),
                )
            }
        SourceType.entries.filter { sourceType -> sourceType != SourceType.LOCAL }.forEach { sourceType ->
            journalRepository.getDirtyJournalEntriesForSource(sourceType)
                .filter { entry -> entry.itemId in itemIds }
                .forEach { entry -> journalRepository.markJournalEntrySyncState(entry.itemId, SyncState.LOCAL_ONLY) }
            journalRepository.getDirtyReadingSessionsForSource(sourceType)
                .filter { session -> session.itemId in itemIds }
                .forEach { session -> journalRepository.markReadingSessionSyncState(session.id, SyncState.LOCAL_ONLY) }
        }
    }
}

private fun SyncState.waitsToUpload(): Boolean =
    this == SyncState.DIRTY || this == SyncState.FAILED
