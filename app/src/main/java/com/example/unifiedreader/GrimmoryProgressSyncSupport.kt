package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
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
            throwable.rethrowIfServerAddressUnreachable()
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
    val epubPosition = if (item.format == FormatType.EPUB) {
        toGrimmoryEpubPosition() ?: percent?.let { GrimmoryEpubPosition() } ?: return null
    } else {
        null
    }
    val positionData = if (epubPosition != null) {
        epubPosition.cfi
    } else {
        when (locatorType) {
            LocatorType.CFI -> cfi
            LocatorType.PAGE -> page?.let { (it + 1).coerceAtLeast(1).toString() }
            LocatorType.PERCENT -> percent?.let { (it.coerceIn(0.0, 1.0) * 100.0).toInt().toString() }
        } ?: return null
    }
    return GrimmoryProgressUpdate(
        bookId = remoteItemId,
        bookFileId = item.remoteFileId,
        bookType = item.remoteFileType ?: item.format.name,
        positionData = positionData,
        positionHref = epubPosition?.href,
        percent = percent ?: pagePercent(pageCount) ?: 0.0,
        contentSourceProgressPercent = epubPosition?.chapterProgressPercent,
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

/**
 * An EPUB position as Grimmory stores it. [cfi] is only ever a real EPUB CFI (one that came from
 * Grimmory's web reader): that reader takes any other position data for a file path, fails to
 * resolve it and loses the position. Without one, it opens [href] and scrolls to
 * [chapterProgressPercent] in it, as it does for Kobo positions.
 */
private data class GrimmoryEpubPosition(
    val cfi: String? = null,
    val href: String? = null,
    val chapterProgressPercent: Double? = null,
)

private fun ReaderProgressSnapshot.toGrimmoryEpubPosition(): GrimmoryEpubPosition? {
    if (locatorType != LocatorType.CFI) return null
    val serialized = cfi?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!serialized.startsWith("{")) {
        return GrimmoryEpubPosition(cfi = serialized.takeIf(String::isEpubCfi))
    }
    return runCatching {
        val locator = JSONObject(serialized)
        val locations = locator.optJSONObject("locations")
        val fragments = locations?.optJSONArray("fragments")
            ?.let { array -> (0 until array.length()).map(array::optString) }
            .orEmpty()
        GrimmoryEpubPosition(
            // The reader may put the chapter's anchor ahead of Grimmory's CFI (resolveEpubRestoredLocator).
            cfi = fragments.firstOrNull(String::isEpubCfi),
            // Decoded, like the paths Grimmory's web reader and Kobo save and compare.
            href = locator.optString("href").substringBefore('#').takeIf(String::isNotBlank)?.let(Uri::decode),
            chapterProgressPercent = locations
                ?.optDouble("progression", Double.NaN)
                ?.takeIf { it in 0.0..1.0 }
                ?.let { it * 100.0 },
        )
    }.getOrNull()
}

private fun String.isEpubCfi(): Boolean = startsWith("epubcfi(")
