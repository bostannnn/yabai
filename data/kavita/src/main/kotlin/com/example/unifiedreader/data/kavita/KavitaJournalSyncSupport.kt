package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingEventType
import com.example.unifiedreader.journal.JournalRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

suspend fun pullKavitaJournalCache(
    api: KavitaApi,
    accessToken: String,
    libraries: List<Library>,
    entries: List<KavitaChapterCatalogEntry>,
    journalRepository: JournalRepository?,
) {
    val repository = journalRepository ?: return
    val itemByRemoteChapterId = entries
        .map(KavitaChapterCatalogEntry::item)
        .mapNotNull { item -> item.remoteId?.toIntOrNull()?.let { it to item } }
        .toMap()
    val reviewLookup = runCatching {
        val userId = api.getCurrentUserId(accessToken)
        api.getAllReviews(
            accessToken = accessToken,
            userId = userId,
        )
    }.fold(
        onSuccess = { reviews ->
            KavitaReviewLookup(
                reviews = reviews,
                shouldPreserveCachedReviews = false,
            )
        },
        onFailure = { error ->
            when (error) {
                is CancellationException,
                is KavitaUnauthorizedException -> throw error
            }
            KavitaReviewLookup(
                reviews = emptyList(),
                shouldPreserveCachedReviews = true,
            )
        },
    )
    val libraryIdByRemoteId = libraries
        .mapNotNull { library -> library.remoteId?.toIntOrNull()?.let { remoteId -> remoteId to library.id } }
        .toMap()
    val failedHistoryLibraryIds = mutableSetOf<String>()
    val history = libraryIdByRemoteId.keys
        .flatMap { libraryId ->
            runCatching {
                api.getReadingHistory(
                    accessToken = accessToken,
                    libraryId = libraryId,
                )
            }.getOrElse { error ->
                when (error) {
                    is CancellationException,
                    is KavitaUnauthorizedException -> throw error
                }
                libraryIdByRemoteId[libraryId]?.let(failedHistoryLibraryIds::add)
                emptyList()
            }
        }
    val historyFetchFailed = failedHistoryLibraryIds.isNotEmpty()
    val historyEvents = history.toJournalEvents(itemByRemoteChapterId)
    val finishedHistoryItemIds = historyEvents
        .filter { event -> event.eventType == JournalReadingEventType.FINISHED }
        .mapTo(mutableSetOf(), JournalReadingEvent::itemId)
    val readingEventReplacementItemIds = if (historyFetchFailed) {
        entries
            .map(KavitaChapterCatalogEntry::item)
            .filterNot { item -> item.libraryId in failedHistoryLibraryIds }
            .mapTo(mutableSetOf(), ReadableItem::id)
    } else {
        null
    }
    val progressEvents = entries
        .filter { entry ->
            readingEventReplacementItemIds == null ||
                entry.item.id in readingEventReplacementItemIds
        }
        .mapNotNull(KavitaChapterCatalogEntry::toFinishedProgressJournalEvent)
        .filterNot { event -> event.itemId in finishedHistoryItemIds }
    repository.replaceCleanSourceJournalCache(
        sourceType = SourceType.KAVITA,
        events = historyEvents + progressEvents,
        entries = reviewLookup.reviews.toJournalEntries(itemByRemoteChapterId),
        preserveReviewFieldsWhenIncomingBlank = reviewLookup.shouldPreserveCachedReviews,
        preserveRatingWhenIncomingBlank = reviewLookup.shouldPreserveCachedReviews,
        preserveReadingEventsWhenIncomingIncomplete = historyFetchFailed,
        readingEventReplacementItemIds = readingEventReplacementItemIds,
    )
}

private data class KavitaReviewLookup(
    val reviews: List<KavitaUserReviewDto>,
    val shouldPreserveCachedReviews: Boolean,
)

suspend fun pushDirtyKavitaJournalEntries(
    api: KavitaApi,
    accessToken: String,
    entries: List<ReadableItem>,
    journalRepository: JournalRepository?,
) {
    val repository = journalRepository ?: return
    val itemById = entries.associateBy(ReadableItem::id)
    repository.getDirtyJournalEntriesForSource(SourceType.KAVITA).forEach { entry ->
        runCatching {
            val item = itemById[entry.itemId] ?: error("No Kavita catalog item for journal entry ${entry.itemId}")
            val chapterId = item.remoteId?.toIntOrNull()
                ?: error("Kavita journal entry ${entry.itemId} has no numeric chapter id")
            val chapterInfo = api.getChapterInfo(accessToken = accessToken, chapterId = chapterId.toString())
            val seriesId = chapterInfo.seriesId
                ?: error("Kavita chapter $chapterId has no series id")
            api.updateChapterRating(
                accessToken = accessToken,
                seriesId = seriesId,
                chapterId = chapterId,
                rating = entry.rating.toKavitaRating(),
            )
            if (entry.reviewText.isBlank()) {
                api.deleteChapterReview(accessToken = accessToken, chapterId = chapterId)
            } else {
                api.updateChapterReview(
                    accessToken = accessToken,
                    seriesId = seriesId,
                    chapterId = chapterId,
                    body = entry.reviewText,
                )
            }
        }.onSuccess {
            repository.markJournalEntrySyncState(entry.itemId, SyncState.CLEAN)
        }.onFailure { error ->
            when (error) {
                is CancellationException,
                is KavitaUnauthorizedException -> throw error
            }
            repository.markJournalEntrySyncState(entry.itemId, SyncState.FAILED)
        }
    }
}

