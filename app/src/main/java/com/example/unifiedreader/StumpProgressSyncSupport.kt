package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

internal suspend fun pushDirtyStumpProgress(
    api: StumpApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    items: List<ReadableItem>,
    readerSessionStore: ReaderSessionStore,
    diagnostics: StumpProgressSyncDiagnostics = NoOpStumpProgressSyncDiagnostics,
): StumpTokenRefreshResult<List<Throwable>> {
    val tokens = RefreshingTokens(initialAccessToken, initialRefreshToken, api.tokenRenewal)
    val itemById = items.associateBy(ReadableItem::id)
    val failures = pushDirtyProgress(readerSessionStore) { progress ->
        val item = itemById[progress.itemId] ?: return@pushDirtyProgress ProgressPushPlan.Leave
        val mediaId = item.remoteId ?: return@pushDirtyProgress ProgressPushPlan.Leave
        val operation = progress.toStumpProgressPushOperation(item)
        val event = progress.toStumpProgressSyncDiagnosticEvent(item = item, mediaId = mediaId, operation = operation)
        diagnostics.onAttempt(event)
        if (operation.operation == StumpProgressSyncOperation.LocalUnreadReset) {
            diagnostics.onSuccess(event)
            return@pushDirtyProgress ProgressPushPlan.SettleLocally(replacement = null)
        }
        ProgressPushPlan.Push(
            push = {
                tokens.run { token ->
                    val serverCopy = progress.newerServerCopy(
                        remote = api.getProgress(
                            accessToken = token,
                            mediaId = mediaId,
                            itemId = item.id,
                            pageCount = item.pageCount,
                        ),
                    )
                    if (serverCopy != null) return@run PendingProgressPush.ServerNewer(serverCopy)
                    api.pushStumpProgressOperation(accessToken = token, mediaId = mediaId, operation = operation)
                    PendingProgressPush.Pushed
                }
            },
            onSent = { diagnostics.onSuccess(event) },
            onFailed = { throwable -> diagnostics.onFailure(event, throwable) },
        )
    }
    return tokens.result(failures)
}

private data class StumpProgressPushOperation(
    val operation: StumpProgressSyncOperation,
    val pageIndex: Int?,
)

private fun ReaderProgressSnapshot.toStumpProgressPushOperation(
    item: ReadableItem,
): StumpProgressPushOperation {
    val pageIndex = page?.coerceAtLeast(0) ?: 0
    return when {
        isExplicitUnreadReset -> StumpProgressPushOperation(
            // Stump completion/unread mutations are unreliable, so stale manual unread resets
            // from older builds are resolved locally instead of retrying a failing remote call.
            operation = StumpProgressSyncOperation.LocalUnreadReset,
            pageIndex = null,
        )

        isFinished -> StumpProgressPushOperation(
            operation = StumpProgressSyncOperation.UpdatePagedProgress,
            pageIndex = item.pageCount?.minus(1)?.coerceAtLeast(pageIndex) ?: pageIndex,
        )

        else -> StumpProgressPushOperation(
            operation = StumpProgressSyncOperation.UpdatePagedProgress,
            pageIndex = pageIndex,
        )
    }
}

private suspend fun StumpApi.pushStumpProgressOperation(
    accessToken: String,
    mediaId: String,
    operation: StumpProgressPushOperation,
) {
    when (operation.operation) {
        StumpProgressSyncOperation.MarkMediaIncomplete -> markMediaComplete(
            accessToken = accessToken,
            mediaId = mediaId,
            isComplete = false,
            pageIndex = null,
        )

        StumpProgressSyncOperation.MarkMediaComplete -> markMediaComplete(
            accessToken = accessToken,
            mediaId = mediaId,
            isComplete = true,
            pageIndex = operation.pageIndex,
        )

        StumpProgressSyncOperation.UpdatePagedProgress -> updatePagedProgress(
            accessToken = accessToken,
            mediaId = mediaId,
            pageIndex = operation.pageIndex ?: 0,
        )

        StumpProgressSyncOperation.LocalUnreadReset -> error("Local-only Stump unread reset should not be pushed.")
    }
}

private fun ReaderProgressSnapshot.toStumpProgressSyncDiagnosticEvent(
    item: ReadableItem,
    mediaId: String,
    operation: StumpProgressPushOperation,
): StumpProgressSyncDiagnosticEvent = StumpProgressSyncDiagnosticEvent(
    itemId = item.id,
    mediaId = mediaId,
    operation = operation.operation,
    pageIndex = operation.pageIndex,
    remotePageNumber = operation.pageIndex?.plus(1),
    pageCount = item.pageCount,
    isFinished = isFinished,
    isExplicitUnreadReset = isExplicitUnreadReset,
    syncState = syncState,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
