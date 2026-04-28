package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.periodBounds as journalPeriodBounds
import com.example.unifiedreader.journal.toItemRef as journalToItemRef
import com.example.unifiedreader.journal.toJournalEntry as journalToJournalEntry

typealias ItemJournalEntry = com.example.unifiedreader.journal.ItemJournalEntry
typealias JournalActivityDay = com.example.unifiedreader.journal.JournalActivityDay
typealias JournalEventRecorder = com.example.unifiedreader.journal.JournalEventRecorder
typealias JournalItemRef = com.example.unifiedreader.journal.JournalItemRef
typealias JournalItemSummary = com.example.unifiedreader.journal.JournalItemSummary
typealias JournalPeriod = com.example.unifiedreader.journal.JournalPeriod
typealias JournalPeriodOverview = com.example.unifiedreader.journal.JournalPeriodOverview
typealias JournalProgressInput = com.example.unifiedreader.journal.JournalProgressInput
typealias JournalReadingEvent = com.example.unifiedreader.journal.JournalReadingEvent
typealias JournalReadingEventType = com.example.unifiedreader.journal.JournalReadingEventType
typealias JournalRepository = com.example.unifiedreader.journal.JournalRepository
typealias JournalSnapshot = com.example.unifiedreader.journal.JournalSnapshot
typealias JournalStats = com.example.unifiedreader.journal.JournalStats

internal fun JournalReadingEventEntity.toDomain(): JournalReadingEvent = JournalReadingEvent(
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
)

internal fun JournalReadingEvent.toEntity(): JournalReadingEventEntity = JournalReadingEventEntity(
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
)

internal fun ItemJournalEntryEntity.toDomain(): ItemJournalEntry = ItemJournalEntry(
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
)

internal fun ItemJournalEntry.toEntity(): ItemJournalEntryEntity = ItemJournalEntryEntity(
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
)

fun periodBounds(
    period: JournalPeriod,
    nowEpochMillis: Long,
): Pair<Long, Long> = journalPeriodBounds(
    period = period,
    nowEpochMillis = nowEpochMillis,
)

internal fun ReadableItem.toJournalEntry(
    rating: Int?,
    reviewText: String,
    reviewedAtEpochMillis: Long?,
    updatedAtEpochMillis: Long,
): ItemJournalEntry = journalToJournalEntry(
    rating = rating,
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun JournalItemRef.toJournalEntry(
    rating: Int?,
    reviewText: String,
    reviewedAtEpochMillis: Long?,
    updatedAtEpochMillis: Long,
): ItemJournalEntry = journalToJournalEntry(
    rating = rating,
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun JournalReadingEvent.toItemRef(): JournalItemRef =
    journalToItemRef()

fun ItemJournalEntry.toItemRef(): JournalItemRef =
    journalToItemRef()

private fun String.toSourceTypeOrDefault(): SourceType =
    SourceType.entries.firstOrNull { it.name == this } ?: SourceType.LOCAL

private fun String.toFormatTypeOrDefault(): FormatType =
    FormatType.entries.firstOrNull { it.name == this } ?: FormatType.EPUB

private fun String.toReadableItemKindOrDefault(): ReadableItemKind =
    ReadableItemKind.entries.firstOrNull { it.name == this } ?: ReadableItemKind.BOOK

private fun String.toJournalReadingEventTypeOrDefault(): JournalReadingEventType =
    JournalReadingEventType.entries.firstOrNull { it.name == this } ?: JournalReadingEventType.CONTINUED
