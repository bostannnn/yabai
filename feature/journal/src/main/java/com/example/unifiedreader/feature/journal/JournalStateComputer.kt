package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.primaryAuthor
import com.example.unifiedreader.journal.classifyJournalMedia
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class JournalStateComputer(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun snapshot(
        events: List<JournalReadingEvent>,
        sessions: List<JournalReadingSession> = emptyList(),
        entries: List<ItemJournalEntry>,
        catalogItemsById: Map<String, ReadableItem> = emptyMap(),
    ): JournalSnapshot {
        val (monthStart, monthEnd) = periodBounds(JournalPeriod.MONTH, nowEpochMillis())
        val (yearStart, yearEnd) = periodBounds(JournalPeriod.YEAR, nowEpochMillis())
        val monthEvents = events.inPeriod(monthStart, monthEnd)
        val yearEvents = events.inPeriod(yearStart, yearEnd)
        val monthSessions = sessions.overlappingPeriod(monthStart, monthEnd)
        val yearSessions = sessions.overlappingPeriod(yearStart, yearEnd)
        val monthStats = computeStats(
            events = monthEvents,
            sessions = monthSessions,
            entries = entries.journalEntriesInPeriod(monthStart, monthEnd),
            catalogItemsById = catalogItemsById,
            periodStart = monthStart,
            periodEnd = monthEnd,
        )
        val yearStats = computeStats(
            events = yearEvents,
            sessions = yearSessions,
            entries = entries.journalEntriesInPeriod(yearStart, yearEnd),
            catalogItemsById = catalogItemsById,
            periodStart = yearStart,
            periodEnd = yearEnd,
        )
        return JournalSnapshot(
            events = events.sortedByDescending(JournalReadingEvent::occurredAtEpochMillis),
            sessions = sessions.sortedByDescending(JournalReadingSession::endedAtEpochMillis),
            entries = entries.sortedByDescending(ItemJournalEntry::updatedAtEpochMillis),
            itemSummaries = computeItemSummaries(events, sessions, entries, catalogItemsById),
            monthStats = monthStats,
            yearStats = yearStats,
        )
    }
}

fun computeItemSummaries(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession> = emptyList(),
    entries: List<ItemJournalEntry>,
    catalogItemsById: Map<String, ReadableItem> = emptyMap(),
): List<JournalItemSummary> {
    val entriesByItemId = entries.associateBy(ItemJournalEntry::itemId)
    val eventGroups = events.groupBy(JournalReadingEvent::itemId)
    val sessionGroups = sessions.groupBy(JournalReadingSession::itemId)
    val itemIds = (eventGroups.keys + sessionGroups.keys + entriesByItemId.keys).toSet()
    return itemIds.mapNotNull { itemId ->
        val itemEvents = eventGroups[itemId].orEmpty()
            .sortedBy(JournalReadingEvent::occurredAtEpochMillis)
        val itemSessions = sessionGroups[itemId].orEmpty()
            .sortedBy(JournalReadingSession::startedAtEpochMillis)
        val entry = entriesByItemId[itemId]
        val item = summaryItemRef(
            events = itemEvents,
            sessions = itemSessions,
            entry = entry,
            catalogItem = catalogItemsById[itemId],
        ) ?: return@mapNotNull null
        JournalItemSummary(
            item = item,
            events = itemEvents,
            sessions = itemSessions,
            catalogItem = catalogItemsById[itemId],
            entry = entry,
        )
    }.sortedByDescending(JournalItemSummary::lastActivityAtEpochMillis)
}

