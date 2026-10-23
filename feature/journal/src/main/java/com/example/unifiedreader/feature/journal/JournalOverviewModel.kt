package com.example.unifiedreader.feature.journal

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The journal's figures that the screen draws, worked out from a period overview. Kept pure so
 * they can be tested without Compose.
 */

/** How strongly a calendar day is shaded: 0 for no reading, then four steps up to [maxValue]. */
internal fun journalHeatLevel(value: Int, maxValue: Int): Int {
    if (value <= 0) return 0
    if (maxValue <= 0) return 1
    val share = value.toFloat() / maxValue.toFloat()
    return when {
        share <= 0.25f -> 1
        share <= 0.5f -> 2
        share <= 0.75f -> 3
        else -> 4
    }
}

/** A day's weight on the calendar: its pages, and at least 1 for a day with any reading. */
internal val JournalActivityDay.heatValue: Int
    get() = pagesRead.coerceAtLeast(if (eventCount > 0) 1 else 0)

internal val JournalActivityDay.finishedCount: Int
    get() = finishedBooks + finishedComics

internal data class JournalReadingRun(
    val length: Int,
    val first: JournalActivityDay,
    val last: JournalActivityDay,
)

/** The longest stretch of consecutive reading days; the earliest wins a tie. */
internal fun longestReadingRun(days: List<JournalActivityDay>): JournalReadingRun? {
    val sorted = days.filter { day -> day.heatValue > 0 }.sortedBy(JournalActivityDay::epochDay)
    var best: JournalReadingRun? = null
    var runStart = 0
    sorted.indices.forEach { index ->
        if (index > 0 && sorted[index].epochDay != sorted[index - 1].epochDay + 1) runStart = index
        val length = index - runStart + 1
        if (best == null || length > best!!.length) {
            best = JournalReadingRun(length = length, first = sorted[runStart], last = sorted[index])
        }
    }
    return best
}

private val JournalActivityDay.epochDay: Long
    get() = LocalDate.of(year, month + 1, dayOfMonth).toEpochDay()

/** The month's figures beside its calendar; each is null when there is nothing to say. */
internal data class JournalMonthHighlights(
    val longestRun: JournalReadingRun?,
    val busiestDay: JournalActivityDay?,
    val mostFinishedDay: JournalActivityDay?,
)

internal fun journalMonthHighlights(days: List<JournalActivityDay>): JournalMonthHighlights {
    val chronological = days.sortedBy(JournalActivityDay::epochDay)
    return JournalMonthHighlights(
        longestRun = longestReadingRun(days)?.takeIf { run -> run.length >= 2 },
        busiestDay = chronological.filter { it.pagesRead > 0 }.maxByOrNull(JournalActivityDay::pagesRead),
        // One finish a day is ordinary; the figure is for a day that finished several.
        mostFinishedDay = chronological.filter { it.finishedCount >= 2 }.maxByOrNull(JournalActivityDay::finishedCount),
    )
}

/** "21–23 Oct", or "30 Sep – 2 Oct" across a month's end. */
internal fun JournalReadingRun.rangeLabel(): String {
    val month = { day: JournalActivityDay -> shortMonthName(day.month) }
    return if (first.month == last.month) {
        "${first.dayOfMonth}–${last.dayOfMonth} ${month(last)}"
    } else {
        "${first.dayOfMonth} ${month(first)} – ${last.dayOfMonth} ${month(last)}"
    }
}

/** "Thu 22 Oct". */
internal fun JournalActivityDay.shortLabel(): String =
    SimpleDateFormat("EEE d MMM", Locale.US).format(Date(startEpochMillis()))

/** "Saturday 17 October". */
internal fun JournalActivityDay.longLabel(): String =
    SimpleDateFormat("EEEE d MMMM", Locale.US).format(Date(startEpochMillis()))

internal fun JournalActivityDay.startEpochMillis(): Long = Calendar.getInstance(Locale.US).apply {
    clear()
    set(year, month, dayOfMonth)
}.timeInMillis

internal fun shortMonthName(month: Int): String =
    java.text.DateFormatSymbols.getInstance(Locale.US).shortMonths[month]

internal fun longMonthName(month: Int): String =
    java.text.DateFormatSymbols.getInstance(Locale.US).months[month]

/** "182 days in 2026 · longest run 11 days, in July". */
internal fun JournalPeriodOverview.yearDaysSummary(): String {
    val year = SimpleDateFormat("yyyy", Locale.US).format(Date(periodStartEpochMillis))
    val readingDays = activityDays.count { day -> day.heatValue > 0 }
    val run = longestReadingRun(activityDays)?.takeIf { it.length >= 2 }
    return listOfNotNull(
        "${readingDays.counted("day")} in $year",
        run?.let { "longest run ${it.length} days, in ${longMonthName(it.last.month)}" },
    ).joinToString(" · ")
}

