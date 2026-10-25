package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalReadingModelTest {

    @Test
    fun `what you read puts the longest first and folds the ones only opened`() {
        val oct = { day: Int, hour: Int -> epochMillis(2026, Calendar.OCTOBER, day, hour) }
        val kristina = summary(
            "kristina",
            sessions = listOf(session("kristina", oct(20, 23), 22), session("kristina", oct(21, 23), 10), session("kristina", oct(22, 1), 49)),
        )
        val lily = summary("lily", sessions = listOf(session("lily", oct(19, 2), 64)))
        val finished = summary(
            "finished",
            events = listOf(event("finished", oct(24, 1), JournalReadingEventType.FINISHED, from = 1, to = 38)),
            sessions = listOf(session("finished", oct(24, 1), 11)),
        )
        val elsewhere = summary("elsewhere", events = listOf(event("elsewhere", oct(18, 9), JournalReadingEventType.CONTINUED, from = 10, to = 30)))
        val glanced = summary("glanced", sessions = listOf(session("glanced", oct(17, 22), 1)))
        val opened = summary("opened", events = listOf(event("opened", oct(18, 1), JournalReadingEventType.STARTED, to = 5)))
        val overview = overview(
            finished = listOf(finished),
            active = listOf(glanced, opened, elsewhere, finished, lily, kristina),
        )

        val reading = overview.whatYouRead()

        assertEquals(listOf("kristina", "lily", "finished", "elsewhere"), reading.read.map { it.summary.item.itemId })
        assertEquals(listOf("glanced", "opened"), reading.opened.map { it.summary.item.itemId })
        val first = reading.read.first()
        assertEquals(81 * 60_000L, first.readingMillis)
        assertEquals(3, first.readingDays)
        assertTrue(reading.read[2].needsRating)
        assertFalse(first.needsRating)
        // Read on another device: pages, no time.
        assertEquals(20, reading.read[3].pages)
    }

    @Test
    fun `the days you read name the run that reaches today`() {
        val now = epochMillis(2026, Calendar.OCTOBER, 25, 18)
        val running = overview(days = (17..25).map { day(it, minutes = 10) }).summary(now)
        assertEquals(9, running.readingDays)
        assertEquals(25, running.daysSoFar)
        assertEquals("Every day since 17 Oct", running.daysLine(JournalPeriod.MONTH))

        // Nothing yet today: the run up to yesterday still counts.
        val yesterday = overview(days = (20..24).map { day(it, minutes = 10) }).summary(now)
        assertEquals("Every day since 20 Oct", yesterday.daysLine(JournalPeriod.MONTH))

        val broken = overview(days = listOf(day(3), day(4), day(5), day(6), day(20))).summary(now)
        assertNull(broken.currentRun)
        assertEquals("Longest run 4 days, 3–6 Oct", broken.daysLine(JournalPeriod.MONTH))

        val scattered = overview(days = listOf(day(3), day(20))).summary(now)
        assertEquals("Of 25 so far", scattered.daysLine(JournalPeriod.MONTH))

        val past = overview(days = listOf(day(3), day(20))).summary(epochMillis(2026, Calendar.NOVEMBER, 2, 9))
        assertEquals("Of 31", past.daysLine(JournalPeriod.MONTH))
    }

    @Test
    fun `the year's longest run is named by its dates, not the month it ends in`() {
        // 13 Apr – 3 May read as "in May" before.
        val april = (13..30).map { day(it, month = Calendar.APRIL) }
        val may = (1..3).map { day(it, month = Calendar.MAY) }
        val summary = overview(period = JournalPeriod.YEAR, days = april + may).summary(epochMillis(2026, Calendar.OCTOBER, 25, 9))

        assertEquals(21, summary.longestRun!!.length)
        assertEquals("13 Apr – 3 May", summary.longestRun!!.rangeLabel())
        assertEquals("Of 298 so far this year", summary.daysLine(JournalPeriod.YEAR))
    }

    @Test
    fun `finished names the one book or the ones waiting for a rating`() {
        val now = epochMillis(2026, Calendar.OCTOBER, 25, 9)
        val one = overview(finished = listOf(summary("Viewer Discretion Advised"))).summary(now)
        assertEquals("Viewer Discretion Advised", one.finishedLine())

        val rated = summary("rated").copy(entry = entry("rated", rating = 8))
        val several = overview(finished = listOf(summary("a"), summary("b"), rated)).summary(now)
        assertEquals(3, several.finished)
        assertEquals("2 waiting for a rating", several.finishedLine())
        assertEquals("All rated", overview(finished = listOf(rated, rated.copy(item = rated.item.copy(itemId = "other")))).summary(now).finishedLine())
    }

    @Test
    fun `reading hours find the stretch that holds most of the time`() {
        // The tablet's October: mostly after midnight.
        val october = hours(0 to 100, 1 to 43, 8 to 5, 11 to 5, 12 to 5, 13 to 5, 15 to 5, 17 to 14, 20 to 5, 21 to 5, 22 to 5, 23 to 21)
        assertEquals(0 to 2, journalReadingHours(october))
        assertEquals("Mostly 00:00–02:00", journalReadingHoursLabel(october))
        // A stretch can run past midnight.
        assertEquals("Mostly 22:00–01:00", journalReadingHoursLabel(hours(22 to 30, 23 to 40, 0 to 30, 12 to 20)))

        assertEquals("Mostly around 15:00", journalReadingHoursLabel(hours(15 to 100, 9 to 5)))
        assertEquals("Spread across the day", journalReadingHoursLabel(hours(*(0 until 24).map { it to 10 }.toTypedArray())))
        assertNull(journalReadingHours(hours()))
    }

    @Test
    fun `times read short in a square and in the summary`() {
        assertEquals("1m", formatJournalCellMinutes(20_000L))
        assertEquals("22m", formatJournalCellMinutes(22 * 60_000L))
        assertEquals("1h 27", formatJournalCellMinutes(87 * 60_000L))
        assertEquals("1h 05", formatJournalCellMinutes(65 * 60_000L))
        assertEquals("2h", formatJournalCellMinutes(120 * 60_000L))
        assertEquals("3 h 24 min", formatJournalReadingTime(204 * 60_000L))
        assertEquals("61 h", formatJournalReadingTime(61 * 3_600_000L + 12 * 60_000L))
    }

    @Test
    fun `most read waits for three finished and compares only counts that differ`() {
        assertFalse(overview(finished = listOf(summary("a"))).showsMostRead)
        assertTrue(overview(finished = listOf(summary("a"), summary("b"), summary("c"))).showsMostRead)

        val section = JournalFacetSection("Authors", JournalMediaType.BOOK, JournalFacetType.AUTHOR, emptyList())
        val equal = JournalMostReadGroup("Authors", section, listOf(mostRead("Tartt", "b1"), mostRead("Rooney", "b2")))
        val differing = JournalMostReadGroup("Writers", section, listOf(mostRead("Moore", "c1", "c2"), mostRead("Tynion", "c3")))
        assertFalse(equal.barsCompare)
        assertTrue(differing.barsCompare)
    }

    @Test
    fun `the busiest month is the one with the most time`() {
        val year = (0..11).map { month ->
            JournalTimeBucket(key = month, label = shortMonthName(month), totalMillis = if (month == 5) 99L else month.toLong(), averageMillis = 0L)
        }
        assertEquals(5, journalBusiestMonth(year))
        assertNull(journalBusiestMonth(year.map { it.copy(totalMillis = 0L) }))
    }

    private fun hours(vararg minutes: Pair<Int, Int>): List<JournalTimeBucket> {
        val byHour = minutes.toMap()
        return (0..23).map { hour ->
            val total = (byHour[hour] ?: 0) * 60_000L
            JournalTimeBucket(key = hour, label = hour.toString().padStart(2, '0'), totalMillis = total, averageMillis = total / 31)
        }
    }

    private fun mostRead(label: String, vararg ids: String) =
        JournalMostReadRow(row = JournalFacetRow(label = label, itemIds = ids.toList()), books = ids.size, comics = 0)

    private fun day(
        dayOfMonth: Int,
        month: Int = Calendar.OCTOBER,
        minutes: Long = 5,
    ) = JournalActivityDay(
        localDayKey = localDateKey(epochMillis(2026, month, dayOfMonth)),
        year = 2026,
        month = month,
        dayOfMonth = dayOfMonth,
        pagesRead = 0,
        eventCount = 1,
        finishedBooks = 0,
        finishedComics = 0,
        readingMillis = minutes * 60_000L,
    )

    private fun overview(
        period: JournalPeriod = JournalPeriod.MONTH,
        days: List<JournalActivityDay> = emptyList(),
        finished: List<JournalItemSummary> = emptyList(),
        active: List<JournalItemSummary> = emptyList(),
    ): JournalPeriodOverview {
        val (start, end) = periodBounds(period, epochMillis(2026, Calendar.OCTOBER, 10))
        return JournalPeriodOverview(
            period = period,
            periodStartEpochMillis = start,
            periodEndEpochMillis = end,
            stats = JournalStats(),
            activityDays = days,
            finishedItems = finished,
            activeItems = active,
        )
    }

    private fun summary(
        id: String,
        events: List<JournalReadingEvent> = emptyList(),
        sessions: List<JournalReadingSession> = emptyList(),
    ) = JournalItemSummary(
        item = JournalItemRef(
            itemId = id,
            sourceType = SourceType.LOCAL,
            libraryId = "library",
            itemTitle = id,
            coverUrl = null,
            itemKind = ReadableItemKind.BOOK,
            format = FormatType.EPUB,
        ),
        events = events,
        sessions = sessions,
        entry = null,
    )

    private fun entry(id: String, rating: Int) = ItemJournalEntry(
        itemId = id,
        sourceType = SourceType.LOCAL,
        libraryId = "library",
        itemTitle = id,
        coverUrl = null,
        itemKind = ReadableItemKind.BOOK,
        format = FormatType.EPUB,
        rating = rating,
        reviewText = "",
        reviewedAtEpochMillis = null,
        updatedAtEpochMillis = 0L,
    )

    private fun event(
        id: String,
        at: Long,
        type: JournalReadingEventType,
        from: Int? = null,
        to: Int? = null,
    ) = JournalReadingEvent(
        id = "$id:$at",
        itemId = id,
        sourceType = SourceType.LOCAL,
        libraryId = "library",
        itemTitle = id,
        coverUrl = null,
        itemKind = ReadableItemKind.BOOK,
        format = FormatType.EPUB,
        eventType = type,
        fromPercent = null,
        toPercent = null,
        fromPage = from,
        toPage = to,
        pageCount = 100,
        occurredAtEpochMillis = at,
    )

    private fun session(id: String, endedAt: Long, minutes: Long) = JournalReadingSession(
        id = "$id:session:$endedAt",
        itemId = id,
        sourceType = SourceType.LOCAL,
        libraryId = "library",
        itemTitle = id,
        coverUrl = null,
        itemKind = ReadableItemKind.BOOK,
        format = FormatType.EPUB,
        mediaType = JournalMediaType.BOOK,
        startedAtEpochMillis = endedAt - minutes * 60_000L,
        endedAtEpochMillis = endedAt,
        durationMillis = minutes * 60_000L,
        fromPercent = null,
        toPercent = null,
        fromPage = null,
        toPage = null,
        pageCount = null,
    )

    private fun epochMillis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis
}