private fun List<KavitaUserReviewDto>.toJournalEntries(
    itemByRemoteChapterId: Map<Int, ReadableItem>,
): List<ItemJournalEntry> = mapNotNull { review ->
    val chapterId = review.chapterId ?: return@mapNotNull null
    val item = itemByRemoteChapterId[chapterId] ?: return@mapNotNull null
    ItemJournalEntry(
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        rating = review.rating?.toInternalRating(),
        reviewText = review.body.orEmpty(),
        reviewedAtEpochMillis = review.createdUtc?.toEpochMillisOrNull(),
        updatedAtEpochMillis = review.createdUtc?.toEpochMillisOrNull() ?: System.currentTimeMillis(),
        remoteEntryId = review.id?.toString(),
        remoteUpdatedAtEpochMillis = review.createdUtc?.toEpochMillisOrNull(),
        syncState = SyncState.CLEAN,
    )
}

private fun List<KavitaReadingHistoryItemDto>.toJournalEvents(
    itemByRemoteChapterId: Map<Int, ReadableItem>,
): List<JournalReadingEvent> = flatMap { session ->
    session.chapters.orEmpty().mapNotNull { chapter ->
        val chapterId = chapter.chapterId ?: return@mapNotNull null
        val item = itemByRemoteChapterId[chapterId] ?: return@mapNotNull null
        val occurredAt = chapter.endTimeUtc?.toEpochMillisOrNull()
            ?: chapter.startTimeUtc?.toEpochMillisOrNull()
            ?: session.endTimeUtc?.toEpochMillisOrNull()
            ?: session.startTimeUtc?.toEpochMillisOrNull()
            ?: return@mapNotNull null
        JournalReadingEvent(
            id = "kavita:${session.sessionId ?: 0}:$chapterId:$occurredAt",
            itemId = item.id,
            sourceType = item.sourceType,
            libraryId = item.libraryId,
            itemTitle = item.title,
            coverUrl = item.coverUrl,
            itemKind = item.kind,
            format = item.format,
            eventType = if (chapter.completed == true) JournalReadingEventType.FINISHED else JournalReadingEventType.CONTINUED,
            fromPercent = chapter.startPage?.toPagePercent(chapter.totalPages),
            toPercent = chapter.endPage?.toPagePercent(chapter.totalPages),
            fromPage = chapter.startPage,
            toPage = chapter.endPage,
            pageCount = chapter.totalPages ?: item.pageCount,
            occurredAtEpochMillis = occurredAt,
            remoteEventId = session.sessionId?.toString(),
            remoteUpdatedAtEpochMillis = occurredAt,
            syncState = SyncState.CLEAN,
        )
    }
}

private fun KavitaChapterCatalogEntry.toFinishedProgressJournalEvent(): JournalReadingEvent? {
    val progress = progress?.takeIf { it.isFinished } ?: return null
    val occurredAt = progress.updatedAtEpochMillis ?: System.currentTimeMillis()
    val itemPageCount = item.pageCount?.takeIf { it > 0 }
    val toPercent = progress.percent
        ?: progress.page?.let { page -> itemPageCount?.let { count -> page.toDouble() / count.toDouble() } }
        ?: 1.0
    return JournalReadingEvent(
        id = "kavita:progress:${item.id}:$occurredAt",
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        eventType = JournalReadingEventType.FINISHED,
        fromPercent = 0.0,
        toPercent = toPercent.coerceIn(0.0, 1.0),
        fromPage = null,
        toPage = progress.page,
        pageCount = item.pageCount,
        occurredAtEpochMillis = occurredAt,
        remoteEventId = item.remoteId,
        remoteUpdatedAtEpochMillis = occurredAt,
        syncState = SyncState.CLEAN,
    )
}

private fun Int.toPagePercent(pageCount: Int?): Double? {
    val count = pageCount?.takeIf { it > 0 } ?: return null
    return toDouble() / count.toDouble()
}

private fun Float.toInternalRating(): Int? {
    if (this <= 0f) return null
    return (coerceIn(0f, 5f) * 2f).roundToInt().coerceIn(1, 10)
}

private fun Int?.toKavitaRating(): Float = this?.coerceIn(1, 10)?.toFloat()?.div(2f) ?: 0f

private fun String.toEpochMillisOrNull(): Long? = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrNull()