private fun summaryItemRef(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession>,
    entry: ItemJournalEntry?,
    catalogItem: ReadableItem?,
): JournalItemRef? {
    val newestEvent = events.maxByOrNull(JournalReadingEvent::occurredAtEpochMillis)
    val newestSession = sessions.maxByOrNull(JournalReadingSession::endedAtEpochMillis)
    val coverUrl = catalogItem?.coverUrl?.takeIf(String::isNotBlank)
        ?: entry?.coverUrl?.takeIf(String::isNotBlank)
        ?: events
            .asReversed()
            .firstOrNull { event -> !event.coverUrl.isNullOrBlank() }
            ?.coverUrl
        ?: sessions
            .asReversed()
            .firstOrNull { session -> !session.coverUrl.isNullOrBlank() }
            ?.coverUrl
    return (entry?.toItemRef() ?: newestEvent?.toItemRef() ?: newestSession?.toItemRef())?.copy(
        itemPrimaryText = catalogItem?.journalPrimaryText()?.takeIf(String::isNotBlank),
        itemSecondaryText = catalogItem?.journalSecondaryText()?.takeIf(String::isNotBlank),
        coverUrl = coverUrl,
    )
}

private fun ReadableItem.journalPrimaryText(): String = when (kind) {
    ReadableItemKind.BOOK -> title
    ReadableItemKind.COMIC -> comicMetadata?.series?.takeIf(String::isNotBlank) ?: title
}

private fun ReadableItem.journalSecondaryText(): String? = when (kind) {
    ReadableItemKind.BOOK -> bookMetadata?.primaryAuthor()?.takeIf(String::isNotBlank)
    ReadableItemKind.COMIC -> comicMetadata
        ?.issueTitle
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { issueTitle -> issueTitle == journalPrimaryText() }
}

fun computeStats(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession> = emptyList(),
    entries: List<ItemJournalEntry> = emptyList(),
    catalogItemsById: Map<String, ReadableItem> = emptyMap(),
    periodStart: Long? = null,
    periodEnd: Long? = null,
): JournalStats {
    val finishedEvents = events
        .filter { event -> event.eventType == JournalReadingEventType.FINISHED }
        .distinctBy(JournalReadingEvent::itemId)
    val readingTimeMillis = sessions.sumOf { session ->
        session.activeDurationWithin(periodStart, periodEnd)
    }
    val readingTimeByMedia = sessions.groupBy(JournalReadingSession::mediaType)
        .mapValues { (_, mediaSessions) ->
            mediaSessions.sumOf { session -> session.activeDurationWithin(periodStart, periodEnd) }
        }
    val pagesReadByMedia = computePagesReadByMedia(events, sessions)
    val activeDays = (
        events.map(JournalReadingEvent::localDayKey) +
            sessions.flatMap { session -> session.activeDayKeys(periodStart, periodEnd) }
        ).distinct().size
    val activeDaysByMedia = computeActiveDaysByMedia(events, sessions, periodStart, periodEnd)
    return JournalStats(
        booksFinished = finishedEvents.count { event -> event.mediaType() == JournalMediaType.BOOK },
        comicsFinished = finishedEvents.count { event -> event.mediaType() == JournalMediaType.COMIC },
        pagesRead = computePagesRead(events, sessions),
        bookPagesRead = pagesReadByMedia[JournalMediaType.BOOK] ?: 0,
        comicPagesRead = pagesReadByMedia[JournalMediaType.COMIC] ?: 0,
        readingTimeMillis = readingTimeMillis,
        bookReadingTimeMillis = readingTimeByMedia[JournalMediaType.BOOK] ?: 0L,
        comicReadingTimeMillis = readingTimeByMedia[JournalMediaType.COMIC] ?: 0L,
        activeDays = activeDays,
        bookActiveDays = activeDaysByMedia[JournalMediaType.BOOK] ?: 0,
        comicActiveDays = activeDaysByMedia[JournalMediaType.COMIC] ?: 0,
        sourceCounts = events.groupingBy(JournalReadingEvent::sourceType).eachCount().enumOrder(SourceType.entries),
        formatCounts = events.groupingBy(JournalReadingEvent::format).eachCount().enumOrder(FormatType.entries),
        ratingCountsByMediaType = entries
            .mapNotNull { entry ->
                val rating = entry.rating ?: return@mapNotNull null
                val mediaType = entry.mediaType(catalogItemsById[entry.itemId])
                mediaType to rating
            }
            .groupBy({ (mediaType, _) -> mediaType }, { (_, rating) -> rating })
            .mapValues { (_, ratings) -> ratings.groupingBy { it }.eachCount().toSortedMap(compareByDescending { it }) },
    )
}

