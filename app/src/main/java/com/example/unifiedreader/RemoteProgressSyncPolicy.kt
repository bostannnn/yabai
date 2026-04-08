package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

internal sealed interface RemoteProgressSyncDecision {
    data object KeepLocal : RemoteProgressSyncDecision

    data object ClearLocal : RemoteProgressSyncDecision

    data class ApplyRemote(
        val locator: ReaderLocator,
        val isFinished: Boolean,
        val updatedAtEpochMillis: Long,
    ) : RemoteProgressSyncDecision
}

internal fun decideRemoteProgressSync(
    local: ReaderProgressSnapshot?,
    remote: ReadingProgress?,
    nowEpochMillis: Long,
    missingRemoteClearsLocal: Boolean = false,
    remoteClearOverridesLocal: Boolean = true,
): RemoteProgressSyncDecision {
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

internal fun applyRemoteProgressSyncDecision(
    store: ReaderSessionStore,
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

internal fun ReadingProgress.toReaderLocator(): ReaderLocator = ReaderLocator(
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
)

private fun ReadingProgress.toApplyRemoteDecision(
    updatedAtEpochMillis: Long,
): RemoteProgressSyncDecision.ApplyRemote = RemoteProgressSyncDecision.ApplyRemote(
    locator = toReaderLocator(),
    isFinished = isFinished,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
