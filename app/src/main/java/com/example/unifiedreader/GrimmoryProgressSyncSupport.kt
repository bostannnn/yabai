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
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun pushDirtyGrimmoryProgress(
    api: GrimmoryApi,
    initialAccessToken: String,
    initialRefreshToken: String?,
    items: List<ReadableItem>,
    readerSessionStore: ReaderSessionStore,
): GrimmoryTokenRefreshResult<List<Throwable>> {
    val itemsById = items.associateBy(ReadableItem::id)
    var activeAccessToken = initialAccessToken
    var activeRefreshToken = initialRefreshToken
    var latestSession: GrimmorySession? = null
    val failures = mutableListOf<Throwable>()
    readerSessionStore.progressByItemIdFlow.value.values
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)
        .forEach { progress ->
        val item = itemsById[progress.itemId] ?: return@forEach
        if (progress.syncState != SyncState.DIRTY && progress.syncState != SyncState.FAILED) return@forEach
        val remoteItemId = item.remoteId ?: return@forEach
        val update = if (progress.isExplicitUnreadReset) {
            null
        } else {
            progress.toGrimmoryProgressUpdate(
                item = item,
                pageCount = readerSessionStore.getSession(item)?.pageCount ?: item.pageCount,
            ) ?: return@forEach
        }
        runCatching {
            api.runWithTokenRefresh(
                initialAccessToken = activeAccessToken,
                initialRefreshToken = activeRefreshToken,
            ) { token ->
                if (progress.isExplicitUnreadReset) {
                    api.resetBookProgress(
                        accessToken = token,
                        bookId = remoteItemId,
                    )
                } else {
                    api.updateProgress(
                        accessToken = token,
                        progress = checkNotNull(update),
                    )
                }
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
    return GrimmoryTokenRefreshResult(
        value = failures.toList(),
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
    pageCount: Int? = item.pageCount,
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
        percent = percent ?: pagePercent(pageCount) ?: 0.0,
        isFinished = isFinished,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

// Comic locators carry only a page; Grimmory's web reader reports (page + 1) / pageCount.
private fun ReaderProgressSnapshot.pagePercent(
    pageCount: Int?,
): Double? {
    if (locatorType != LocatorType.PAGE) return null
    val pageIndex = page ?: return null
    val count = pageCount?.takeIf { it > 0 } ?: return null
    return ((pageIndex + 1).toDouble() / count).coerceIn(0.0, 1.0)
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
