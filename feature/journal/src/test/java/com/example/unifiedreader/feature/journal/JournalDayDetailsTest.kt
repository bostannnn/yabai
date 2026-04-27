package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class JournalDayDetailsTest {
    @Test
    fun `day details includes only items finished on selected day`() {
        val april26 = epochMillis(2026, Calendar.APRIL, 26)
        val april27 = epochMillis(2026, Calendar.APRIL, 27)
        val overview = JournalPeriodOverview(
            period = JournalPeriod.MONTH,
            periodStartEpochMillis = periodBounds(JournalPeriod.MONTH, april27).first,
            periodEndEpochMillis = periodBounds(JournalPeriod.MONTH, april27).second,
            stats = JournalStats(),
            activityDays = emptyList(),
            finishedItems = listOf(
                summary("april-26", april26),
                summary("april-27-a", april27),
                summary("april-27-b", april27 + 60_000L),
            ),
        )
        val day = JournalActivityDay(
            localDayKey = localDateKey(april27),
            year = 2026,
            month = Calendar.APRIL,
            dayOfMonth = 27,
            pagesRead = 120,
            eventCount = 2,
            finishedBooks = 0,
            finishedComics = 2,
        )

        val details = overview.dayDetails(day)

        assertEquals(listOf("april-27-a", "april-27-b"), details.finishedItems.map { it.item.itemId })
    }

    @Test
    fun `calendar month uses single row only when the full month fits`() {
        assertTrue(monthCalendarLayout(maxWidthDp = 1200f, daysInMonth = 31, compact = false).isSingleRow)
        assertFalse(monthCalendarLayout(maxWidthDp = 360f, daysInMonth = 31, compact = false).isSingleRow)
        assertFalse(monthCalendarLayout(maxWidthDp = 1200f, daysInMonth = 31, compact = true).isSingleRow)
    }

    @Test
    fun `year calendar increases month columns on wide screens`() {
        assertEquals(1, yearCalendarMonthColumns(260f))
        assertEquals(2, yearCalendarMonthColumns(330f))
        assertEquals(4, yearCalendarMonthColumns(620f))
        assertEquals(6, yearCalendarMonthColumns(1200f))
    }

    private fun summary(
        itemId: String,
        finishedAt: Long,
    ): JournalItemSummary = JournalItemSummary(
        item = JournalItemRef(
            itemId = itemId,
            sourceType = SourceType.LOCAL,
            libraryId = "library",
            itemTitle = itemId,
            coverUrl = null,
            itemKind = ReadableItemKind.COMIC,
            format = FormatType.CBZ,
        ),
        events = listOf(
            JournalReadingEvent(
                id = "$itemId:finished",
                itemId = itemId,
                sourceType = SourceType.LOCAL,
                libraryId = "library",
                itemTitle = itemId,
                coverUrl = null,
                itemKind = ReadableItemKind.COMIC,
                format = FormatType.CBZ,
                eventType = JournalReadingEventType.FINISHED,
                fromPercent = null,
                toPercent = null,
                fromPage = null,
                toPage = 20,
                pageCount = 20,
                occurredAtEpochMillis = finishedAt,
            ),
        ),
        entry = null,
    )
}

private fun epochMillis(
    year: Int,
    month: Int,
    dayOfMonth: Int,
): Long = Calendar.getInstance(Locale.US).apply {
    clear()
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, dayOfMonth)
}.timeInMillis
