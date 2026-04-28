package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingEventType
import com.example.unifiedreader.journal.JournalRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

const val GRIMMORY_UNIFIED_READER_REVIEW_TITLE = "Unified Reader Review"
const val GRIMMORY_UNIFIED_READER_REVIEW_CFI = "unified-reader-review"

suspend fun pullGrimmoryJournalCache(
    api: GrimmoryApi,
    accessToken: String,
    entries: List<GrimmoryBookCatalogEntry>,
    journalRepository: JournalRepository?,
) {
    val repository = journalRepository ?: return
    val itemByRemoteBookId = entries
        .map(GrimmoryBookCatalogEntry::item)
        .mapNotNull { item -> item.remoteId?.toLongOrNull()?.let { it to item } }
        .toMap()
    val reviewLookup = runCatching {
        api.getNotebookEntriesExport(
            accessToken = accessToken,
            types = listOf("NOTE"),
            search = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
        )
    }.fold(
        onSuccess = { entries ->
            GrimmoryReviewLookup(
                entries = entries.toReviewJournalEntries(itemByRemoteBookId),
                shouldPreserveCachedReviews = false,
            )
        },
        onFailure = { error ->
            when (error) {
                is CancellationException,
                is GrimmoryUnauthorizedException -> throw error
            }
            GrimmoryReviewLookup(
                entries = emptyList(),
                shouldPreserveCachedReviews = true,
            )
        },
    )
    val journalEntries = entries
        .mapNotNull(GrimmoryBookCatalogEntry::toJournalEntry)
        .mergeReviewEntries(reviewLookup.entries)
    val failedSessionItemIds = mutableSetOf<String>()
    val sessionEvents = entries.flatMap { entry ->
        val bookId = entry.item.remoteId ?: return@flatMap emptyList()
        runCatching {
            api.getReadingSessionsForBook(
                accessToken = accessToken,
                bookId = bookId,
            ).toJournalEvents(entry.item)
        }.getOrElse { error ->
            when (error) {
                is CancellationException,
                is GrimmoryUnauthorizedException -> throw error
            }
            failedSessionItemIds += entry.item.id
            emptyList()
        }
    }
    val sessionFetchFailed = failedSessionItemIds.isNotEmpty()
    val sessionFinishedItemIds = sessionEvents
        .filter { event -> event.eventType == JournalReadingEventType.FINISHED }
        .mapTo(mutableSetOf(), JournalReadingEvent::itemId)
    val readingEventReplacementItemIds = if (sessionFetchFailed) {
        entries
            .map(GrimmoryBookCatalogEntry::item)
            .filterNot { item -> item.id in failedSessionItemIds }
            .mapTo(mutableSetOf(), ReadableItem::id)
    } else {
        null
    }
    val progressEvents = entries
        .filter { entry ->
            readingEventReplacementItemIds == null ||
                entry.item.id in readingEventReplacementItemIds
        }
        .mapNotNull(GrimmoryBookCatalogEntry::toFinishedProgressJournalEvent)
        .filterNot { event -> event.itemId in sessionFinishedItemIds }
    repository.replaceCleanSourceJournalCache(
        sourceType = SourceType.GRIMMORY,
        events = sessionEvents + progressEvents,
        entries = journalEntries,
        preserveReviewFieldsWhenIncomingBlank = reviewLookup.shouldPreserveCachedReviews,
        preserveRatingWhenIncomingBlank = false,
        preserveReadingEventsWhenIncomingIncomplete = sessionFetchFailed,
        readingEventReplacementItemIds = readingEventReplacementItemIds,
    )
}

private data class GrimmoryReviewLookup(
    val entries: List<ItemJournalEntry>,
    val shouldPreserveCachedReviews: Boolean,
)

private fun List<ItemJournalEntry>.mergeReviewEntries(
    reviewEntries: List<ItemJournalEntry>,
): List<ItemJournalEntry> {
    if (reviewEntries.isEmpty()) return this
    val reviewByItemId = reviewEntries.associateBy(ItemJournalEntry::itemId)
    val merged = map { entry ->
        val reviewEntry = reviewByItemId[entry.itemId] ?: return@map entry
        entry.copy(
            reviewText = reviewEntry.reviewText,
            reviewedAtEpochMillis = reviewEntry.reviewedAtEpochMillis,
            updatedAtEpochMillis = maxOf(entry.updatedAtEpochMillis, reviewEntry.updatedAtEpochMillis),
            remoteReviewNoteId = reviewEntry.remoteReviewNoteId,
            remoteUpdatedAtEpochMillis = listOfNotNull(
                entry.remoteUpdatedAtEpochMillis,
                reviewEntry.remoteUpdatedAtEpochMillis,
            ).maxOrNull(),
        )
    }
    val existingItemIds = merged.mapTo(mutableSetOf(), ItemJournalEntry::itemId)
    return merged + reviewEntries.filterNot { entry -> entry.itemId in existingItemIds }
}