/** "5 of 31 days". */
internal fun JournalPeriodOverview.monthDaysSummary(): String {
    val daysInMonth = Calendar.getInstance(Locale.US).apply { timeInMillis = periodStartEpochMillis }
        .getActualMaximum(Calendar.DAY_OF_MONTH)
    val readingDays = activityDays.count { day -> day.heatValue > 0 }
    return "$readingDays of $daysInMonth days"
}

/** One book or comic on a day's sheet: what was read of it that day. */
internal data class JournalDayEntry(
    val summary: JournalItemSummary,
    val finishedAtEpochMillis: Long?,
    val lastReadAtEpochMillis: Long,
    val pages: Int,
    val readingMillis: Long,
)

/** Everything read on [day], the books finished then first, latest first within each. */
internal fun JournalPeriodOverview.dayEntries(day: JournalActivityDay): List<JournalDayEntry> {
    val summaries = (finishedItems + activeItems).distinctBy { summary -> summary.item.itemId }
    return summaries.mapNotNull { summary ->
        val events = summary.events.filter { event -> event.localDayKey() == day.localDayKey }
        val sessions = summary.sessions.filter { session ->
            localDateKey(session.startedAtEpochMillis) == day.localDayKey ||
                localDateKey(session.endedAtEpochMillis) == day.localDayKey
        }
        if (events.isEmpty() && sessions.isEmpty()) return@mapNotNull null
        JournalDayEntry(
            summary = summary,
            finishedAtEpochMillis = events
                .filter { event -> event.eventType == JournalReadingEventType.FINISHED }
                .maxOfOrNull(JournalReadingEvent::occurredAtEpochMillis),
            lastReadAtEpochMillis = (
                events.map(JournalReadingEvent::occurredAtEpochMillis) +
                    sessions.map(JournalReadingSession::endedAtEpochMillis)
                ).max(),
            pages = maxOf(
                events.sumOf(JournalReadingEvent::positivePageDelta),
                sessions.sumOf(JournalReadingSession::positivePageDelta),
            ),
            readingMillis = sessions.sumOf { session -> session.durationMillis.coerceAtLeast(0L) },
        )
    }.sortedWith(
        compareBy<JournalDayEntry> { entry -> entry.finishedAtEpochMillis == null }
            .thenByDescending(JournalDayEntry::lastReadAtEpochMillis),
    )
}

/** "24 pages · 5 finished · 12 min". */
internal fun journalDaySummary(day: JournalActivityDay, entries: List<JournalDayEntry>): String = listOfNotNull(
    day.pagesRead.takeIf { it > 0 }?.counted("page"),
    day.finishedCount.takeIf { it > 0 }?.let { "$it finished" },
    entries.sumOf(JournalDayEntry::readingMillis).takeIf { it > 0L }?.let(::formatJournalMinutes),
).joinToString(" · ")

/** "Finished 20:05 · 6 pages · 4 min", or "Read 14:12 · …" for a book not finished that day. */
internal fun JournalDayEntry.line(): String = listOfNotNull(
    finishedAtEpochMillis?.let { "Finished ${formatJournalTime(it)}" } ?: "Read ${formatJournalTime(lastReadAtEpochMillis)}",
    pages.takeIf { it > 0 }?.counted("page"),
    readingMillis.takeIf { it > 0L }?.let(::formatJournalMinutes),
).joinToString(" · ")

internal fun formatJournalTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMillis))

/** "4 min", "1 h 12 min". */
internal fun formatJournalMinutes(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(1L)
    return if (minutes >= 60L) "${minutes / 60L} h ${minutes % 60L} min" else "$minutes min"
}

/** A rating out of 10 (half stars) counted toward its whole star; a half star goes below. */
internal fun journalRatingStarRow(rating: Int): Int = (rating.coerceIn(1, 10) / 2).coerceAtLeast(1)

internal data class JournalRatingSummary(
    /** How many ratings have 5, 4, 3, 2 and 1 stars, in that order. */
    val starCounts: List<Int>,
    val count: Int,
    /** Out of 5. */
    val average: Double,
)

/** Null when nothing is rated, so the section folds away. */
internal fun journalRatingSummary(countsByRating: Map<Int, Int>): JournalRatingSummary? {
    val count = countsByRating.values.sum()
    if (count <= 0) return null
    val byStars = IntArray(5)
    countsByRating.forEach { (rating, n) -> byStars[5 - journalRatingStarRow(rating)] += n }
    val total = countsByRating.entries.sumOf { (rating, n) -> rating.coerceIn(1, 10) * n }
    return JournalRatingSummary(
        starCounts = byStars.toList(),
        count = count,
        average = total.toDouble() / count / 2.0,
    )
}

/** "4.1". */
internal fun formatJournalAverage(value: Double): String = String.format(Locale.US, "%.1f", value)

