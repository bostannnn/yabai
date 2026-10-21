package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

/** What pushing one queued progress save came to. */
internal sealed interface PendingProgressPush {
    /** Sent: the save is now the server's copy. */
    data object Pushed : PendingProgressPush

    /** Not sent: the server's copy is the more recent read and replaces the save. */
    data class ServerNewer(val serverCopy: ReaderProgressSnapshot) : PendingProgressPush

    /** Not sent, and left queued for a later push. */
    data object Skipped : PendingProgressPush
}

/**
 * The server's copy to keep instead of pushing this queued save, or null to push it. The most
 * recent read wins, by the rule a pull follows ([decideRemoteProgressSync]); no server copy, or one
 * without a date, never beats a local save.
 */
internal fun ReaderProgressSnapshot.newerServerCopy(
    remote: ReadingProgress?,
    localRefinesRemote: (ProgressSyncSnapshot, ReadingProgress?) -> Boolean = { _, _ -> false },
): ReaderProgressSnapshot? {
    val decision = decideRemoteProgressSync(
        local = ProgressSyncSnapshot(
            itemId = itemId,
            locatorType = locatorType,
            page = page,
            cfi = cfi,
            percent = percent,
            isFinished = isFinished,
            isExplicitUnreadReset = isExplicitUnreadReset,
            syncState = syncState,
            updatedAtEpochMillis = updatedAtEpochMillis,
        ),
        remote = remote,
        nowEpochMillis = updatedAtEpochMillis,
        localRefinesRemote = localRefinesRemote,
    ) as? RemoteProgressSyncDecision.ApplyRemote ?: return null
    return ReaderProgressSnapshot(
        itemId = itemId,
        locatorType = decision.locator.locatorType,
        page = decision.locator.page,
        cfi = decision.locator.cfi,
        percent = decision.locator.percent,
        isFinished = decision.isFinished,
        syncState = SyncState.CLEAN,
        updatedAtEpochMillis = decision.updatedAtEpochMillis,
    )
}

/** Records how pushing [progress] went, unless a newer local save replaced it meanwhile. */
internal suspend fun ReaderSessionStore.settlePendingProgress(
    progress: ReaderProgressSnapshot,
    push: PendingProgressPush,
) {
    val replacement = when (push) {
        PendingProgressPush.Pushed -> progress.copy(syncState = SyncState.CLEAN)
            .takeUnless { progress.isExplicitUnreadReset }
        is PendingProgressPush.ServerNewer -> push.serverCopy
        PendingProgressPush.Skipped -> return
    }
    replaceProgressIfCurrent(expected = progress, replacement = replacement)
}
