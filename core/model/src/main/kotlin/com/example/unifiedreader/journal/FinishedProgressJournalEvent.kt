package com.example.unifiedreader.journal

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState

/**
 * The journal's "finished" entry for a book a server marks as read, when its reading history has none: read from the
 * start to [toPercent] (and [toPage]) at [occurredAtEpochMillis]. [sourceKey] ("kavita", "grimmory") keeps entries from
 * different servers apart; the same book finished at the same time always gets the same id.
 */
fun ReadableItem.finishedProgressJournalEvent(
    sourceKey: String,
    occurredAtEpochMillis: Long,
    toPercent: Double,
    toPage: Int?,
): JournalReadingEvent = JournalReadingEvent(
    id = "$sourceKey:progress:$id:$occurredAtEpochMillis",
    itemId = id,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = title,
    coverUrl = coverUrl,
    itemKind = kind,
    format = format,
    eventType = JournalReadingEventType.FINISHED,
    fromPercent = 0.0,
    toPercent = toPercent.coerceIn(0.0, 1.0),
    fromPage = null,
    toPage = toPage,
    pageCount = pageCount,
    occurredAtEpochMillis = occurredAtEpochMillis,
    remoteEventId = remoteId,
    remoteUpdatedAtEpochMillis = occurredAtEpochMillis,
    syncState = SyncState.CLEAN,
)
