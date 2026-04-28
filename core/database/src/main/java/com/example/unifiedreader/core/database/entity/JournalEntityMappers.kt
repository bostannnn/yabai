package com.example.unifiedreader.core.database.entity

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingEventType

fun JournalReadingEventEntity.toDomain(): JournalReadingEvent = JournalReadingEvent(
    id = id,
    itemId = itemId,
    sourceType = sourceType.toSourceTypeOrDefault(),
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind.toReadableItemKindOrDefault(),
    format = format.toFormatTypeOrDefault(),
    eventType = eventType.toJournalReadingEventTypeOrDefault(),
    fromPercent = fromPercent,
    toPercent = toPercent,
    fromPage = fromPage,
    toPage = toPage,
    pageCount = pageCount,
    occurredAtEpochMillis = occurredAtEpochMillis,
    remoteEventId = remoteEventId,
    remoteUpdatedAtEpochMillis = remoteUpdatedAtEpochMillis,
    syncState = syncState.toSyncStateOrDefault(),
)

fun JournalReadingEvent.toEntity(): JournalReadingEventEntity = JournalReadingEventEntity(
    id = id,
    itemId = itemId,
    sourceType = sourceType.name,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind.name,
    format = format.name,
    eventType = eventType.name,
    fromPercent = fromPercent,
    toPercent = toPercent,
    fromPage = fromPage,
    toPage = toPage,
    pageCount = pageCount,
    occurredAtEpochMillis = occurredAtEpochMillis,
    remoteEventId = remoteEventId,
    remoteUpdatedAtEpochMillis = remoteUpdatedAtEpochMillis,
    syncState = syncState.name,
)

fun ItemJournalEntryEntity.toDomain(): ItemJournalEntry = ItemJournalEntry(
    itemId = itemId,
    sourceType = sourceType.toSourceTypeOrDefault(),
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind.toReadableItemKindOrDefault(),
    format = format.toFormatTypeOrDefault(),
    rating = rating,
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    remoteEntryId = remoteEntryId,
    remoteReviewNoteId = remoteReviewNoteId,
    remoteUpdatedAtEpochMillis = remoteUpdatedAtEpochMillis,
    syncState = syncState.toSyncStateOrDefault(),
)

fun ItemJournalEntry.toEntity(): ItemJournalEntryEntity = ItemJournalEntryEntity(
    itemId = itemId,
    sourceType = sourceType.name,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind.name,
    format = format.name,
    rating = rating,
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    remoteEntryId = remoteEntryId,
    remoteReviewNoteId = remoteReviewNoteId,
    remoteUpdatedAtEpochMillis = remoteUpdatedAtEpochMillis,
    syncState = syncState.name,
)

private fun String.toSourceTypeOrDefault(): SourceType =
    SourceType.entries.firstOrNull { it.name == this } ?: SourceType.LOCAL

private fun String.toFormatTypeOrDefault(): FormatType =
    FormatType.entries.firstOrNull { it.name == this } ?: FormatType.EPUB

private fun String.toReadableItemKindOrDefault(): ReadableItemKind =
    ReadableItemKind.entries.firstOrNull { it.name == this } ?: ReadableItemKind.BOOK

private fun String.toJournalReadingEventTypeOrDefault(): JournalReadingEventType =
    JournalReadingEventType.entries.firstOrNull { it.name == this } ?: JournalReadingEventType.CONTINUED

private fun String.toSyncStateOrDefault(): SyncState =
    SyncState.entries.firstOrNull { it.name == this } ?: SyncState.CLEAN
