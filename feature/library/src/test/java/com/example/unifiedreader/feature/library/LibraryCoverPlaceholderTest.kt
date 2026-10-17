package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCoverPlaceholderTest {
    @Test
    fun `epub gets a title cover with its author`() {
        val spec = libraryCoverPlaceholderSpec(
            item(
                title = "The Secret History",
                format = FormatType.EPUB,
                bookMetadata = BookMetadata(author = "Donna Tartt"),
            ),
        )

        assertEquals(LibraryCoverPlaceholderStyle.TitleCover, spec.style)
        assertEquals("The Secret History", spec.heading)
        assertEquals("Donna Tartt", spec.byline)
        assertEquals("EPUB", spec.formatLabel)
        assertNull(spec.number)
    }

    @Test
    fun `pdf gets a title cover even when it has an issue number`() {
        val spec = libraryCoverPlaceholderSpec(
            item(
                title = "Swamp Thing 52",
                format = FormatType.PDF,
                comicMetadata = ComicMetadata(series = "Swamp Thing", issueNumber = "52"),
            ),
        )

        assertEquals(LibraryCoverPlaceholderStyle.TitleCover, spec.style)
        assertEquals("PDF", spec.formatLabel)
    }

    @Test
    fun `cbz with series and issue gets a series cover`() {
        val spec = libraryCoverPlaceholderSpec(
            item(
                title = "Swamp Thing 052",
                format = FormatType.CBZ,
                comicMetadata = ComicMetadata(
                    series = "Swamp Thing",
                    issueNumber = "#52",
                    issueTitle = "Natural Consequences",
                ),
            ),
        )

        assertEquals(LibraryCoverPlaceholderStyle.SeriesNumber, spec.style)
        assertEquals("Swamp Thing", spec.heading)
        assertEquals("#52", spec.number)
        assertNull(spec.numberPrefix)
        assertEquals("Natural Consequences", spec.byline)
    }

    @Test
    fun `cbr with only a volume shows the volume number`() {
        val spec = libraryCoverPlaceholderSpec(
            item(
                title = "Chainsaw Man v04",
                format = FormatType.CBR,
                comicMetadata = ComicMetadata(series = "Chainsaw Man", volume = "Vol. 4"),
            ),
        )

        assertEquals(LibraryCoverPlaceholderStyle.SeriesNumber, spec.style)
        assertEquals("4", spec.number)
        assertEquals("Vol.", spec.numberPrefix)
    }

    @Test
    fun `comic without a number falls back to a title cover with its writer`() {
        val spec = libraryCoverPlaceholderSpec(
            item(
                title = "Look Back",
                format = FormatType.CBZ,
                comicMetadata = ComicMetadata(series = "Look Back", writers = listOf("Tatsuki Fujimoto")),
            ),
        )

        assertEquals(LibraryCoverPlaceholderStyle.TitleCover, spec.style)
        assertEquals("Tatsuki Fujimoto", spec.byline)
    }

    @Test
    fun `issues of one series share a color`() {
        val first = libraryCoverPlaceholderSpec(seriesIssue(series = "Swamp Thing", issue = "52"))
        val second = libraryCoverPlaceholderSpec(seriesIssue(series = "swamp thing ", issue = "53"))

        assertEquals(first.palette, second.palette)
    }

    @Test
    fun `colors differ across titles and stay stable for one title`() {
        val titles = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel")
        val palettes = titles.map { title -> libraryCoverPlaceholderSpec(item(title, FormatType.EPUB)).palette }

        assertTrue(palettes.toSet().size > 1)
        assertEquals(palettes, titles.map { title -> libraryCoverPlaceholderSpec(item(title, FormatType.EPUB)).palette })
    }

    @Test
    fun `underscores in filename titles become spaces`() {
        val spec = libraryCoverPlaceholderSpec(item("Monumentalnoe_iskusstvo__SSSR", FormatType.PDF))

        assertEquals("Monumentalnoe iskusstvo SSSR", spec.heading)
    }

    @Test
    fun `a break inside a word splits it but a break at a space or between cjk characters does not`() {
        assertTrue(splitsWordAt("Monumentalnoe", 6))
        assertFalse(splitsWordAt("The Secret History", 4))
        assertFalse(splitsWordAt("さよなら絵梨", 3))
        assertFalse(splitsWordAt("abc", 0))
        assertFalse(splitsWordAt("abc", 3))
    }

    @Test
    fun `different series usually get different colors`() {
        val a = libraryCoverPlaceholderSpec(seriesIssue(series = "Swamp Thing", issue = "1")).palette
        val b = libraryCoverPlaceholderSpec(seriesIssue(series = "Chainsaw Man", issue = "1")).palette

        assertNotEquals(a, b)
    }
}

private fun seriesIssue(
    series: String,
    issue: String,
): ReadableItem = item(
    title = "$series $issue",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(series = series, issueNumber = issue),
)

private fun item(
    title: String,
    format: FormatType,
    bookMetadata: BookMetadata? = null,
    comicMetadata: ComicMetadata? = null,
): ReadableItem = ReadableItem(
    id = "local:$title",
    sourceType = SourceType.LOCAL,
    libraryId = "local",
    title = title,
    format = format,
    bookMetadata = bookMetadata,
    comicMetadata = comicMetadata,
)
