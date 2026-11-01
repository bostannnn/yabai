package com.example.unifiedreader.feature.library

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryReadingCardModelTest {
    @Test
    fun `on its own the card has one line, the author and the chapter or page`() {
        assertEquals(listOf("Oona Fitch · Chapter 4 of 6"), libraryReadingCardLines("Oona Fitch", epub(chapterTitle = "The Ledger"), null, tall = false))
        assertEquals(listOf("Issue 2 · Page 5 of 8"), libraryReadingCardLines("Issue 2", comic(), null, tall = false))
        assertEquals(listOf("Page 5 of 8"), libraryReadingCardLines(null, comic(), null, tall = false))
        assertEquals(listOf("Oona Fitch"), libraryReadingCardLines("Oona Fitch", ItemProgressSummary(label = "Resume at 40%", fraction = 0.4f), null, tall = false))
        assertEquals(emptyList<String>(), libraryReadingCardLines(null, null, null, tall = false))
    }

    @Test
    fun `beside the shelves the author and the place get a line each, the place with the chapter's name`() {
        assertEquals(
            listOf("Oona Fitch", "Chapter 4 of 6 · The Lighthouse Ledger"),
            libraryReadingCardLines("Oona Fitch", epub(chapterTitle = "The Lighthouse Ledger"), null, tall = true),
        )
        assertEquals(listOf("Oona Fitch", "Chapter 4 of 6"), libraryReadingCardLines("Oona Fitch", epub(chapterTitle = null), null, tall = true))
        assertEquals(listOf("Page 5 of 8"), libraryReadingCardLines(null, comic(), null, tall = true))
    }

    @Test
    fun `a reading order's footnote takes the place's spot`() {
        val then = "Then Neon Ronin #13, The Long Eclipse #3"
        assertEquals(listOf("Issue 12 · $then"), libraryReadingCardLines("Issue 12", comic(), then, tall = false))
        assertEquals(listOf("Issue 12", then), libraryReadingCardLines("Issue 12", comic(), then, tall = true))
    }

    @Test
    fun `a chapter name that only numbers the chapter is left out`() {
        listOf("Chapter 4", "chapter 4.", "Part II", "IV", "4", "Глава 4").forEach { title ->
            assertEquals(title, listOf("Chapter 4 of 6"), libraryReadingCardLines(null, epub(chapterTitle = title), null, tall = true))
        }
        listOf("Chapter 4: The Ledger", "Civil Dawn", "Mix").forEach { title ->
            assertEquals(title, listOf("Chapter 4 of 6 · $title"), libraryReadingCardLines(null, epub(chapterTitle = title), null, tall = true))
        }
    }

    @Test
    fun `the bar's label is the percentage, with the time left when the pace is known`() {
        assertEquals("47% · 25 min left", libraryReadingCardBarLabel(epub(chapterTitle = null), 25 * Minute))
        assertEquals("63%", libraryReadingCardBarLabel(comic(), null))
        assertEquals("Not started", libraryReadingCardBarLabel(null, 25 * Minute))
    }

    @Test
    fun `time left reads in minutes, then hours and minutes, then whole hours`() {
        assertEquals("Under a minute left", libraryReadingTimeLeftLabel(20_000L))
        assertEquals("25 min left", libraryReadingTimeLeftLabel(25 * Minute + 10_000L))
        assertEquals("1 h 20 min left", libraryReadingTimeLeftLabel(80 * Minute))
        assertEquals("2 h left", libraryReadingTimeLeftLabel(120 * Minute))
        assertEquals("13 h left", libraryReadingTimeLeftLabel(12 * 60 * Minute + 40 * Minute))
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
