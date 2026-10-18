package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun pushDirtyStumpProgress(
    api: StumpApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    items: List<ReadableItem>,
    readerSessionStore: ReaderSessionStore,
    diagnostics: StumpProgressSyncDiagnostics = NoOpStumpProgressSyncDiagnostics,
): StumpTokenRefreshResult<List<Throwable>> {
    var activeAccessToken = initialAccessToken
    var activeRefreshToken = initialRefreshToken
    var latestSession: com.example.unifiedreader.data.stump.StumpSession? = null
    val failures = mutableListOf<Throwable>()
    val itemById = items.associateBy(ReadableItem::id)
    val dirtyProgress = readerSessionStore.progressByItemIdFlow.value.values
        .filter { progress ->
            progress.syncState == SyncState.DIRTY || progress.syncState == SyncState.FAILED
        }
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)

    dirtyProgress.forEach { progress ->
        val item = itemById[progress.itemId] ?: return@forEach
        val mediaId = item.remoteId ?: return@forEach
        val operation = progress.toStumpProgressPushOperation(item)
        val event = progress.toStumpProgressSyncDiagnosticEvent(
            item = item,
            mediaId = mediaId,
            operation = operation,
        )
        diagnostics.onAttempt(event)
        if (operation.operation == StumpProgressSyncOperation.LocalUnreadReset) {
            diagnostics.onSuccess(event)
            readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = null,
            )
            return@forEach
        }
        runCatching {
            api.runWithTokenRefresh(
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
            ) { token ->
                api.pushStumpProgressOperation(
                    accessToken = token,
                    mediaId = mediaId,
                    operation = operation,
                )
            }
        }.onSuccess { uploadResult ->
            diagnostics.onSuccess(event)
            activeAccessToken = uploadResult.accessToken
            activeRefreshToken = uploadResult.refreshToken
            latestSession = uploadResult.refreshedSession ?: latestSession
            if (progress.isExplicitUnreadReset) {
                readerSessionStore.replaceProgressIfCurrent(
                    expected = progress,
                    replacement = null,
                )
            } else {
                readerSessionStore.replaceProgressIfCurrent(
                    expected = progress,
                    replacement = progress.copy(syncState = SyncState.CLEAN),
                )
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            diagnostics.onFailure(event, throwable)
            throwable.rethrowIfServerAddressUnreachable()
            failures += throwable
            readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = progress.copy(syncState = SyncState.FAILED),
            )
        }
    }

    return StumpTokenRefreshResult(
        value = failures.toList(),
        accessToken = activeAccessToken,
        refreshToken = activeRefreshToken,
        refreshedSession = latestSession,
    )
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