suspend fun pushDirtyGrimmoryJournalEntries(
    api: GrimmoryApi,
    accessToken: String,
    entries: List<ReadableItem>,
    journalRepository: JournalRepository?,
) {
    val repository = journalRepository ?: return
    val itemById = entries.associateBy(ReadableItem::id)
    repository.getDirtyJournalEntriesForSource(SourceType.GRIMMORY).forEach { entry ->
        var syncedRemoteReviewNoteId = entry.remoteReviewNoteId
        runCatching {
            val item = itemById[entry.itemId] ?: error("No Grimmory catalog item for journal entry ${entry.itemId}")
            val bookId = item.remoteId ?: error("Grimmory journal entry ${entry.itemId} has no remote book id")
            val rating = entry.rating
            if (rating == null) {
                api.resetPersonalRating(accessToken = accessToken, bookId = bookId)
            } else {
                api.updatePersonalRating(
                    accessToken = accessToken,
                    bookId = bookId,
                    rating = rating.coerceIn(1, 10),
                )
            }
            val reviewNoteId = entry.remoteReviewNoteId
                ?: findExistingReservedReviewNoteId(
                    api = api,
                    accessToken = accessToken,
                    bookId = bookId,
                )
            when {
                entry.reviewText.isBlank() && reviewNoteId != null -> {
                    api.deleteBookNoteV2(accessToken = accessToken, noteId = reviewNoteId)
                    syncedRemoteReviewNoteId = null
                }
                entry.reviewText.isNotBlank() && reviewNoteId != null -> {
                    val updatedNote = api.updateBookNoteV2(
                        accessToken = accessToken,
                        noteId = reviewNoteId,
                        noteContent = entry.reviewText,
                        chapterTitle = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                    )
                    syncedRemoteReviewNoteId = updatedNote.id?.toString() ?: reviewNoteId
                }
                entry.reviewText.isNotBlank() -> {
                    val createdNote = api.createBookNoteV2(
                        accessToken = accessToken,
                        bookId = bookId,
                        cfi = GRIMMORY_UNIFIED_READER_REVIEW_CFI,
                        selectedText = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                        noteContent = entry.reviewText,
                        chapterTitle = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
                    )
                    syncedRemoteReviewNoteId = createdNote.id?.toString()
                        ?: error("Created Grimmory review note did not include an id")
                }
            }
        }.onSuccess {
            repository.updateJournalEntryRemoteReviewNoteId(entry.itemId, syncedRemoteReviewNoteId)
            repository.markJournalEntrySyncState(entry.itemId, SyncState.CLEAN)
        }.onFailure { error ->
            when (error) {
                is CancellationException,
                is GrimmoryUnauthorizedException -> throw error
            }
            repository.markJournalEntrySyncState(entry.itemId, SyncState.FAILED)
        }
    }
}

private fun GrimmoryBookCatalogEntry.toJournalEntry(): ItemJournalEntry? {
    val isFinishedByReadStatus = readStatus.isFinishedReadStatus()
    val hasJournalData = personalRating != null ||
        dateFinished != null ||
        lastReadTime != null ||
        isFinishedByReadStatus
    if (!hasJournalData) return null
    val updatedAt = listOfNotNull(
        dateFinished?.toEpochMillisOrNull(),
        lastReadTime?.toEpochMillisOrNull(),
    ).maxOrNull() ?: System.currentTimeMillis()
    return ItemJournalEntry(
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        rating = personalRating?.coerceIn(1, 10),
        reviewText = "",
        reviewedAtEpochMillis = null,
        updatedAtEpochMillis = updatedAt,
        remoteUpdatedAtEpochMillis = updatedAt,
        syncState = SyncState.CLEAN,
    )
}

