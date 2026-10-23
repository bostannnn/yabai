package com.example.unifiedreader.feature.settings

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormattingTest {
    private val now = at(2026, Calendar.OCTOBER, 23, 12, 0)

    @Test
    fun `moments read as today, yesterday, a day, or a day in another year`() {
        assertEquals("today at 10:42", formatSettingsMoment(at(2026, Calendar.OCTOBER, 23, 10, 42), now))
        assertEquals("yesterday at 21:10", formatSettingsMoment(at(2026, Calendar.OCTOBER, 22, 21, 10), now))
        assertEquals("20 Oct at 09:14", formatSettingsMoment(at(2026, Calendar.OCTOBER, 20, 9, 14), now))
        assertEquals("20 Oct 2025", formatSettingsMoment(at(2025, Calendar.OCTOBER, 20, 9, 14), now))
        assertEquals("20 Oct", formatSettingsDay(at(2026, Calendar.OCTOBER, 20, 9, 14), now))
    }

    @Test
    fun `a host drops the scheme and the trailing slash`() {
        assertEquals("192.168.50.60:5000", settingsHostLabel("http://192.168.50.60:5000/"))
        assertEquals("books.example.org", settingsHostLabel(" https://books.example.org "))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
