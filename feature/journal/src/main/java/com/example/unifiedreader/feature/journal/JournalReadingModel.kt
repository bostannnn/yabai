package com.example.unifiedreader.feature.journal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/*
 * What the journal's panels say about a period, worked out from its overview: what was read and
 * for how long, the summary at the top, and when the reading happens. Pure, so it is tested
 * without Compose.
 */

/** One book or comic read in the period: for how long, on how many days, finished then or not. */
internal data class JournalReadItem(
    val summary: JournalItemSummary,
    val readingMillis: Long,
    val readingDays: Int,
    val pages: Int,
    /** When it was finished, when that was in the period. */
    val finishedAtEpochMillis: Long?,
) {
    val needsRating: Boolean
        get() = finishedAtEpochMillis != null && summary.entry?.rating == null
}

/** What you read: the books given time, longest first, and the ones only opened for a moment. */
internal data class JournalWhatYouRead(
    val read: List<JournalReadItem>,
    val opened: List<JournalReadItem>,
)

internal fun JournalPeriodOverview.whatYouRead(): JournalWhatYouRead {
    val start = periodStartEpochMillis
    val end = periodEndEpochMillis
    val items = (activeItems + finishedItems).distinctBy { summary -> summary.item.itemId }.map { summary ->
        val events = summary.events.inPeriod(start, end)
        val sessions = summary.sessions.overlappingPeriod(start, end)
        val millis = sessions.sumOf { session -> session.activeDurationWithin(start, end) }
        JournalReadItem(
            summary = summary,
            readingMillis = millis,
            readingDays = (
                events.map(JournalReadingEvent::localDayKey) +
                    sessions.flatMap { session -> session.activeDayKeys(start, end) }
                ).distinct().size,
            pages = journalItemPages(events, sessions, millis),
            finishedAtEpochMillis = summary.finishedAtEpochMillis?.takeIf { at -> at in start until end },
        )
    }.sortedWith(
        compareByDescending<JournalReadItem> { item -> item.readingMillis }
            .thenByDescending { item -> item.pages }
            .thenByDescending { item -> item.summary.lastActivityAtEpochMillis },
    )
    // Only opened: a few minutes and not finished. Reading done on another device leaves pages
    // without time, and counts as read.
    val (opened, read) = items.partition { item ->
        item.finishedAtEpochMillis == null &&
            item.readingMillis < JournalOpenedOnlyMillis &&
            (item.readingMillis > 0L || item.pages == 0)
    }
    return JournalWhatYouRead(read = read, opened = opened)
}

/** The period's summary panel. */
internal data class JournalSummary(
    val readingMillis: Long,
    val readingDays: Int,
    /** The period's days so far: all of a past one, up to today in the current one. */
    val daysSoFar: Int,
    val isCurrent: Boolean,
    /** The run of reading days that reaches today or yesterday, from two days. */
    val currentRun: JournalReadingRun?,
    /** The period's longest run, from two days. */
    val longestRun: JournalReadingRun?,
    val finished: Int,
    val waitingForRating: Int,
    /** The one book finished, when there is exactly one. */
    val soleFinishedTitle: String?,
    val bookMillis: Long,
    val comicMillis: Long,
) {
    val millisPerReadingDay: Long?
        get() = if (readingMillis > 0L && readingDays > 0) readingMillis / readingDays else null
}

internal fun JournalPeriodOverview.summary(nowEpochMillis: Long): JournalSummary {
    val readingDays = activityDays.filter { day -> day.heatValue > 0 }
    val today = localDate(nowEpochMillis)
    val first = localDate(periodStartEpochMillis)
    val end = localDate(periodEndEpochMillis)
    val isCurrent = !today.isBefore(first) && today.isBefore(end)
    val daysSoFar = when {
        today.isBefore(first) -> 0
        isCurrent -> ChronoUnit.DAYS.between(first, today).toInt() + 1
        else -> ChronoUnit.DAYS.between(first, end).toInt()
    }
    return JournalSummary(
        readingMillis = stats.readingTimeMillis,
        readingDays = readingDays.size,
        daysSoFar = daysSoFar,
        isCurrent = isCurrent,
        currentRun = if (isCurrent) runReaching(readingDays, today) else null,
        longestRun = longestReadingRun(readingDays)?.takeIf { run -> run.length >= 2 },
        finished = finishedItems.size,
        waitingForRating = finishedItems.count { summary -> summary.entry?.rating == null },
        soleFinishedTitle = finishedItems.singleOrNull()?.item?.displayTitle,
        bookMillis = stats.bookReadingTimeMillis,
        comicMillis = stats.comicReadingTimeMillis,
    )
}

