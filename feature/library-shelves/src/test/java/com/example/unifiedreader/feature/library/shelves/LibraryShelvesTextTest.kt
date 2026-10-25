package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LibraryShelvesTextTest {
    private lateinit var defaultLocale: Locale

    @Before
    fun useEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `a book finished more than a week ago shows the date as the book page writes it`() {
        val finished = finishedAt(LocalDateTime.of(2026, 10, 17, 21, 0))
        val now = millis(LocalDateTime.of(2026, 10, 25, 17, 0))

        assertEquals("17 Oct", finished.tileMetaText(nowEpochMillis = now, zone = ZoneOffset.UTC))
    }

    @Test
    fun `a book finished in another year shows the year`() {
        val finished = finishedAt(LocalDateTime.of(2025, 12, 30, 9, 0))
        val now = millis(LocalDateTime.of(2026, 1, 20, 9, 0))

        assertEquals("30 Dec 2025", finished.tileMetaText(nowEpochMillis = now, zone = ZoneOffset.UTC))
    }

    private fun finishedAt(time: LocalDateTime) = RecentlyFinishedShelfItem(
        item = ReadableItem(
            id = "kavita:finished",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library",
            remoteId = "finished",
            title = "Finished Book",
            format = FormatType.EPUB,
        ),
        progress = ItemProgressSummary(
            label = "Finished",
            fraction = 1f,
            lastReadAtEpochMillis = millis(time),
            isFinished = true,
        ),
    )

    private fun millis(time: LocalDateTime): Long = time.toInstant(ZoneOffset.UTC).toEpochMilli()
}
