package com.example.unifiedreader

import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.toKavitaProgressUpdate
import com.example.unifiedreader.data.kavita.toProgressUpdate
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun pushDirtyKavitaProgress(
    api: KavitaApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    initialApiKey: String?,
    items: List<com.example.unifiedreader.core.model.ReadableItem>,
    readerSessionStore: ReaderSessionStore,
): KavitaTokenRefreshResult<List<Throwable>> {
    var activeAccessToken = initialAccessToken
    var activeRefreshToken = initialRefreshToken
    var latestSession: com.example.unifiedreader.data.kavita.KavitaSession? = null
    val failures = mutableListOf<Throwable>()
    val itemById = items.associateBy(com.example.unifiedreader.core.model.ReadableItem::id)
    val dirtyProgress = readerSessionStore.progressByItemIdFlow.value.values
        .filter { progress ->
            progress.syncState == SyncState.DIRTY || progress.syncState == SyncState.FAILED
        }
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)

    dirtyProgress.forEach { progress ->
        val item = itemById[progress.itemId]
        if (item == null) {
            readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = if (progress.isExplicitUnreadReset) {
                    null
                } else {
                    progress.copy(syncState = SyncState.CLEAN)
                },
            )
            return@forEach
        }
        val chapterId = item.remoteId ?: return@forEach
        runCatching {
            api.runWithTokenRefresh(
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
                initialApiKey = initialApiKey,
            ) { token, _ ->
                val progressPage = if (progress.isExplicitUnreadReset) 0 else progress.page ?: 0
                val update = item.toKavitaProgressUpdate(
                    page = progressPage,
                    isFinished = progress.isFinished,
                    updatedAtEpochMillis = progress.updatedAtEpochMillis,
                ) ?: api.getChapterInfo(
                    accessToken = token,
                    chapterId = chapterId,
                ).toProgressUpdate(
                    item = item,
                    page = progressPage,
                    isFinished = progress.isFinished,
                    updatedAtEpochMillis = progress.updatedAtEpochMillis,
                ) ?: return@runWithTokenRefresh false
                api.updateProgress(
                    accessToken = token,
                    progress = update,
                )
                true
            }
        }.onSuccess { uploadResult ->
            activeAccessToken = uploadResult.accessToken
            activeRefreshToken = uploadResult.refreshToken
            latestSession = uploadResult.refreshedSession ?: latestSession
            if (!uploadResult.value) return@forEach
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
            failures += throwable
            readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = progress.copy(syncState = SyncState.FAILED),
            )
        }
    }

    return KavitaTokenRefreshResult(
        value = failures.toList(),
        accessToken = activeAccessToken,
        refreshToken = activeRefreshToken,
        refreshedSession = latestSession,
    )
}