/** The reading days in a row up to today, or up to yesterday when today has none yet. */
private fun runReaching(days: List<JournalActivityDay>, today: LocalDate): JournalReadingRun? {
    val byEpochDay = days.associateBy(JournalActivityDay::epochDay)
    val last = byEpochDay[today.toEpochDay()] ?: byEpochDay[today.toEpochDay() - 1] ?: return null
    var first = last
    while (true) {
        first = byEpochDay[first.epochDay - 1] ?: break
    }
    val length = (last.epochDay - first.epochDay + 1).toInt()
    return JournalReadingRun(length = length, first = first, last = last).takeIf { length >= 2 }
}

/** Under the days you read: "Every day since 17 Oct", "Longest run 4 days, 3–6 Oct", "Of 25 so far". */
internal fun JournalSummary.daysLine(period: JournalPeriod): String {
    val run = currentRun
    return when {
        run != null && period == JournalPeriod.MONTH && run.first.dayOfMonth == 1 -> "Every day this month"
        run != null && period == JournalPeriod.MONTH -> "Every day since ${formatShortDate(run.first.startEpochMillis())}"
        period == JournalPeriod.MONTH && longestRun != null -> "Longest run ${longestRun.length} days, ${longestRun.rangeLabel()}"
        isCurrent -> "Of $daysSoFar so far${if (period == JournalPeriod.YEAR) " this year" else ""}"
        else -> "Of $daysSoFar"
    }
}

/** Under the finished count: the one book's name, or how many wait for a rating. */
internal fun JournalSummary.finishedLine(): String? = when {
    soleFinishedTitle != null -> soleFinishedTitle
    waitingForRating > 0 -> "$waitingForRating waiting for a rating"
    finished > 0 -> "All rated"
    else -> null
}

/** Under the reading time: "About 23 min on a day you read". */
internal fun JournalSummary.paceLine(): String? =
    millisPerReadingDay?.let { millis -> "About ${formatJournalMinutes(millis)} on a day you read" }

/** A summary figure's time: "3 h 24 min", "23 min", and whole hours from ten: "61 h". */
internal fun formatJournalReadingTime(millis: Long): String {
    if (millis < 10L * 3_600_000L) return formatJournalMinutes(millis)
    return "${(millis / 3_600_000.0).roundToLong()} h"
}

/**
 * When most of the reading happens: the shortest stretch of hours, past midnight too, holding
 * at least 60% of the time, if that is six hours or fewer. The first hour and how many, or null
 * for reading spread over the day.
 */
internal fun journalReadingHours(buckets: List<JournalTimeBucket>): Pair<Int, Int>? {
    if (buckets.size != 24) return null
    val total = buckets.sumOf(JournalTimeBucket::totalMillis)
    if (total <= 0L) return null
    (1..JournalReadingHoursMax).forEach { length ->
        val best = (0 until 24)
            .map { start -> start to (0 until length).sumOf { offset -> buckets[(start + offset) % 24].totalMillis } }
            .filter { (_, millis) -> millis >= total * JournalReadingHoursShare }
            .maxByOrNull { (_, millis) -> millis }
        if (best != null) return best.first to length
    }
    return null
}

/** "Mostly 23:00–02:00", "Mostly around 15:00", "Spread across the day". */
internal fun journalReadingHoursLabel(buckets: List<JournalTimeBucket>): String {
    val (start, length) = journalReadingHours(buckets) ?: return "Spread across the day"
    return if (length == 1) {
        "Mostly around ${hourLabel(start)}"
    } else {
        "Mostly ${hourLabel(start)}–${hourLabel((start + length) % 24)}"
    }
}

/** The month of the year with the most reading time, or null for a year without any. */
internal fun journalBusiestMonth(buckets: List<JournalTimeBucket>): Int? =
    buckets.maxByOrNull(JournalTimeBucket::totalMillis)?.takeIf { it.totalMillis > 0L }?.key

/** Most read needs a few finished books to say anything; with one, every name counts one. */
internal val JournalPeriodOverview.showsMostRead: Boolean
    get() = finishedItems.size >= JournalMostReadMinFinished

/** A kind's bars only compare when its names count differently. */
internal val JournalMostReadGroup.barsCompare: Boolean
    get() = rows.map { row -> row.row.count }.distinct().size > 1

private fun hourLabel(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

private fun localDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private const val JournalOpenedOnlyMillis = 5 * 60_000L
private const val JournalReadingHoursMax = 6
private const val JournalReadingHoursShare = 0.6
private const val JournalMostReadMinFinished = 3