fun computePeriodOverview(
    snapshot: JournalSnapshot,
    period: JournalPeriod,
    referenceEpochMillis: Long,
): JournalPeriodOverview {
    val (periodStart, periodEnd) = periodBounds(period, referenceEpochMillis)
    val periodEvents = snapshot.events.inPeriod(periodStart, periodEnd)
    val periodSessions = snapshot.sessions.overlappingPeriod(periodStart, periodEnd)
    val periodEntries = snapshot.entries.journalEntriesInPeriod(periodStart, periodEnd)
    val readItemIds = (
        periodEvents.map(JournalReadingEvent::itemId) +
            periodSessions.map(JournalReadingSession::itemId)
        ).toSet()
    val finishedItems = snapshot.itemSummaries
        .filter { summary -> summary.finishedAtEpochMillis in periodStart until periodEnd }
        .sortedByDescending(JournalItemSummary::finishedAtEpochMillis)
    val activeItems = snapshot.itemSummaries
        .filter { summary -> summary.item.itemId in readItemIds }
        .sortedByDescending(JournalItemSummary::lastActivityAtEpochMillis)
    return JournalPeriodOverview(
        period = period,
        periodStartEpochMillis = periodStart,
        periodEndEpochMillis = periodEnd,
        stats = computeStats(
            events = periodEvents,
            sessions = periodSessions,
            entries = periodEntries,
            catalogItemsById = snapshot.itemSummaries
                .mapNotNull { summary -> summary.catalogItem?.let { item -> item.id to item } }
                .toMap(),
            periodStart = periodStart,
            periodEnd = periodEnd,
        ),
        activityDays = computeActivityDays(periodEvents),
        finishedItems = finishedItems,
        activeItems = activeItems,
        averageReadingTimeBuckets = computeAverageReadingTimeBuckets(
            sessions = periodSessions,
            period = period,
            periodStart = periodStart,
            periodEnd = periodEnd,
        ),
        topFacets = computeTopFacetSections(
            activeItems = activeItems,
        ),
    )
}

fun JournalSnapshot.firstJournalAtEpochMillis(): Long? = listOfNotNull(
    events.minOfOrNull(JournalReadingEvent::occurredAtEpochMillis),
    sessions.minOfOrNull(JournalReadingSession::startedAtEpochMillis),
    entries.minOfOrNull(ItemJournalEntry::updatedAtEpochMillis),
).minOrNull()

fun computeActivityDays(
    events: List<JournalReadingEvent>,
): List<JournalActivityDay> = events
    .groupBy(JournalReadingEvent::localDayKey)
    .map { (dayKey, dayEvents) ->
        val calendar = Calendar.getInstance(Locale.US).apply {
            timeInMillis = dayEvents.minOf(JournalReadingEvent::occurredAtEpochMillis)
        }
        JournalActivityDay(
            localDayKey = dayKey,
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
            pagesRead = dayEvents.sumOf(JournalReadingEvent::positivePageDelta),
            eventCount = dayEvents.size,
            finishedBooks = dayEvents.count { event ->
                event.eventType == JournalReadingEventType.FINISHED &&
                    event.mediaType() == JournalMediaType.BOOK
            },
            finishedComics = dayEvents.count { event ->
                event.eventType == JournalReadingEventType.FINISHED &&
                    event.mediaType() == JournalMediaType.COMIC
            },
        )
    }
    .sortedWith(compareBy<JournalActivityDay> { it.year }.thenBy { it.month }.thenBy { it.dayOfMonth })

