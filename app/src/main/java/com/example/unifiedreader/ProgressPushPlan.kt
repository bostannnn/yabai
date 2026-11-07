package com.example.unifiedreader

import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

/** What a server does with one unsent reading position. */
internal sealed interface ProgressPushPlan {
    /** Not this run's to send: it stays unsent. */
    data object Leave : ProgressPushPlan

    /** Settled without asking the server; a null [replacement] removes the position. */
    data class SettleLocally(val replacement: ReaderProgressSnapshot?) : ProgressPushPlan

    /** Sent by [push], which checks the server's copy first. */
    data class Push(
        val push: suspend () -> PendingProgressPush,
        val onSent: () -> Unit = {},
        val onFailed: (Throwable) -> Unit = {},
    ) : ProgressPushPlan
}

/**
 * Sends every unsent or failed reading position, oldest first, as [planFor] says. A position the server has a
 * newer copy of takes that copy; one that fails is marked failed and its error returned. An unreachable server
 * stops the run, so the next address can be tried.
 */
internal suspend fun pushDirtyProgress(
    readerSessionStore: ReaderSessionStore,
    planFor: suspend (ReaderProgressSnapshot) -> ProgressPushPlan,
): List<Throwable> {
    val failures = mutableListOf<Throwable>()
    val unsent = readerSessionStore.progressByItemIdFlow.value.values
        .filter { progress -> progress.syncState == SyncState.DIRTY || progress.syncState == SyncState.FAILED }
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)
    unsent.forEach { progress ->
        when (val plan = planFor(progress)) {
            ProgressPushPlan.Leave -> Unit
            is ProgressPushPlan.SettleLocally -> readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = plan.replacement,
            )
            is ProgressPushPlan.Push -> runSuspendCatching { plan.push() }
                .onSuccess { pushed ->
                    plan.onSent()
                    readerSessionStore.settlePendingProgress(progress, pushed)
                }
                .onFailure { throwable ->
                    plan.onFailed(throwable)
                    throwable.rethrowIfServerAddressUnreachable()
                    failures += throwable
                    readerSessionStore.replaceProgressIfCurrent(
                        expected = progress,
                        replacement = progress.copy(syncState = SyncState.FAILED),
                    )
                }
        }
    }
    return failures.toList()
}
