package com.example.unifiedreader.feature.journal

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalFinishedListsTest {
    private val zone = ZoneId.of("UTC")
    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L

    private val list = JournalFinishedList(
        id = "list:a",
        name = "The Long Eclipse",
        startedAtEpochMillis = day(10, 19),
        finishedAtEpochMillis = day(10, 30),
        bookCount = 14,
        seriesCount = 4,
        readMillis = (9 * 60 + 20) * 60_000L,
        books = emptyList(),
    )

    @Test
    fun `the dates say when it was started and finished`() {
        assertEquals("Started 19 Oct · finished today", list.datesLine(day(10, 30), zone))
        assertEquals("Started 19 Oct · finished yesterday", list.datesLine(day(10, 31), zone))
        assertEquals("Finished 30 Oct", list.copy(startedAtEpochMillis = day(10, 30)).datesLine(day(11, 5), zone))
        assertEquals(12L, list.days(zone))
    }

    @Test
    fun `the figures count books, series, time read and days`() {
        assertEquals("14 books · 4 series · 9 h 20 min read", list.figuresLine())
        assertEquals(listOf("14" to "books", "4" to "series", "9 h 20" to "read"), list.figures().take(3))
    }
}
