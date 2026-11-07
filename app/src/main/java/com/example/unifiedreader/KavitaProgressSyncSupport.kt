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
import com.example.unifiedreader.data.kavita.toReadingProgress
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.epub.epubReadingOrderIndexOf

internal suspend fun pushDirtyKavitaProgress(
    api: KavitaApi,
    tokens: KavitaRunTokens,
    items: List<ReadableItem>,
    readerSessionStore: ReaderSessionStore,
    // True only when `items` is the whole Kavita catalog (a catalog sync), so a dirty snapshot
    // missing from it was deleted on the server. A pending flush passes a subset.
    itemsAreWholeCatalog: Boolean,
): List<Throwable> {
    val itemById = items.associateBy(ReadableItem::id)
    return pushDirtyProgress(readerSessionStore) { progress ->
        // The store holds every source's progress; the others push their own.
        if (!progress.itemId.startsWith(KavitaItemIdPrefix)) return@pushDirtyProgress ProgressPushPlan.Leave
        val item = itemById[progress.itemId]
            ?: return@pushDirtyProgress progress.planForChapterNotInBatch(itemsAreWholeCatalog)
        val chapterId = item.remoteId ?: return@pushDirtyProgress ProgressPushPlan.Leave
        ProgressPushPlan.Push(
            push = {
                tokens.run push@{ token, _ ->
                    val serverCopy = progress.newerServerCopy(
                        remote = api.getProgress(accessToken = token, chapterId = chapterId)
                            .toReadingProgress(itemId = item.id, pageCount = item.pageCount),
                        localRefinesRemote = ::kavitaLocalRefinesRemote,
                    )
                    if (serverCopy != null) return@push PendingProgressPush.ServerNewer(serverCopy)
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
                    ) ?: return@push PendingProgressPush.Skipped
                    api.updateProgress(accessToken = token, progress = update)
                    PendingProgressPush.Pushed
                }
            },
        )
    }
}

/**
 * A chapter outside this batch is left for a later push; one missing from the whole catalog was removed on the
 * server, so it stops being retried.
 */
private fun ReaderProgressSnapshot.planForChapterNotInBatch(itemsAreWholeCatalog: Boolean): ProgressPushPlan = when {
    !itemsAreWholeCatalog -> ProgressPushPlan.Leave
    isExplicitUnreadReset -> ProgressPushPlan.SettleLocally(replacement = null)
    else -> ProgressPushPlan.SettleLocally(replacement = copy(syncState = SyncState.CLEAN))
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
 * Kavita keeps a position as a page index, an EPUB's only as a chapter index, and reads index 0 as
 * unread. So a remote "unread" for a book open on its first page, or a remote position in the
 * chapter the local one is in, carries nothing new: keep the exact local spot.
 */
internal fun kavitaLocalRefinesRemote(
    local: ProgressSyncSnapshot,
    remote: ReadingProgress?,
): Boolean {
    if (local.isFinished || local.isExplicitUnreadReset || remote?.isFinished == true) return false
    if (local.locatorType == LocatorType.PAGE) return remote == null && local.page == 0
    if (local.locatorType != LocatorType.CFI) return false
    val localChapter = epubReadingOrderIndexOf(local.cfi) ?: return false
    val remoteChapter = when {
        remote == null -> 0
        remote.locatorType == LocatorType.PAGE -> remote.page ?: return false
        else -> return false
    }
    return localChapter == remoteChapter
}