private fun List<GrimmoryNotebookEntryDto>.toReviewJournalEntries(
    itemByRemoteBookId: Map<Long, ReadableItem>,
): List<ItemJournalEntry> = filter { entry ->
    entry.type.equals("NOTE", ignoreCase = true) &&
        (entry.text == GRIMMORY_UNIFIED_READER_REVIEW_TITLE || entry.chapterTitle == GRIMMORY_UNIFIED_READER_REVIEW_TITLE) &&
        entry.note?.isNotBlank() == true
}.groupBy(GrimmoryNotebookEntryDto::bookId)
    .mapNotNull { (bookId, notes) ->
        val item = bookId?.let(itemByRemoteBookId::get) ?: return@mapNotNull null
        val note = notes.maxByOrNull { entry ->
            entry.updatedAt.toEpochMillisOrNull() ?: entry.createdAt.toEpochMillisOrNull() ?: 0L
        } ?: return@mapNotNull null
        val updatedAt = note.updatedAt.toEpochMillisOrNull()
            ?: note.createdAt.toEpochMillisOrNull()
            ?: System.currentTimeMillis()
        ItemJournalEntry(
            itemId = item.id,
            sourceType = item.sourceType,
            libraryId = item.libraryId,
            itemTitle = item.title,
            coverUrl = item.coverUrl,
            itemKind = item.kind,
            format = item.format,
            rating = null,
            reviewText = note.note.orEmpty(),
            reviewedAtEpochMillis = updatedAt,
            updatedAtEpochMillis = updatedAt,
            remoteReviewNoteId = note.id?.toString(),
            remoteUpdatedAtEpochMillis = updatedAt,
            syncState = SyncState.CLEAN,
    )
}

private suspend fun findExistingReservedReviewNoteId(
    api: GrimmoryApi,
    accessToken: String,
    bookId: String,
): String? = api.getNotebookEntriesExport(
    accessToken = accessToken,
    types = listOf("NOTE"),
    search = GRIMMORY_UNIFIED_READER_REVIEW_TITLE,
).firstReservedReviewNoteForBook(bookId.toLongOrNull())
    ?.id
    ?.toString()

private fun GrimmoryBookCatalogEntry.toFinishedProgressJournalEvent(): JournalReadingEvent? {
    val isFinished = progress?.isFinished == true ||
        dateFinished != null ||
        readStatus.isFinishedReadStatus()
    if (!isFinished) return null
    val occurredAt = dateFinished?.toEpochMillisOrNull()
        ?: progress?.updatedAtEpochMillis
        ?: lastReadTime?.toEpochMillisOrNull()
        ?: System.currentTimeMillis()
    return JournalReadingEvent(
        id = "grimmory:progress:${item.id}:$occurredAt",
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        eventType = JournalReadingEventType.FINISHED,
        fromPercent = 0.0,
        toPercent = (progress?.percent ?: 1.0).coerceIn(0.0, 1.0),
        fromPage = null,
        toPage = progress?.page,
        pageCount = item.pageCount,
        occurredAtEpochMillis = occurredAt,
        remoteEventId = item.remoteId,
        remoteUpdatedAtEpochMillis = occurredAt,
        syncState = SyncState.CLEAN,
    )
}

private fun List<GrimmoryReadingSessionDto>.toJournalEvents(
    item: ReadableItem,
): List<JournalReadingEvent> = mapNotNull { session ->
    val occurredAt = session.endTime.toEpochMillisOrNull()
        ?: session.startTime.toEpochMillisOrNull()
        ?: session.createdAt.toEpochMillisOrNull()
        ?: return@mapNotNull null
    val fromPercent = session.startProgress.toJournalPercent()
    val toPercent = session.endProgress.toJournalPercent()
    JournalReadingEvent(
        id = "grimmory:session:${session.id ?: item.remoteId}:$occurredAt",
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        eventType = if ((toPercent ?: 0.0) >= 0.995) {
            JournalReadingEventType.FINISHED
        } else {
            JournalReadingEventType.CONTINUED
        },
        fromPercent = fromPercent,
        toPercent = toPercent,
        fromPage = null,
        toPage = null,
        pageCount = item.pageCount,
        occurredAtEpochMillis = occurredAt,
        remoteEventId = session.id?.toString(),
        remoteUpdatedAtEpochMillis = occurredAt,
        syncState = SyncState.CLEAN,
    )
}

private fun Float?.toJournalPercent(): Double? = this
    ?.toDouble()
    ?.div(100.0)
    ?.coerceIn(0.0, 1.0)

private fun String?.isFinishedReadStatus(): Boolean = when (this?.trim()?.uppercase()) {
    "READ",
    "FINISHED",
    "COMPLETED",
    "DONE" -> true
    else -> false
}

private fun List<GrimmoryNotebookEntryDto>.firstReservedReviewNoteForBook(
    bookId: Long?,
): GrimmoryNotebookEntryDto? = filter { entry ->
    entry.bookId == bookId &&
        entry.type.equals("NOTE", ignoreCase = true) &&
        (entry.text == GRIMMORY_UNIFIED_READER_REVIEW_TITLE || entry.chapterTitle == GRIMMORY_UNIFIED_READER_REVIEW_TITLE)
}.maxByOrNull { entry ->
    entry.updatedAt.toEpochMillisOrNull() ?: entry.createdAt.toEpochMillisOrNull() ?: 0L
}

private fun String?.toEpochMillisOrNull(): Long? {
    val value = this ?: return null
    return runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrElse {
        runCatching {
            LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }
}
