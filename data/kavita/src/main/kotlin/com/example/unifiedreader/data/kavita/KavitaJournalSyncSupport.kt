package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingEventType
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.journalMediaType
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    val historySessions = history.toJournalSessions(itemByRemoteChapterId)
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
    val likelyRatingChapterIds = buildLikelyRatingChapterIds(
        reviewChapterIds = reviewLookup.reviews.mapNotNull(KavitaUserReviewDto::chapterId),
        history = history,
        progressEvents = progressEvents,
        itemByRemoteChapterId = itemByRemoteChapterId,
    )
    var ratingFetchFailed = false
    val nativeRatingsByChapterId = likelyRatingChapterIds
        .mapNotNull { chapterId ->
            runCatching {
                chapterId to api.getChapterDetailPlus(
                    accessToken = accessToken,
                    chapterId = chapterId,
                )
            }.getOrElse { error ->
                when (error) {
                    is CancellationException,
                    is KavitaUnauthorizedException -> throw error
                }
                ratingFetchFailed = true
                null
            }
        }
        .toMap()
    val reviewEntries = reviewLookup.reviews.toJournalEntries(itemByRemoteChapterId)
    val ratingOnlyPreservationExcludedItemIds = nativeRatingsByChapterId.keys
        .mapNotNull { chapterId -> itemByRemoteChapterId[chapterId]?.id }
        .toSet()
    repository.replaceCleanSourceJournalCache(
        sourceType = SourceType.KAVITA,
        events = historyEvents + progressEvents,
        entries = mergeKavitaJournalEntries(
            reviewEntries = reviewEntries,
            nativeRatingsByChapterId = nativeRatingsByChapterId,
            itemByRemoteChapterId = itemByRemoteChapterId,
        ),
        sessions = historySessions,
        preserveReviewFieldsWhenIncomingBlank = reviewLookup.shouldPreserveCachedReviews,
        preserveRatingWhenIncomingBlank = reviewLookup.shouldPreserveCachedReviews || ratingFetchFailed,
        preserveReadingEventsWhenIncomingIncomplete = historyFetchFailed,
        preserveReadingSessionsWhenIncomingIncomplete = historyFetchFailed,
        readingEventReplacementItemIds = readingEventReplacementItemIds,
        readingSessionReplacementItemIds = readingEventReplacementItemIds,
        preserveRatingOnlyEntriesWhenIncomingMissing = true,
        ratingOnlyPreservationExcludedItemIds = ratingOnlyPreservationExcludedItemIds,
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
            val parsedReview = entry.reviewText.parseYabaiJournalReviewBody()
            val reviewText = parsedReview.reviewText
            val rating = entry.rating ?: parsedReview.rating
            if (reviewText.isBlank()) {
                if (rating == null) {
                    api.updateChapterRating(
                        accessToken = accessToken,
                        seriesId = seriesId,
                        chapterId = chapterId,
                        rating = 0f,
                    )
                    api.deleteChapterReview(accessToken = accessToken, chapterId = chapterId)
                } else {
                    api.deleteChapterReview(accessToken = accessToken, chapterId = chapterId)
                    api.updateChapterRating(
                        accessToken = accessToken,
                        seriesId = seriesId,
                        chapterId = chapterId,
                        rating = rating.toKavitaRating(),
                    )
                }
            } else {
                api.updateChapterRating(
                    accessToken = accessToken,
                    seriesId = seriesId,
                    chapterId = chapterId,
                    rating = rating.toKavitaRating(),
                )
                api.updateChapterReview(
                    accessToken = accessToken,
                    seriesId = seriesId,
                    chapterId = chapterId,
                    body = reviewText,
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

private fun buildLikelyRatingChapterIds(
    reviewChapterIds: List<Int>,
    history: List<KavitaReadingHistoryItemDto>,
    progressEvents: List<JournalReadingEvent>,
    itemByRemoteChapterId: Map<Int, ReadableItem>,
): Set<Int> {
    val chapterIds = reviewChapterIds.toMutableSet()
    history.forEach { session ->
        session.chapters.orEmpty().mapNotNullTo(chapterIds, KavitaReadingHistoryChapterItemDto::chapterId)
    }
    val chapterIdByItemId = itemByRemoteChapterId.entries.associate { (chapterId, item) -> item.id to chapterId }
    progressEvents.mapNotNullTo(chapterIds) { event -> chapterIdByItemId[event.itemId] }
    return chapterIds
}

private fun mergeKavitaJournalEntries(
    reviewEntries: List<ItemJournalEntry>,
    nativeRatingsByChapterId: Map<Int, KavitaChapterDetailPlusDto>,
    itemByRemoteChapterId: Map<Int, ReadableItem>,
): List<ItemJournalEntry> {
    val entriesByItemId = reviewEntries.associateBy(ItemJournalEntry::itemId).toMutableMap()
    nativeRatingsByChapterId.forEach { (chapterId, detail) ->
        val nativeRating = detail.toInternalRating() ?: return@forEach
        val item = itemByRemoteChapterId[chapterId] ?: return@forEach
        val existing = entriesByItemId[item.id]
        entriesByItemId[item.id] = existing?.copy(rating = nativeRating)
            ?: item.toRatingOnlyJournalEntry(rating = nativeRating)
    }
    return entriesByItemId.values.toList()
}

private fun List<KavitaUserReviewDto>.toJournalEntries(
    itemByRemoteChapterId: Map<Int, ReadableItem>,
): List<ItemJournalEntry> = mapNotNull { review ->
    val chapterId = review.chapterId ?: return@mapNotNull null
    val item = itemByRemoteChapterId[chapterId] ?: return@mapNotNull null
    val parsedReview = review.body.orEmpty().parseYabaiJournalReviewBody()
    ItemJournalEntry(
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        rating = parsedReview.rating ?: review.rating?.toInternalRating(),
        reviewText = parsedReview.reviewText,
        reviewedAtEpochMillis = review.createdUtc?.toEpochMillisOrNull(),
        updatedAtEpochMillis = review.createdUtc?.toEpochMillisOrNull() ?: System.currentTimeMillis(),
        remoteEntryId = review.id?.toString(),
        remoteUpdatedAtEpochMillis = review.createdUtc?.toEpochMillisOrNull(),
        syncState = SyncState.CLEAN,
    )
}

private fun ReadableItem.toRatingOnlyJournalEntry(
    rating: Int,
): ItemJournalEntry {
    val now = System.currentTimeMillis()
    return ItemJournalEntry(
        itemId = id,
        sourceType = sourceType,
        libraryId = libraryId,
        itemTitle = title,
        coverUrl = coverUrl,
        itemKind = kind,
        format = format,
        rating = rating,
        reviewText = "",
        reviewedAtEpochMillis = null,
        updatedAtEpochMillis = now,
        remoteEntryId = null,
        remoteUpdatedAtEpochMillis = now,
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

private fun List<KavitaReadingHistoryItemDto>.toJournalSessions(
    itemByRemoteChapterId: Map<Int, ReadableItem>,
): List<JournalReadingSession> = flatMap { session ->
    session.chapters.orEmpty().mapNotNull { chapter ->
        val chapterId = chapter.chapterId ?: return@mapNotNull null
        val item = itemByRemoteChapterId[chapterId] ?: return@mapNotNull null
        val startedAt = chapter.startTimeUtc?.toEpochMillisOrNull()
            ?: session.startTimeUtc?.toEpochMillisOrNull()
            ?: return@mapNotNull null
        val durationMillis = (chapter.durationSeconds ?: session.durationSeconds)
            ?.takeIf { seconds -> seconds > 0 }
            ?.toLong()
            ?.times(1_000L)
        val endedAt = chapter.endTimeUtc?.toEpochMillisOrNull()
            ?: durationMillis?.let { duration -> startedAt + duration }
            ?: session.endTimeUtc?.toEpochMillisOrNull()
            ?: return@mapNotNull null
        val safeStartedAt = minOf(startedAt, endedAt)
        val safeEndedAt = maxOf(startedAt, endedAt)
        val safeDuration = durationMillis ?: (safeEndedAt - safeStartedAt)
        if (safeDuration <= 0L) return@mapNotNull null
        val pageCount = chapter.totalPages ?: session.totalPages ?: item.pageCount
        JournalReadingSession(
            id = "kavita:session:${session.sessionId ?: 0}:$chapterId:$safeStartedAt:$safeEndedAt",
            itemId = item.id,
            sourceType = item.sourceType,
            libraryId = item.libraryId,
            itemTitle = item.title,
            coverUrl = item.coverUrl,
            itemKind = item.kind,
            format = item.format,
            mediaType = item.journalMediaType(),
            startedAtEpochMillis = safeStartedAt,
            endedAtEpochMillis = safeEndedAt,
            durationMillis = safeDuration,
            fromPercent = chapter.startPage?.toPagePercent(pageCount),
            toPercent = chapter.endPage?.toPagePercent(pageCount),
            fromPage = chapter.startPage,
            toPage = chapter.endPage,
            pageCount = pageCount,
            remoteSessionId = session.sessionId?.toString(),
            remoteUpdatedAtEpochMillis = safeEndedAt,
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

private fun KavitaChapterDetailPlusDto.toInternalRating(): Int? =
    if (hasBeenRated == true) rating?.toInternalRating() else null

private fun Int?.toKavitaRating(): Float = this?.coerceIn(1, 10)?.toFloat()?.div(2f) ?: 0f

private data class ParsedKavitaReviewBody(
    val reviewText: String,
    val rating: Int?,
)

private fun String.parseYabaiJournalReviewBody(): ParsedKavitaReviewBody {
    val markerMatch = YABAI_JOURNAL_MARKER_REGEX.findAll(this).lastOrNull()
        ?: return ParsedKavitaReviewBody(
            reviewText = trim(),
            rating = null,
        )
    val rating = markerMatch.groupValues
        .getOrNull(1)
        ?.parseYabaiJournalRating()
    val reviewText = removeRange(markerMatch.range)
        .lines()
        .dropLastWhile { line -> line.isBlank() }
        .joinToString("\n")
        .trim()
    return ParsedKavitaReviewBody(
        reviewText = reviewText,
        rating = rating,
    )
}

private fun String.parseYabaiJournalRating(): Int? = runCatching {
    val metadata = yabaiJournalJson.decodeFromString(JsonObject.serializer(), this)
    metadata["rating"]
        ?.jsonPrimitive
        ?.intOrNull
        ?.coerceIn(1, 10)
}.getOrNull()

private fun String.toEpochMillisOrNull(): Long? = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrNull()

private val YABAI_JOURNAL_MARKER_REGEX = Regex("""(?m)^\s*YABAI_JOURNAL:(\{[^\r\n]*\})\s*$""")

private val yabaiJournalJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
