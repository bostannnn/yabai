package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.primaryAuthor
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class JournalStateComputer(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun snapshot(
        events: List<JournalReadingEvent>,
        entries: List<ItemJournalEntry>,
        catalogItemsById: Map<String, ReadableItem> = emptyMap(),
    ): JournalSnapshot {
        val (monthStart, monthEnd) = periodBounds(JournalPeriod.MONTH, nowEpochMillis())
        val (yearStart, yearEnd) = periodBounds(JournalPeriod.YEAR, nowEpochMillis())
        val monthEvents = events.inPeriod(monthStart, monthEnd)
        val yearEvents = events.inPeriod(yearStart, yearEnd)
        val monthStats = computeStats(monthEvents, entries.journalEntriesInPeriod(monthStart, monthEnd))
        val yearStats = computeStats(yearEvents, entries.journalEntriesInPeriod(yearStart, yearEnd))
        return JournalSnapshot(
            events = events.sortedByDescending(JournalReadingEvent::occurredAtEpochMillis),
            entries = entries.sortedByDescending(ItemJournalEntry::updatedAtEpochMillis),
            itemSummaries = computeItemSummaries(events, entries, catalogItemsById),
            monthStats = monthStats,
            yearStats = yearStats,
        )
    }
}

fun computeItemSummaries(
    events: List<JournalReadingEvent>,
    entries: List<ItemJournalEntry>,
    catalogItemsById: Map<String, ReadableItem> = emptyMap(),
): List<JournalItemSummary> {
    val entriesByItemId = entries.associateBy(ItemJournalEntry::itemId)
    val eventGroups = events.groupBy(JournalReadingEvent::itemId)
    val itemIds = (eventGroups.keys + entriesByItemId.keys).toSet()
    return itemIds.mapNotNull { itemId ->
        val itemEvents = eventGroups[itemId].orEmpty()
            .sortedBy(JournalReadingEvent::occurredAtEpochMillis)
        val entry = entriesByItemId[itemId]
        val item = summaryItemRef(
            events = itemEvents,
            entry = entry,
            catalogItem = catalogItemsById[itemId],
        ) ?: return@mapNotNull null
        JournalItemSummary(
            item = item,
            events = itemEvents,
            entry = entry,
        )
    }.sortedByDescending(JournalItemSummary::lastActivityAtEpochMillis)
}

private fun summaryItemRef(
    events: List<JournalReadingEvent>,
    entry: ItemJournalEntry?,
    catalogItem: ReadableItem?,
): JournalItemRef? {
    val newestEvent = events.maxByOrNull(JournalReadingEvent::occurredAtEpochMillis)
    val coverUrl = catalogItem?.coverUrl?.takeIf(String::isNotBlank)
        ?: entry?.coverUrl?.takeIf(String::isNotBlank)
        ?: events
            .asReversed()
            .firstOrNull { event -> !event.coverUrl.isNullOrBlank() }
            ?.coverUrl
    return (entry?.toItemRef() ?: newestEvent?.toItemRef())?.copy(
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
    entries: List<ItemJournalEntry> = emptyList(),
): JournalStats {
    val finishedEvents = events
        .filter { event -> event.eventType == JournalReadingEventType.FINISHED }
        .distinctBy(JournalReadingEvent::itemId)
    return JournalStats(
        booksFinished = finishedEvents.count { event -> event.itemKind == ReadableItemKind.BOOK },
        comicsFinished = finishedEvents.count { event -> event.itemKind == ReadableItemKind.COMIC },
        pagesRead = events.sumOf(JournalReadingEvent::positivePageDelta),
        activeDays = events.map(JournalReadingEvent::localDayKey).distinct().size,
        sourceCounts = events.groupingBy(JournalReadingEvent::sourceType).eachCount().enumOrder(SourceType.entries),
        formatCounts = events.groupingBy(JournalReadingEvent::format).eachCount().enumOrder(FormatType.entries),
        ratingCounts = entries.mapNotNull(ItemJournalEntry::rating).groupingBy { it }.eachCount().toSortedMap(compareByDescending { it }),
    )
}

fun computePeriodOverview(
    snapshot: JournalSnapshot,
    period: JournalPeriod,
    referenceEpochMillis: Long,
): JournalPeriodOverview {
    val (periodStart, periodEnd) = periodBounds(period, referenceEpochMillis)
    val periodEvents = snapshot.events.inPeriod(periodStart, periodEnd)
    val periodEntries = snapshot.entries.journalEntriesInPeriod(periodStart, periodEnd)
    return JournalPeriodOverview(
        period = period,
        periodStartEpochMillis = periodStart,
        periodEndEpochMillis = periodEnd,
        stats = computeStats(periodEvents, periodEntries),
        activityDays = computeActivityDays(periodEvents),
        finishedItems = snapshot.itemSummaries
            .filter { summary -> summary.finishedAtEpochMillis in periodStart until periodEnd }
            .sortedByDescending(JournalItemSummary::finishedAtEpochMillis),
    )
}

fun JournalSnapshot.firstJournalAtEpochMillis(): Long? = listOfNotNull(
    events.minOfOrNull(JournalReadingEvent::occurredAtEpochMillis),
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
                    event.itemKind == ReadableItemKind.BOOK
            },
            finishedComics = dayEvents.count { event ->
                event.eventType == JournalReadingEventType.FINISHED &&
                    event.itemKind == ReadableItemKind.COMIC
            },
        )
    }
    .sortedWith(compareBy<JournalActivityDay> { it.year }.thenBy { it.month }.thenBy { it.dayOfMonth })

internal fun List<JournalReadingEvent>.inPeriod(
    startInclusive: Long,
    endExclusive: Long,
): List<JournalReadingEvent> = filter { event ->
    event.occurredAtEpochMillis in startInclusive until endExclusive
}

internal fun List<ItemJournalEntry>.journalEntriesInPeriod(
    startInclusive: Long,
    endExclusive: Long,
): List<ItemJournalEntry> = filter { entry ->
    val journaledAt = entry.reviewedAtEpochMillis ?: entry.updatedAtEpochMillis
    journaledAt in startInclusive until endExclusive
}

internal fun JournalReadingEvent.positivePageDelta(): Int {
    val explicitDelta = if (fromPage != null && toPage != null) {
        (toPage - fromPage).coerceAtLeast(0)
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

private fun <K> Map<K, Int>.enumOrder(
    entries: List<K>,
): Map<K, Int> = entries.mapNotNull { entry ->
    get(entry)?.let { count -> entry to count }
}.toMap()