private fun computeAverageReadingTimeBuckets(
    sessions: List<JournalReadingSession>,
    period: JournalPeriod,
    periodStart: Long,
    periodEnd: Long,
): List<JournalTimeBucket> {
    val totals = mutableMapOf<Int, Long>()
    sessions.forEach { session ->
        session.splitIntoBuckets(
            period = period,
            periodStart = periodStart,
            periodEnd = periodEnd,
        ).forEach { (key, duration) ->
            totals[key] = (totals[key] ?: 0L) + duration
        }
    }
    return when (period) {
        JournalPeriod.MONTH -> (0..23).map { hour ->
            JournalTimeBucket(
                key = hour,
                label = hour.toString().padStart(2, '0'),
                totalMillis = totals[hour] ?: 0L,
                averageMillis = (totals[hour] ?: 0L) / daysInPeriod(periodStart).coerceAtLeast(1),
            )
        }
        JournalPeriod.YEAR -> (0..11).map { month ->
            JournalTimeBucket(
                key = month,
                label = DateFormatSymbolsHolder.monthShortNames[month],
                totalMillis = totals[month] ?: 0L,
                averageMillis = (totals[month] ?: 0L) / daysInMonthBucket(periodStart, month).coerceAtLeast(1),
            )
        }
    }
}

private fun computeTopFacetSections(
    activeItems: List<JournalItemSummary>,
): List<JournalFacetSection> {
    return listOf(
        JournalFacetSection(
            title = "Book genres",
            mediaType = JournalMediaType.BOOK,
            facetType = JournalFacetType.GENRE,
            rows = activeItems.topFacetRows(JournalMediaType.BOOK) { item -> item.facetMetadata().genres },
        ),
        JournalFacetSection(
            title = "Book authors",
            mediaType = JournalMediaType.BOOK,
            facetType = JournalFacetType.AUTHOR,
            rows = activeItems.topFacetRows(JournalMediaType.BOOK) { item -> item.facetMetadata().authors },
        ),
        JournalFacetSection(
            title = "Comic genres",
            mediaType = JournalMediaType.COMIC,
            facetType = JournalFacetType.GENRE,
            rows = activeItems.topFacetRows(JournalMediaType.COMIC) { item -> item.facetMetadata().genres },
        ),
        JournalFacetSection(
            title = "Comic tags",
            mediaType = JournalMediaType.COMIC,
            facetType = JournalFacetType.TAG,
            rows = activeItems.topFacetRows(JournalMediaType.COMIC) { item -> item.facetMetadata().tags },
        ),
        JournalFacetSection(
            title = "Comic writers",
            mediaType = JournalMediaType.COMIC,
            facetType = JournalFacetType.WRITER,
            rows = activeItems.topFacetRows(JournalMediaType.COMIC) { item -> item.facetMetadata().writers },
        ),
        JournalFacetSection(
            title = "Comic pencillers",
            mediaType = JournalMediaType.COMIC,
            facetType = JournalFacetType.PENCILLER,
            rows = activeItems.topFacetRows(JournalMediaType.COMIC) { item -> item.facetMetadata().pencillers },
        ),
    )
}

private fun List<JournalItemSummary>.topFacetRows(
    mediaType: JournalMediaType,
    values: (ReadableItem) -> List<String>,
): List<JournalFacetRow> = filter { summary ->
    summary.item.mediaType() == mediaType
}.flatMap { summary ->
    val item = summary.catalogItem
    val labels = if (item == null) emptyList() else values(item)
    labels.ifEmpty { listOf(UNKNOWN_FACET_LABEL) }.map { label ->
        label to summary.item.itemId
    }
}.groupBy({ (label, _) -> label }, { (_, itemId) -> itemId })
    .map { (label, itemIds) ->
        JournalFacetRow(
            label = label,
            itemIds = itemIds.distinct().sorted(),
        )
    }
    .sortedWith(compareByDescending<JournalFacetRow> { it.count }.thenBy { it.label })

internal fun List<JournalReadingEvent>.inPeriod(
    startInclusive: Long,
    endExclusive: Long,
): List<JournalReadingEvent> = filter { event ->
    event.occurredAtEpochMillis in startInclusive until endExclusive
}

