package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
import com.example.unifiedreader.core.database.entity.JournalReadingSessionEntity
import com.example.unifiedreader.core.database.entity.toDomain as journalToDomain
import com.example.unifiedreader.core.database.entity.toEntity as journalToEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.journal.periodBounds as journalPeriodBounds
import com.example.unifiedreader.journal.toItemRef as journalToItemRef
import com.example.unifiedreader.journal.toJournalEntry as journalToJournalEntry

typealias ItemJournalEntry = com.example.unifiedreader.journal.ItemJournalEntry
typealias JournalActivityDay = com.example.unifiedreader.journal.JournalActivityDay
typealias JournalEventRecorder = com.example.unifiedreader.journal.JournalEventRecorder
typealias JournalFacetRow = com.example.unifiedreader.journal.JournalFacetRow
typealias JournalFacetSection = com.example.unifiedreader.journal.JournalFacetSection
typealias JournalFacetType = com.example.unifiedreader.journal.JournalFacetType
typealias JournalItemRef = com.example.unifiedreader.journal.JournalItemRef
typealias JournalItemSummary = com.example.unifiedreader.journal.JournalItemSummary
typealias JournalMediaType = com.example.unifiedreader.journal.JournalMediaType
typealias JournalPeriod = com.example.unifiedreader.journal.JournalPeriod
typealias JournalPeriodOverview = com.example.unifiedreader.journal.JournalPeriodOverview
typealias JournalProgressInput = com.example.unifiedreader.journal.JournalProgressInput
typealias JournalReadingEvent = com.example.unifiedreader.journal.JournalReadingEvent
typealias JournalReadingEventType = com.example.unifiedreader.journal.JournalReadingEventType
typealias JournalReadingSession = com.example.unifiedreader.journal.JournalReadingSession
typealias JournalReadingSessionInput = com.example.unifiedreader.journal.JournalReadingSessionInput
typealias JournalReadingSessionRecorder = com.example.unifiedreader.journal.JournalReadingSessionRecorder
typealias JournalRepository = com.example.unifiedreader.journal.JournalRepository
typealias JournalSnapshot = com.example.unifiedreader.journal.JournalSnapshot
typealias JournalStats = com.example.unifiedreader.journal.JournalStats
typealias JournalTimeBucket = com.example.unifiedreader.journal.JournalTimeBucket

fun JournalReadingEventEntity.toDomain(): JournalReadingEvent =
    journalToDomain()

fun JournalReadingEvent.toEntity(): JournalReadingEventEntity =
    journalToEntity()

fun JournalReadingSessionEntity.toDomain(): JournalReadingSession =
    journalToDomain()

fun JournalReadingSession.toEntity(): JournalReadingSessionEntity =
    journalToEntity()

fun ItemJournalEntryEntity.toDomain(): ItemJournalEntry =
    journalToDomain()

fun ItemJournalEntry.toEntity(): ItemJournalEntryEntity =
    journalToEntity()

fun periodBounds(
    period: JournalPeriod,
    nowEpochMillis: Long,
): Pair<Long, Long> = journalPeriodBounds(
    period = period,
    nowEpochMillis = nowEpochMillis,
)

fun ReadableItem.toJournalEntry(
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

fun JournalItemRef.toJournalEntry(
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

fun JournalReadingSession.toItemRef(): JournalItemRef =
    journalToItemRef()

fun ItemJournalEntry.toItemRef(): JournalItemRef =
    journalToItemRef()
