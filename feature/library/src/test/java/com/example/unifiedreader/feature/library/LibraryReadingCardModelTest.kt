package com.example.unifiedreader.feature.library

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryReadingCardModelTest {
    @Test
    fun `the place names the chapter over its title, the chapter alone, or the page`() {
        assertEquals(
            LibraryReadingCardPlace(label = "Chapter 4 of 6", line = "The Lighthouse Ledger"),
            libraryReadingCardPlace(epub(chapterTitle = "The Lighthouse Ledger")),
        )
        assertEquals(LibraryReadingCardPlace(label = null, line = "Chapter 4 of 6"), libraryReadingCardPlace(epub(chapterTitle = null)))
        assertEquals(LibraryReadingCardPlace(label = null, line = "Page 5 of 8"), libraryReadingCardPlace(comic()))
        assertNull(libraryReadingCardPlace(ItemProgressSummary(label = "Resume at 40%", fraction = 0.4f)))
        assertNull(libraryReadingCardPlace(null))
    }

    @Test
    fun `a title that only numbers the chapter is left to the label`() {
        listOf("Chapter 4", "chapter 4.", "Part II", "IV", "4", "Глава 4").forEach { title ->
            assertEquals(title, LibraryReadingCardPlace(label = null, line = "Chapter 4 of 6"), libraryReadingCardPlace(epub(chapterTitle = title)))
        }
        listOf("Chapter 4: The Ledger", "Civil Dawn", "Mix").forEach { title ->
            assertEquals(title, title, libraryReadingCardPlace(epub(chapterTitle = title))?.line)
        }
    }

    @Test
    fun `the progress line adds the page only when the place names the chapter`() {
        assertEquals("47% · Page 47 of 73", libraryReadingCardProgressLabel(epub(chapterTitle = "The Lighthouse Ledger")))
        assertEquals("63%", libraryReadingCardProgressLabel(comic()))
        assertEquals("Not started", libraryReadingCardProgressLabel(null))
    }

    @Test
    fun `time left reads in minutes, then hours and minutes, then whole hours`() {
        assertEquals("Less than a minute left", libraryReadingTimeLeftLabel(20_000L))
        assertEquals("About 25 min left", libraryReadingTimeLeftLabel(25 * Minute + 10_000L))
        assertEquals("About 1 h 20 min left", libraryReadingTimeLeftLabel(80 * Minute))
        assertEquals("About 2 h left", libraryReadingTimeLeftLabel(120 * Minute))
        assertEquals("About 13 h left", libraryReadingTimeLeftLabel(12 * 60 * Minute + 40 * Minute))
    }

    @Test
    fun `the bar breaks at the chapters, else at a short comic's pages`() {
        assertEquals(
            listOf(0f, 0.1f, 0.24f, 0.38f, 1f),
            libraryReadingCardSegments(epub(chapterTitle = null, starts = listOf(0f, 0.1f, 0.24f, 0.38f))),
        )
        // A first chapter after a foreword still starts a piece; the foreword is the piece before it.
        assertEquals(listOf(0f, 0.05f, 0.5f, 1f), libraryReadingCardSegments(epub(chapterTitle = null, starts = listOf(0.05f, 0.5f))))
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), libraryReadingCardSegments(comic(pages = 4)))
    }

    @Test
    fun `the bar is whole without breaks or with too many`() {
        assertNull(libraryReadingCardSegments(epub(chapterTitle = null, starts = emptyList())))
        assertNull(libraryReadingCardSegments(comic(pages = 200)))
        assertNull(libraryReadingCardSegments(epub(chapterTitle = null, starts = (0 until 40).map { it / 40f })))
        assertNull(libraryReadingCardSegments(null))
    }

    @Test
    fun `the overline's day is today, yesterday, days ago, then the date`() {
        val zone = ZoneId.of("UTC")
        val now = 1_761_400_000_000L
        assertEquals(LibraryReadDay("today"), libraryLastReadDay(now - Minute, now, zone))
        assertEquals(LibraryReadDay("yesterday"), libraryLastReadDay(now - Day, now, zone))
        assertEquals(LibraryReadDay("3 days ago"), libraryLastReadDay(now - 3 * Day, now, zone))
        assertEquals(true, libraryLastReadDay(now - 30 * Day, now, zone)?.isDate)
        assertNull(libraryLastReadDay(0L, now, zone))
    }

    private fun epub(chapterTitle: String?, starts: List<Float> = emptyList()) = ItemProgressSummary(
        label = "Resume",
        fraction = 0.47f,
        currentPage = 47,
        pageCount = 73,
        chapter = 4,
        chapterCount = 6,
        chapterTitle = chapterTitle,
        chapterStarts = starts,
    )

    private fun comic(pages: Int = 8) = ItemProgressSummary(
        label = "Resume on page 5 of $pages",
        fraction = 5f / pages,
        currentPage = 5,
        pageCount = pages,
    )

    private companion object {
        const val Minute = 60_000L
        const val Day = 24 * 60 * Minute
    }
}