internal fun List<JournalReadingSession>.overlappingPeriod(
    startInclusive: Long,
    endExclusive: Long,
): List<JournalReadingSession> = filter { session ->
    session.startedAtEpochMillis < endExclusive && session.endedAtEpochMillis > startInclusive
}

internal fun List<ItemJournalEntry>.journalEntriesInPeriod(
    startInclusive: Long,
    endExclusive: Long,
): List<ItemJournalEntry> = filter { entry ->
    val journaledAt = entry.reviewedAtEpochMillis ?: entry.updatedAtEpochMillis
    journaledAt in startInclusive until endExclusive
}

internal fun JournalReadingSession.positivePageDelta(): Int {
    val fromPageValue = fromPage
    val toPageValue = toPage
    val explicitDelta = if (fromPageValue != null && toPageValue != null) {
        (toPageValue - fromPageValue).coerceAtLeast(0)
    } else {
        null
    }
    if (explicitDelta != null && explicitDelta > 0) return explicitDelta
    val count = pageCount ?: return 0
    val from = fromPercent ?: 0.0
    val to = toPercent ?: return 0
    return ((to - from).coerceAtLeast(0.0) * count).roundToInt()
}

internal fun JournalReadingEvent.positivePageDelta(): Int {
    val fromPageValue = fromPage
    val toPageValue = toPage
    val explicitDelta = if (fromPageValue != null && toPageValue != null) {
        (toPageValue - fromPageValue).coerceAtLeast(0)
    } else {
        null
    }
    if (explicitDelta != null && explicitDelta > 0) return explicitDelta
    val count = pageCount ?: return 0
    val from = fromPercent ?: 0.0
    val to = toPercent ?: return 0
    return ((to - from).coerceAtLeast(0.0) * count).roundToInt()
}

internal fun JournalReadingEvent.localDayKey(): Int {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = occurredAtEpochMillis
    }
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
}

private fun computePagesRead(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession>,
): Int {
    return computePagesReadByMedia(events, sessions).values.sum()
}

private fun computePagesReadByMedia(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession>,
): Map<JournalMediaType, Int> {
    val eventPagesByItem = events
        .groupBy(JournalReadingEvent::itemId)
        .mapValues { (_, itemEvents) -> itemEvents.sumOf(JournalReadingEvent::positivePageDelta) }
    val sessionPagesByItem = sessions
        .groupBy(JournalReadingSession::itemId)
        .mapValues { (_, itemSessions) -> itemSessions.sumOf(JournalReadingSession::positivePageDelta) }
    val mediaByItem = events
        .associate { event -> event.itemId to event.mediaType() } +
        sessions.associate { session -> session.itemId to session.mediaType }
    return (eventPagesByItem.keys + sessionPagesByItem.keys)
        .groupBy { itemId -> mediaByItem[itemId] ?: JournalMediaType.BOOK }
        .mapValues { (_, itemIds) ->
            itemIds.sumOf { itemId ->
                maxOf(eventPagesByItem[itemId] ?: 0, sessionPagesByItem[itemId] ?: 0)
            }
        }
}

private fun computeActiveDaysByMedia(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession>,
    periodStart: Long?,
    periodEnd: Long?,
): Map<JournalMediaType, Int> {
    val dayKeysByMedia = mutableMapOf<JournalMediaType, MutableSet<Int>>()
    events.forEach { event ->
        dayKeysByMedia
            .getOrPut(event.mediaType()) { mutableSetOf() }
            .add(event.localDayKey())
    }
    sessions.forEach { session ->
        dayKeysByMedia
            .getOrPut(session.mediaType) { mutableSetOf() }
            .addAll(session.activeDayKeys(periodStart, periodEnd))
    }
    return dayKeysByMedia.mapValues { (_, dayKeys) -> dayKeys.size }
}

