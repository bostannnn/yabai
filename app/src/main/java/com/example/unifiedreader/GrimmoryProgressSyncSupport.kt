package com.example.unifiedreader

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import org.json.JSONObject

internal suspend fun pushDirtyGrimmoryProgress(
    api: GrimmoryApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    items: List<ReadableItem>,
    readerSessionStore: ReaderSessionStore,
): GrimmoryTokenRefreshResult<Unit> {
    val itemsById = items.associateBy(ReadableItem::id)
    var activeAccessToken = initialAccessToken
    var activeRefreshToken = initialRefreshToken
    var latestSession: GrimmorySession? = null
    readerSessionStore.progressByItemIdFlow.value.forEach { (itemId, progress) ->
        val item = itemsById[itemId] ?: return@forEach
        if (progress.syncState != SyncState.DIRTY && progress.syncState != SyncState.FAILED) return@forEach
        val update = if (progress.isExplicitUnreadReset) {
            GrimmoryProgressUpdate(
                bookId = item.remoteId ?: return@forEach,
                bookFileId = item.remoteFileId,
                bookType = item.remoteFileType ?: item.format.name,
                positionData = "0",
                percent = 0.0,
            )
        } else {
            progress.toGrimmoryProgressUpdate(item) ?: return@forEach
        }
        runCatching {
            api.runWithTokenRefresh(
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
            ) { token ->
                api.updateProgress(
                    accessToken = token,
                    progress = update,
                )
            }
        }.onSuccess {
            activeAccessToken = it.accessToken
            activeRefreshToken = it.refreshToken
            latestSession = it.refreshedSession ?: latestSession
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
        }.onFailure {
            readerSessionStore.replaceProgressIfCurrent(
                expected = progress,
                replacement = progress.copy(syncState = SyncState.FAILED),
            )
        }
    }
    return GrimmoryTokenRefreshResult(
        value = Unit,
        accessToken = activeAccessToken,
        refreshToken = activeRefreshToken,
        refreshedSession = latestSession,
    )
}

internal fun ReaderProgressSnapshot.toReaderLocator(): ReaderLocator = ReaderLocator(
    locatorType = locatorType,
    page = page,
    cfi = cfi,
    percent = percent,
)

internal fun ReaderProgressSnapshot.toGrimmoryProgressUpdate(
    item: ReadableItem,
): GrimmoryProgressUpdate? {
    val remoteItemId = item.remoteId ?: return null
    val positionData = when (locatorType) {
        LocatorType.CFI -> cfi?.toGrimmoryEpubPosition()?.cfi
        LocatorType.PAGE -> page?.let { (it + 1).coerceAtLeast(1).toString() }
        LocatorType.PERCENT -> percent?.let { (it.coerceIn(0.0, 1.0) * 100.0).toInt().toString() }
    } ?: return null
    val epubPosition = cfi?.toGrimmoryEpubPosition()
    return GrimmoryProgressUpdate(
        bookId = remoteItemId,
        bookFileId = item.remoteFileId,
        bookType = item.remoteFileType ?: item.format.name,
        positionData = positionData,
        positionHref = epubPosition?.href,
        percent = percent ?: 0.0,
    )
}

private data class GrimmoryEpubPosition(
    val cfi: String,
    val href: String?,
)

private fun String.toGrimmoryEpubPosition(): GrimmoryEpubPosition {
    if (!trimStart().startsWith("{")) {
        return GrimmoryEpubPosition(cfi = this, href = null)
    }
    return runCatching {
        val locator = JSONObject(this)
        val href = locator.optString("href").takeIf(String::isNotBlank)
        val fragments = locator.optJSONObject("locations")?.optJSONArray("fragments")
        val cfi = fragments?.optString(0)?.takeIf(String::isNotBlank)
            ?: return@runCatching GrimmoryEpubPosition(cfi = this, href = href)
        GrimmoryEpubPosition(cfi = cfi, href = href)
    }.getOrElse {
        GrimmoryEpubPosition(cfi = this, href = null)
    }
}
