package com.example.unifiedreader.core.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortDayLabelTest {
    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun leavesOutThisYear() {
        assertEquals("3 Nov", shortDayLabel(LocalDate.of(2026, 11, 3), today))
        assertEquals("1 Jan", shortDayLabel(LocalDate.of(2026, 1, 1), today))
    }

    @Test
    fun writesAnotherYear() {
        assertEquals("31 Dec 2025", shortDayLabel(LocalDate.of(2025, 12, 31), today))
        assertEquals("3 Nov 2027", shortDayLabel(LocalDate.of(2027, 11, 3), today))
    }

    @Test
    fun readsAMomentInTheGivenZone() {
        val lateOnTheThirdInUtc = 1_793_750_400_000L - 1 // 2026-11-03T23:59:59.999Z
        val now = 1_793_923_200_000L // 2026-11-06T00:00:00Z
        assertEquals("3 Nov", shortDayLabel(lateOnTheThirdInUtc, now, ZoneOffset.UTC))
        assertEquals("4 Nov", shortDayLabel(lateOnTheThirdInUtc, now, ZoneId.of("Europe/Moscow")))
    }
}
