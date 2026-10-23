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

class JournalOverviewModelTest {

    @Test
    fun `heat levels step by quarters of the busiest day`() {
        assertEquals(0, journalHeatLevel(0, 25))
        assertEquals(1, journalHeatLevel(2, 25))
        assertEquals(2, journalHeatLevel(12, 25))
        assertEquals(3, journalHeatLevel(18, 25))
        assertEquals(4, journalHeatLevel(25, 25))
        assertEquals(1, journalHeatLevel(3, 0))
    }

    @Test
    fun `a day with reading but no pages still shows`() {
        assertEquals(1, day(5, pages = 0, events = 1).heatValue)
        assertEquals(0, day(5, pages = 0, events = 0).heatValue)
    }

    @Test
    fun `the longest run counts consecutive days and keeps the first of a tie`() {
        val days = listOf(day(17), day(18), day(21), day(22), day(23), day(26), day(27), day(28))
        val run = longestReadingRun(days)!!
        assertEquals(3, run.length)
        assertEquals(21, run.first.dayOfMonth)
        assertEquals(23, run.last.dayOfMonth)
        assertEquals("21–23 Oct", run.rangeLabel())
    }

    @Test
    fun `a run crosses the end of a month`() {
        val days = listOf(day(30, month = Calendar.SEPTEMBER), day(1), day(2))
        val run = longestReadingRun(days)!!
        assertEquals(3, run.length)
        assertEquals("30 Sep – 2 Oct", run.rangeLabel())
    }

    @Test
    fun `month highlights leave out a one-day run and a single finish`() {
        val highlights = journalMonthHighlights(listOf(day(17, pages = 24, finished = 1), day(20, pages = 25)))
        assertNull(highlights.longestRun)
        assertEquals(20, highlights.busiestDay!!.dayOfMonth)
        assertNull(highlights.mostFinishedDay)

        val busy = journalMonthHighlights(listOf(day(17, pages = 24, finished = 5), day(18, pages = 3)))
        assertEquals(2, busy.longestRun!!.length)
        assertEquals(17, busy.mostFinishedDay!!.dayOfMonth)
    }

    @Test
    fun `day labels name the weekday`() {
        assertEquals("Sat 17 Oct", day(17).shortLabel())
        assertEquals("Saturday 17 October", day(17).longLabel())
    }

    @Test
    fun `a day lists what was read then, finished books first`() {
        val oct17 = epochMillis(2026, Calendar.OCTOBER, 17, hour = 20)
        val finished = summary("finished", events = listOf(event("finished", oct17, JournalReadingEventType.FINISHED, from = 10, to = 16)))
        val read = summary(
            "read",
            events = listOf(event("read", oct17 + 3_600_000L, JournalReadingEventType.CONTINUED, from = 1, to = 5)),
            sessions = listOf(session("read", oct17 + 3_000_000L, minutes = 4)),
        )
        val otherDay = summary("other", events = listOf(event("other", oct17 + 86_400_000L, JournalReadingEventType.CONTINUED)))
        val overview = overview(finished = listOf(finished), active = listOf(read, otherDay, finished))

        val entries = overview.dayEntries(day(17))

        assertEquals(listOf("finished", "read"), entries.map { it.summary.item.itemId })
        assertEquals("Finished 20:00 · 6 pages", entries[0].line())
        assertEquals("Read 21:00 · 4 pages · 4 min", entries[1].line())
        assertEquals("24 pages · 5 finished · 4 min", journalDaySummary(day(17, pages = 24, finished = 5), entries))
    }

    @Test
    fun `ratings group half stars toward the star below`() {
        assertEquals(5, journalRatingStarRow(10))
        assertEquals(4, journalRatingStarRow(9))
        assertEquals(4, journalRatingStarRow(8))
        assertEquals(1, journalRatingStarRow(1))

        val summary = journalRatingSummary(mapOf(10 to 2, 9 to 1, 7 to 1))!!
        assertEquals(listOf(2, 1, 1, 0, 0), summary.starCounts)
        assertEquals(4, summary.count)
        assertEquals("4.5", formatJournalAverage(summary.average))
        assertNull(journalRatingSummary(emptyMap()))
        assertNull(journalRatingSummary(mapOf(8 to 0)))
    }

    @Test
    fun `most read merges genres across books and comics and drops empty kinds`() {
        val sections = listOf(
            JournalFacetSection("Book genres", JournalMediaType.BOOK, JournalFacetType.GENRE, listOf(row("Fantasy", "b1"), row("Mystery", "b2"))),
            JournalFacetSection("Book authors", JournalMediaType.BOOK, JournalFacetType.AUTHOR, listOf(row("Mira Kalden", "b1", "b2"))),
            JournalFacetSection("Comic genres", JournalMediaType.COMIC, JournalFacetType.GENRE, listOf(row("Fantasy", "c1", "c2"))),
            JournalFacetSection("Comic tags", JournalMediaType.COMIC, JournalFacetType.TAG, emptyList()),
        )
        val media = mapOf("b1" to JournalMediaType.BOOK, "b2" to JournalMediaType.BOOK, "c1" to JournalMediaType.COMIC, "c2" to JournalMediaType.COMIC)

        val groups = journalMostReadGroups(sections, media)

        assertEquals(listOf("Authors", "Genres"), groups.map { it.title })
        val genres = groups[1].rows
        assertEquals(listOf("Fantasy", "Mystery"), genres.map { it.row.label })
        assertEquals("1 book, 2 comics", genres[0].countLabel)
        assertEquals("1 book", genres[1].countLabel)
        assertEquals("2 books", groups[0].rows.single().countLabel)
    }

    @Test
    fun `finish pace reads naturally`() {
        assertEquals("one finished every 2.5 days", journalFinishPace(finished = 2, activeDays = 5))
        assertEquals("1 finished a day", journalFinishPace(finished = 3, activeDays = 3))
        assertEquals("1.5 finished a day", journalFinishPace(finished = 3, activeDays = 2))
        assertNull(journalFinishPace(finished = 0, activeDays = 4))
    }

    @Test
    fun `a period with nothing in it is empty`() {
        assertTrue(overview().isEmpty)
        assertFalse(overview(days = listOf(day(3))).isEmpty)
    }

    @Test
    fun `summaries count the days`() {
        assertEquals("2 of 31 days", overview(days = listOf(day(3), day(4))).monthDaysSummary())
        val year = overview(period = JournalPeriod.YEAR, days = listOf(day(3), day(4), day(5), day(9, month = Calendar.JULY)))
        assertEquals("4 days in 2026 · longest run 3 days, in October", year.yearDaysSummary())
    }

    @Test
    fun `year months go four across on a tablet and two on a narrow phone`() {
        assertEquals(4, journalYearMonthColumns(760f))
        assertEquals(3, journalYearMonthColumns(450f))
        assertEquals(2, journalYearMonthColumns(340f))
    }

    private fun day(
        dayOfMonth: Int,
        month: Int = Calendar.OCTOBER,
        pages: Int = 5,
        events: Int = 1,
        finished: Int = 0,
    ) = JournalActivityDay(
        localDayKey = localDateKey(epochMillis(2026, month, dayOfMonth)),
        year = 2026,
        month = month,
        dayOfMonth = dayOfMonth,
        pagesRead = pages,
        eventCount = events,
        finishedBooks = finished,
        finishedComics = 0,
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

    private fun row(label: String, vararg ids: String) = JournalFacetRow(label = label, itemIds = ids.toList())

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
        id = "$id:session",
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