private fun JournalReadingSession.activeDayKeys(
    periodStart: Long?,
    periodEnd: Long?,
): List<Int> {
    val start = maxOf(startedAtEpochMillis, periodStart ?: startedAtEpochMillis)
    val end = minOf(endedAtEpochMillis, periodEnd ?: endedAtEpochMillis)
    if (end <= start) return emptyList()
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = start
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val keys = mutableListOf<Int>()
    while (calendar.timeInMillis < end) {
        keys += calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return keys
}

private fun JournalReadingSession.activeDurationWithin(
    startInclusive: Long?,
    endExclusive: Long?,
): Long {
    val activeDuration = durationMillis.coerceAtLeast(0L)
    if (activeDuration <= 0L) return 0L
    if (startInclusive == null || endExclusive == null) return activeDuration
    val wallDuration = (endedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
    if (wallDuration <= 0L) return 0L
    val clippedWallDuration = (minOf(endedAtEpochMillis, endExclusive) - maxOf(startedAtEpochMillis, startInclusive))
        .coerceAtLeast(0L)
    if (clippedWallDuration <= 0L) return 0L
    return (activeDuration.toDouble() * clippedWallDuration.toDouble() / wallDuration.toDouble()).roundToLong()
}

private fun JournalReadingSession.splitIntoBuckets(
    period: JournalPeriod,
    periodStart: Long,
    periodEnd: Long,
): List<Pair<Int, Long>> {
    var cursor = maxOf(startedAtEpochMillis, periodStart)
    val end = minOf(endedAtEpochMillis, periodEnd)
    if (end <= cursor) return emptyList()
    val result = mutableListOf<Pair<Int, Long>>()
    while (cursor < end) {
        val calendar = Calendar.getInstance(Locale.US).apply { timeInMillis = cursor }
        val key = if (period == JournalPeriod.MONTH) {
            calendar.get(Calendar.HOUR_OF_DAY)
        } else {
            calendar.get(Calendar.MONTH)
        }
        val nextBoundary = Calendar.getInstance(Locale.US).apply {
            timeInMillis = cursor
            if (period == JournalPeriod.MONTH) {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            } else {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, 1)
            }
        }.timeInMillis
        val segmentEnd = minOf(end, nextBoundary)
        val wallDuration = (endedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
        val activeDuration = durationMillis.coerceAtLeast(0L)
        val segmentWallDuration = segmentEnd - cursor
        val segmentActiveDuration = if (wallDuration > 0L && activeDuration > 0L) {
            (activeDuration.toDouble() * segmentWallDuration.toDouble() / wallDuration.toDouble()).roundToLong()
        } else {
            0L
        }
        result += key to segmentActiveDuration
        cursor = segmentEnd
    }
    return result
}

private fun daysInPeriod(periodStart: Long): Int {
    val calendar = Calendar.getInstance(Locale.US).apply { timeInMillis = periodStart }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun daysInMonthBucket(yearStart: Long, month: Int): Int {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = yearStart
        set(Calendar.MONTH, month)
    }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun JournalReadingEvent.mediaType(): JournalMediaType = classifyJournalMedia(
    sourceType = sourceType,
    itemKind = itemKind,
    format = format,
)

private fun JournalItemRef.mediaType(): JournalMediaType = classifyJournalMedia(
    sourceType = sourceType,
    itemKind = itemKind,
    format = format,
)

private fun ItemJournalEntry.mediaType(
    catalogItem: ReadableItem?,
): JournalMediaType = classifyJournalMedia(
    sourceType = catalogItem?.sourceType ?: sourceType,
    itemKind = catalogItem?.kind ?: itemKind,
    format = catalogItem?.format ?: format,
)

private object DateFormatSymbolsHolder {
    val monthShortNames: Array<String> = java.text.DateFormatSymbols.getInstance(Locale.US).shortMonths
}

private fun <K> Map<K, Int>.enumOrder(
    entries: List<K>,
): Map<K, Int> = entries.mapNotNull { entry ->
    get(entry)?.let { count -> entry to count }
}.toMap()

private const val UNKNOWN_FACET_LABEL = "Unknown"
