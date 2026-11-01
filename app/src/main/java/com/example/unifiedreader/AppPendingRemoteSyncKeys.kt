package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalSnapshot

// The keys AppPendingProgressFlushCoordinator watches: one per upload waiting, so a change to what
// waits (not to anything else in the progress or the journal) starts a flush.

internal fun pendingProgressKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
): List<PendingRemoteSyncKey> = progressByItemId.values
    .filter(ReaderProgressSnapshot::isPendingRemoteSync)
    .sortedBy(ReaderProgressSnapshot::itemId)
    .map { progress ->
        PendingRemoteSyncKey(
            artifact = RemoteSyncArtifact.PROGRESS,
            itemId = progress.itemId,
            syncState = progress.syncState.name,
            updatedAtEpochMillis = progress.updatedAtEpochMillis,
        )
    }

internal fun pendingJournalKeys(
    snapshot: JournalSnapshot,
): List<PendingRemoteSyncKey> =
    snapshot.entries.mapNotNull(ItemJournalEntry::pendingRemoteSyncKey) +
        snapshot.sessions.mapNotNull(JournalReadingSession::pendingRemoteSyncKey)

internal fun ItemJournalEntry.pendingRemoteSyncKey(): PendingRemoteSyncKey? =
    syncState.takeIf(SyncState::isPendingSync)?.let { state ->
        PendingRemoteSyncKey(
            artifact = RemoteSyncArtifact.JOURNAL_ENTRY,
            itemId = itemId,
            syncState = state.name,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

internal fun JournalReadingSession.pendingRemoteSyncKey(): PendingRemoteSyncKey? =
    syncState.takeIf(SyncState::isPendingSync)?.let { state ->
        PendingRemoteSyncKey(
            artifact = RemoteSyncArtifact.READING_SESSION,
            itemId = itemId,
            syncState = state.name,
            updatedAtEpochMillis = endedAtEpochMillis,
        )
    }

internal fun pendingSyncKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
    journalSnapshot: JournalSnapshot,
): List<PendingRemoteSyncKey> = (
    pendingProgressKeys(progressByItemId) + pendingJournalKeys(journalSnapshot)
    ).sortedBy(PendingRemoteSyncKey::sortKey)

internal fun SyncState.isPendingSync(): Boolean =
    toPendingRemoteSyncItemState() != null

internal data class PendingRemoteSyncKey(
    val artifact: RemoteSyncArtifact,
    val itemId: String,
    val syncState: String,
    val updatedAtEpochMillis: Long?,
) {
    val sortKey: String = "${artifact.name}:$itemId:${updatedAtEpochMillis ?: 0L}:$syncState"
}

internal fun List<PendingRemoteSyncKey>.toAutoFlushKeys(): List<AppRemoteArtifactAutoFlushKey> =
    map { key ->
        AppRemoteArtifactAutoFlushKey(
            artifact = key.artifact,
            itemId = key.itemId,
            updatedAtEpochMillis = key.updatedAtEpochMillis,
        )
    }.distinct()

internal fun List<PendingRemoteSyncKey>.resolvableIn(
    catalogItems: List<ReadableItem>,
): Set<String> {
    if (isEmpty() || catalogItems.isEmpty()) return emptySet()
    val pendingItemIds = mapTo(mutableSetOf(), PendingRemoteSyncKey::itemId)
    return catalogItems.asSequence()
        .map(ReadableItem::id)
        .filter(pendingItemIds::contains)
        .toSet()
}
