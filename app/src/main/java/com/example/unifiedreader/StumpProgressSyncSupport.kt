package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.stump.StumpApi
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

internal suspend fun pushDirtyStumpProgress(
    api: StumpApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    items: List<ReadableItem>,
    readerSessionStore: ReaderSessionStore,
): StumpTokenRefreshResult<Unit> {
    var activeAccessToken = initialAccessToken
    var activeRefreshToken = initialRefreshToken
    var latestSession: com.example.unifiedreader.data.stump.StumpSession? = null
    val itemById = items.associateBy(ReadableItem::id)
    val dirtyProgress = readerSessionStore.progressByItemIdFlow.value.values
        .filter { progress -> progress.syncState == SyncState.DIRTY }
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)

    dirtyProgress.forEach { progress ->
        val item = itemById[progress.itemId] ?: return@forEach
        val mediaId = item.remoteId ?: return@forEach
        val uploadResult = api.runWithTokenRefresh(
            initialAccessToken = activeAccessToken,
            initialRefreshToken = activeRefreshToken,
        ) { token ->
            val pageIndex = progress.page?.coerceAtLeast(0) ?: 0
            if (progress.isExplicitUnreadReset) {
                api.markMediaComplete(
                    accessToken = token,
                    mediaId = mediaId,
                    isComplete = false,
                    pageIndex = null,
                )
            } else if (progress.isFinished) {
                api.markMediaComplete(
                    accessToken = token,
                    mediaId = mediaId,
                    isComplete = true,
                    pageIndex = item.pageCount?.minus(1)?.coerceAtLeast(pageIndex) ?: pageIndex,
                )
            } else {
                api.updatePagedProgress(
                    accessToken = token,
                    mediaId = mediaId,
                    pageIndex = pageIndex,
                )
            }
        }
        activeAccessToken = uploadResult.accessToken
        activeRefreshToken = uploadResult.refreshToken
        latestSession = uploadResult.refreshedSession ?: latestSession
        if (progress.isExplicitUnreadReset) {
            readerSessionStore.clearProgress(item.id)
        } else {
            readerSessionStore.saveProgress(
                item = item,
                locator = progress.toReaderLocator(),
                isFinished = progress.isFinished,
                syncState = SyncState.CLEAN,
                pageCount = item.pageCount,
                updatedAtEpochMillis = progress.updatedAtEpochMillis,
            )
        }
    }

    return StumpTokenRefreshResult(
        value = Unit,
        accessToken = activeAccessToken,
        refreshToken = activeRefreshToken,
        refreshedSession = latestSession,
    )
}