/** One kind under Most read (Authors, Genres, ...): genres and tags count books and comics together. */
internal data class JournalMostReadGroup(
    val title: String,
    /** The kind as one section, for See all and for a name's books. */
    val section: JournalFacetSection,
    val rows: List<JournalMostReadRow>,
)

internal data class JournalMostReadRow(
    val row: JournalFacetRow,
    val books: Int,
    val comics: Int,
) {
    /** "3 books", "1 book, 2 comics". */
    val countLabel: String
        get() = listOfNotNull(books.takeIf { it > 0 }?.counted("book"), comics.takeIf { it > 0 }?.counted("comic"))
            .joinToString(", ")
}

internal fun journalMostReadGroups(
    sections: List<JournalFacetSection>,
    mediaByItemId: Map<String, JournalMediaType>,
): List<JournalMostReadGroup> {
    fun group(title: String, facetType: JournalFacetType, mediaType: JournalMediaType? = null): JournalMostReadGroup? {
        val parts = sections.filter { section ->
            section.facetType == facetType && (mediaType == null || section.mediaType == mediaType)
        }
        val rows = parts
            .flatMap(JournalFacetSection::rows)
            .groupBy(JournalFacetRow::label)
            .map { (label, rows) -> JournalFacetRow(label = label, itemIds = rows.flatMap(JournalFacetRow::itemIds).distinct().sorted()) }
            .sortedWith(compareByDescending<JournalFacetRow> { it.count }.thenBy { it.label })
        if (rows.isEmpty()) return null
        return JournalMostReadGroup(
            title = title,
            section = JournalFacetSection(
                title = title,
                mediaType = mediaType ?: parts.first().mediaType,
                facetType = facetType,
                rows = rows,
            ),
            rows = rows.map { row ->
                JournalMostReadRow(
                    row = row,
                    books = row.itemIds.count { id -> mediaByItemId[id] == JournalMediaType.BOOK },
                    comics = row.itemIds.count { id -> mediaByItemId[id] == JournalMediaType.COMIC },
                )
            },
        )
    }
    return listOfNotNull(
        group("Authors", JournalFacetType.AUTHOR, JournalMediaType.BOOK),
        group("Genres", JournalFacetType.GENRE),
        group("Writers", JournalFacetType.WRITER, JournalMediaType.COMIC),
        group("Artists", JournalFacetType.PENCILLER, JournalMediaType.COMIC),
        group("Tags", JournalFacetType.TAG),
    )
}

internal fun JournalPeriodOverview.mediaByItemId(): Map<String, JournalMediaType> =
    finishedItems.associate { summary ->
        summary.item.itemId to when (summary.item.itemKind) {
            com.example.unifiedreader.core.model.ReadableItemKind.BOOK -> JournalMediaType.BOOK
            com.example.unifiedreader.core.model.ReadableItemKind.COMIC -> JournalMediaType.COMIC
        }
    }

/** Finished books carrying one of a Most read row's names, newest first. */
internal fun JournalPeriodOverview.facetSummaries(row: JournalFacetRow): List<JournalItemSummary> {
    val itemIds = row.itemIds.toSet()
    return finishedItems.filter { summary -> summary.item.itemId in itemIds }
}

/** A period with no reading, no finished books and no ratings shows one empty state instead. */
internal val JournalPeriodOverview.isEmpty: Boolean
    get() = activityDays.none { day -> day.heatValue > 0 } &&
        finishedItems.isEmpty() &&
        stats.readingTimeMillis <= 0L &&
        stats.ratingCountsByMediaType.values.all { counts -> counts.values.sum() == 0 }

/** When the journal last had reading, for the empty state's way back. */
internal fun JournalSnapshot.lastReadAtEpochMillis(): Long? = listOfNotNull(
    events.maxOfOrNull(JournalReadingEvent::occurredAtEpochMillis),
    sessions.maxOfOrNull(JournalReadingSession::endedAtEpochMillis),
).maxOrNull()

/** "one finished every 2.5 days", "2 finished a day"; null with nothing finished. */
internal fun journalFinishPace(finished: Int, activeDays: Int): String? {
    if (finished <= 0 || activeDays <= 0) return null
    return if (finished >= activeDays) {
        "${formatJournalDecimal(finished.toDouble() / activeDays)} finished a day"
    } else {
        "one finished every ${formatJournalDecimal(activeDays.toDouble() / finished)} days"
    }
}

/** One decimal, dropping ".0": "2.5", "2", "12". */
internal fun formatJournalDecimal(value: Double): String {
    if (value >= 10.0) return value.roundToInt().toString()
    val text = String.format(Locale.US, "%.1f", value)
    return text.removeSuffix(".0")
}

internal fun Int.counted(noun: String): String = if (this == 1) "1 $noun" else "$this ${noun}s"
