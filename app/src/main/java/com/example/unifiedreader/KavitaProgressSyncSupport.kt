package com.example.unifiedreader

import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.toProgressUpdate
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore

internal suspend fun pushDirtyKavitaProgress(
    api: KavitaApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    initialApiKey: String?,
    items: List<com.example.unifiedreader.core.model.ReadableItem>,
    readerSessionStore: ReaderSessionStore,
): KavitaTokenRefreshResult<Unit> {
    var activeAccessToken = initialAccessToken
    var activeRefreshToken = initialRefreshToken
    var latestSession: com.example.unifiedreader.data.kavita.KavitaSession? = null
    val itemById = items.associateBy(com.example.unifiedreader.core.model.ReadableItem::id)
    val dirtyProgress = readerSessionStore.progressByItemIdFlow.value.values
        .filter { progress -> progress.syncState == SyncState.DIRTY }
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)

    dirtyProgress.forEach { progress ->
        val item = itemById[progress.itemId] ?: return@forEach
        val chapterId = item.remoteId ?: return@forEach
        val uploadResult = api.runWithTokenRefresh(
            initialAccessToken = activeAccessToken,
            initialRefreshToken = activeRefreshToken,
            initialApiKey = initialApiKey,
        ) { token, _ ->
            val chapterInfo = api.getChapterInfo(
                accessToken = token,
                chapterId = chapterId,
            )
            val update = chapterInfo.toProgressUpdate(
                item = item,
                page = progress.page ?: 0,
                isFinished = progress.isFinished,
                updatedAtEpochMillis = progress.updatedAtEpochMillis,
            ) ?: return@runWithTokenRefresh Unit
            api.updateProgress(
                accessToken = token,
                progress = update,
            )
        }
        activeAccessToken = uploadResult.accessToken
        activeRefreshToken = uploadResult.refreshToken
        latestSession = uploadResult.refreshedSession ?: latestSession
        readerSessionStore.saveProgress(
            item = item,
            locator = progress.toReaderLocator(),
            isFinished = progress.isFinished,
            syncState = SyncState.CLEAN,
            pageCount = item.pageCount,
            updatedAtEpochMillis = progress.updatedAtEpochMillis,
        )
    }

    return KavitaTokenRefreshResult(
        value = Unit,
        accessToken = activeAccessToken,
        refreshToken = activeRefreshToken,
        refreshedSession = latestSession,
    )
}
