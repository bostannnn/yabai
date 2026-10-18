package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.progress.ProgressSyncSnapshot
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.toKavitaProgressUpdate
import com.example.unifiedreader.data.kavita.toProgressUpdate
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.epub.epubReadingOrderIndexOf
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
    // The store holds every source's progress; the others push their own.
    val dirtyProgress = readerSessionStore.progressByItemIdFlow.value.values
        .filter { progress -> progress.itemId.startsWith(KavitaItemIdPrefix) }
        .filter { progress ->
            progress.syncState == SyncState.DIRTY || progress.syncState == SyncState.FAILED
        }
        .sortedBy(ReaderProgressSnapshot::updatedAtEpochMillis)

    dirtyProgress.forEach { progress ->
        val item = itemById[progress.itemId]
        if (item == null) {
            // A Kavita chapter missing from the catalog was removed on the server; stop retrying it.
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
                val progressPage = if (progress.isExplicitUnreadReset) 0 else progress.kavitaPageNum(item)
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

private val KavitaItemIdPrefix = SourceType.KAVITA.remoteItemId(remoteId = "")

// Kavita's pageNum is a page index for comics and PDFs but the reading-order (chapter) index for
// EPUBs, whose local page is a Readium position.
internal fun ReaderProgressSnapshot.kavitaPageNum(
    item: ReadableItem,
): Int {
    if (item.format != FormatType.EPUB || locatorType == LocatorType.PAGE) return page ?: 0
    epubReadingOrderIndexOf(cfi)?.let { chapterIndex -> return chapterIndex }
    // Saved before the reader recorded the chapter: estimate it from the book percent.
    val chapterCount = item.pageCount?.takeIf { it > 0 } ?: return 0
    return percent?.let { (it * chapterCount).toInt().coerceIn(0, chapterCount - 1) } ?: 0
}

/**
 * Kavita keeps an EPUB position only as a chapter index, and reads index 0 as unread, so a remote
 * position in the chapter the local one is in carries nothing new: keep the exact local spot.
 */
internal fun kavitaEpubLocalRefinesRemote(
    local: ProgressSyncSnapshot,
    remote: ReadingProgress?,
): Boolean {
    if (local.isFinished || local.isExplicitUnreadReset || remote?.isFinished == true) return false
    if (local.locatorType != LocatorType.CFI) return false
    val localChapter = epubReadingOrderIndexOf(local.cfi) ?: return false
    val remoteChapter = when {
        remote == null -> 0
        remote.locatorType == LocatorType.PAGE -> remote.page ?: return false
        else -> return false
    }
    return localChapter == remoteChapter
}
