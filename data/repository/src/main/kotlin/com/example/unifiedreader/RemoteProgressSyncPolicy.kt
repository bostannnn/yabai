package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncLocator
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.progress.ProgressSyncStore

sealed interface RemoteProgressSyncDecision {
    data object KeepLocal : RemoteProgressSyncDecision

    data object ClearLocal : RemoteProgressSyncDecision

    data class ApplyRemote(
        val locator: ProgressSyncLocator,
        val isFinished: Boolean,
        val updatedAtEpochMillis: Long,
    ) : RemoteProgressSyncDecision
}

fun decideRemoteProgressSync(
    local: ProgressSyncSnapshot?,
    remote: ReadingProgress?,
    nowEpochMillis: Long,
    missingRemoteClearsLocal: Boolean = false,
    remoteClearOverridesLocal: Boolean = true,
    localRefinesRemote: (local: ProgressSyncSnapshot, remote: ReadingProgress?) -> Boolean = { _, _ -> false },
): RemoteProgressSyncDecision {
    // A source that stores positions coarsely can report the local position back less precisely.
    if (local != null && localRefinesRemote(local, remote)) {
        return RemoteProgressSyncDecision.KeepLocal
    }
    if (remote == null) {
        if (!missingRemoteClearsLocal) {
            return RemoteProgressSyncDecision.KeepLocal
        }
        return when {
            local == null -> RemoteProgressSyncDecision.KeepLocal
            local.syncState == SyncState.CLEAN -> RemoteProgressSyncDecision.ClearLocal
            remoteClearOverridesLocal -> RemoteProgressSyncDecision.ClearLocal
            else -> RemoteProgressSyncDecision.KeepLocal
        }
    }

    if (local == null) {
        return remote.toApplyRemoteDecision(
            updatedAtEpochMillis = remote.updatedAtEpochMillis ?: nowEpochMillis,
        )
    }

    val remoteUpdatedAtEpochMillis = remote.updatedAtEpochMillis
    if (remoteUpdatedAtEpochMillis == null) {
        return RemoteProgressSyncDecision.KeepLocal
    }

    return if (remoteUpdatedAtEpochMillis > local.updatedAtEpochMillis) {
        remote.toApplyRemoteDecision(updatedAtEpochMillis = remoteUpdatedAtEpochMillis)
    } else {
        RemoteProgressSyncDecision.KeepLocal
    }
}

fun applyRemoteProgressSyncDecision(
    store: ProgressSyncStore,
    item: ReadableItem,
    pageCount: Int?,
    decision: RemoteProgressSyncDecision,
) {
    when (decision) {
        RemoteProgressSyncDecision.KeepLocal -> Unit
        RemoteProgressSyncDecision.ClearLocal -> store.clearProgress(item.id)
        is RemoteProgressSyncDecision.ApplyRemote -> store.saveProgress(
            item = item,
            locator = decision.locator,
            isFinished = decision.isFinished,
            syncState = SyncState.CLEAN,
            pageCount = pageCount,
            updatedAtEpochMillis = decision.updatedAtEpochMillis,
        )
    }
}

fun ReadingProgress.toProgressSyncLocator(): ProgressSyncLocator = ProgressSyncLocator(
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
)

private fun ReadingProgress.toApplyRemoteDecision(
    updatedAtEpochMillis: Long,
): RemoteProgressSyncDecision.ApplyRemote = RemoteProgressSyncDecision.ApplyRemote(
    locator = toProgressSyncLocator(),
    isFinished = isFinished,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
