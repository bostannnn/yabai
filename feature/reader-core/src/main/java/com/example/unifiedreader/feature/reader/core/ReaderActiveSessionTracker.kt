package com.example.unifiedreader.feature.reader.core

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.journal.JournalReadingSessionInput
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReaderActiveSessionTracker(
    private val item: ReadableItem,
    private val recorder: JournalReadingSessionRecorder,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private var startedAtEpochMillis: Long? = null
    private var fromPage: Int? = null
    private var fromPercent: Double? = null

    fun start(
        page: Int?,
        percent: Double?,
    ) {
        if (startedAtEpochMillis != null) return
        startedAtEpochMillis = nowEpochMillis()
        fromPage = page
        fromPercent = percent
    }

    fun flushAndReset(
        page: Int?,
        percent: Double?,
        pageCount: Int?,
    ) {
        val startedAt = startedAtEpochMillis ?: return
        val endedAt = nowEpochMillis()
        startedAtEpochMillis = null
        val duration = endedAt - startedAt
        if (duration <= 0L) return
        scope.launch {
            recorder.recordReadingSession(
                JournalReadingSessionInput(
                    item = item,
                    startedAtEpochMillis = startedAt,
                    endedAtEpochMillis = endedAt,
                    fromPage = fromPage,
                    toPage = page,
                    pageCount = pageCount ?: item.pageCount,
                    fromPercent = fromPercent,
                    toPercent = percent,
                ),
            )
        }
        fromPage = page
        fromPercent = percent
    }
}
