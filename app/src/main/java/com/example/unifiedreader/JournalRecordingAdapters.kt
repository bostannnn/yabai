package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.JournalEventRecorder
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class JournalRecordingReaderSessionStore(
    private val delegate: ReaderSessionStore,
    private val journalEventRecorder: JournalEventRecorder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ReaderSessionStore by delegate {
    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        delegate.saveProgress(
            item = item,
            locator = locator,
            isFinished = isFinished,
            isExplicitUnreadReset = isExplicitUnreadReset,
            syncState = syncState,
            pageCount = pageCount,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        scope.launch {
            journalEventRecorder.recordReaderProgress(
                JournalProgressInput(
                    item = item,
                    page = locator.page,
                    percent = locator.percent,
                    pageCount = pageCount ?: item.pageCount,
                    isFinished = isFinished,
                    occurredAtEpochMillis = updatedAtEpochMillis,
                ),
            )
        }
    }

    override val isHydratedFlow: StateFlow<Boolean>
        get() = delegate.isHydratedFlow

    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>>
        get() = delegate.progressByItemIdFlow

    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>>
        get() = delegate.sessionByItemIdFlow
}

class JournalRecordingComicReaderProgressStore(
    private val delegate: ComicReaderProgressStore,
    private val journalEventRecorder: JournalEventRecorder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ComicReaderProgressStore by delegate {
    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean,
    ) {
        val safePageCount = pageCount.coerceAtLeast(0)
        val safePageIndex = if (safePageCount > 0) {
            pageIndex.coerceIn(0, safePageCount - 1)
        } else {
            null
        }
        val previousPageIndex = delegate.loadPage(item)
        delegate.savePage(
            item = item,
            pageIndex = pageIndex,
            pageCount = pageCount,
            isFinished = isFinished,
        )
        val shouldRecordJournalProgress = if (safePageIndex != null && previousPageIndex != null) {
            safePageIndex > previousPageIndex
        } else {
            false
        }
        if (!shouldRecordJournalProgress) return
        val displayPage = safePageIndex?.plus(1)
        scope.launch {
            journalEventRecorder.recordReaderProgress(
                JournalProgressInput(
                    item = item,
                    page = displayPage,
                    percent = if (displayPage != null && safePageCount > 0) {
                        displayPage.toDouble() / safePageCount.toDouble()
                    } else {
                        null
                    },
                    pageCount = safePageCount.takeIf { it > 0 },
                    isFinished = isFinished,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }
}
