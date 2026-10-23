package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBookPageModelTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 10, 23, 15, 0).toInstant(zone).toEpochMilli()
    private val yesterday = LocalDateTime.of(2026, 10, 22, 21, 0).toInstant(zone).toEpochMilli()

    @Test
    fun `a comic issue reads as its series and number, placed in the series`() {
        val issue = issue(id = "2", number = "2", writers = listOf("Kira Vance"), pencillers = listOf("Dax Moreno"))
        val progress = ItemProgressSummary(label = "", fraction = 0.38f, currentPage = 3, pageCount = 8, lastReadAtEpochMillis = yesterday)

        val text = libraryBookPageText(issue, progress, LibrarySeriesPosition(index = 2, count = 3), now, zone)

        assertEquals("Neon Ronin #2", text.title)
        assertEquals("Neon Ronin · 2 of 3", text.seriesLine)
        assertEquals("Kira Vance, Dax Moreno", text.creators)
        assertEquals("Continue · page 3 of 8", text.primaryAction)
        assertEquals("38% · read yesterday", text.progressLine)
        assertEquals("3 / 8", text.progressPages)
        assertEquals("Format" to "CBZ · 8 pages", text.facts.first())
    }

    @Test
    fun `a Kavita volume reads as its series and volume, not Chapter v1`() {
        val volume = issue(id = "v1", number = "v1", series = "Chainsaw Man").let { item ->
            item.copy(comicMetadata = item.comicMetadata?.copy(issueTitle = "Chapter v1"))
        }

        val text = libraryBookPageText(volume, null, LibrarySeriesPosition(index = 1, count = 24), now, zone)

        assertEquals("Chainsaw Man", text.title)
        assertEquals("Volume 1 · 1 of 24", text.seriesLine)
    }

    @Test
    fun `the primary action follows the reading state`() {
        val book = book(id = "b", title = "The Salt Orchard")
        assertEquals("Start reading", libraryBookPageText(book, null, null, now, zone).primaryAction)
        assertEquals(
            "Continue · chapter 4 of 6",
            libraryBookPageText(book, ItemProgressSummary(label = "", fraction = 0.5f, chapter = 4, chapterCount = 6), null, now, zone)
                .primaryAction,
        )
        assertEquals(
            "Read again",
            libraryBookPageText(book, ItemProgressSummary(label = "", fraction = 1f, isFinished = true), null, now, zone).primaryAction,
        )
        val series = buildKavitaSeriesContainer(seriesKey = "kavita:library:k::7", issues = listOf(kavitaIssue("1"), kavitaIssue("2")))
        assertEquals("Show issues", libraryBookPageText(series, null, null, now, zone).primaryAction)
        assertEquals("2 issues", libraryBookPageText(series, null, null, now, zone).seriesLine)
    }

    @Test
    fun `descriptions lose their markup but keep paragraphs`() {
        assertEquals(
            "Kai has one night.\nThe second issue & more.",
            plainTextDescription("<p>Kai has <b>one</b> night.</p><p>The second issue &amp; more.</p>"),
        )
        assertEquals("Line one\nLine two", plainTextDescription("Line one<br/>Line two"))
        // Kavita's AniList summaries end with Markdown links.
        assertEquals(
            "Yuta meets Eri. Official English",
            plainTextDescription("Yuta meets Eri. [Official English](https://www.viz.com/manga-books/goodbye-eri/7484)"),
        )
        assertEquals("A [bracketed] aside", plainTextDescription("A [bracketed] aside"))
    }

    @Test
    fun `languages read as names, not codes`() {
        assertEquals("English", libraryLanguageName("en", Locale.ENGLISH))
        assertEquals("Japanese", libraryLanguageName("ja", Locale.ENGLISH))
        assertEquals("Portuguese (Brazil)", libraryLanguageName("pt-BR", Locale.ENGLISH))
        assertEquals("English", libraryLanguageName("English", Locale.ENGLISH))
    }

    @Test
    fun `more by the author shows one book per other series, by name`() {
        // Kavita volumes are all titled "Chapter vN"; the row used to sort on that.
        fun volume(id: String, series: String, number: String) = issue(
            id = id,
            number = number,
            series = series,
            writers = listOf("Fujimoto Tatsuki"),
        ).let { item -> item.copy(title = "Chapter $number", comicMetadata = item.comicMetadata?.copy(issueTitle = "Chapter $number")) }
        val eri = volume("eri", "Goodbye, Eri", "v1")
        val pool = listOf(
            volume("csm10", "Chainsaw Man", "v10"),
            volume("fp1", "Fire Punch", "v1"),
            volume("csm1", "Chainsaw Man", "v1"),
            volume("fp2", "Fire Punch", "v2"),
            eri,
        )

        val related = libraryBookPageRelated(item = eri, pool = pool)

        assertEquals(listOf("csm1", "fp1"), related.moreByAuthor.map { it.remoteId })
    }

    @Test
    fun `similar shows one book per series`() {
        val item = issue(id = "x", number = "1", series = "Chainsaw Man", genres = listOf("Action", "Horror"))
        val fire = (1..4).map { number ->
            issue(id = "f$number", number = "$number", series = "Fire Punch", genres = listOf("Action", "Horror"))
        }
        val other = issue(id = "o", number = "1", series = "Dorohedoro", genres = listOf("Action", "Horror"))

        val related = libraryBookPageRelated(item = item, pool = fire + other + item)

        // Ties go by title: Dorohedoro, then the first Fire Punch volume only.
        assertEquals(listOf("o", "f1"), related.similar.map { it.remoteId })
    }

    @Test
    fun `last read reads as a day, not a timestamp`() {
        assertEquals("read today", libraryLastReadLabel(now - 60_000L, now, zone))
        assertEquals("read yesterday", libraryLastReadLabel(yesterday, now, zone))
        assertEquals("read 3 days ago", libraryLastReadLabel(now - 3 * DayMillis, now, zone))
        assertTrue(libraryLastReadLabel(now - 30 * DayMillis, now, zone)!!.startsWith("read on 23 "))
        assertNull(libraryLastReadLabel(0L, now, zone))
    }

    @Test
    fun `related rows show the series in order, then the author, then similar books`() {
        val first = issue(id = "1", number = "1", genres = listOf("Action", "Cyberpunk"))
        val second = issue(id = "2", number = "2", genres = listOf("Action", "Cyberpunk"))
        val third = issue(id = "3", number = "3")
        val byWriter = issue(id = "w", number = "1", series = "Hollow Moon", writers = listOf("Kira Vance"))
        val similar = issue(id = "s", number = "1", series = "Star Courier", genres = listOf("cyberpunk", "Action"))
        val unrelated = issue(id = "u", number = "1", series = "Glass", genres = listOf("Action"))

        val related = libraryBookPageRelated(
            item = second.copy(comicMetadata = second.comicMetadata?.copy(writers = listOf("Kira Vance"))),
            pool = listOf(third, similar, first, byWriter, unrelated, second),
        )

        assertEquals("Neon Ronin", related.seriesName)
        assertEquals(LibrarySeriesPosition(index = 2, count = 3), related.seriesPosition)
        assertEquals(listOf("1", "3"), related.moreInSeries.map { it.remoteId })
        assertEquals("Kira Vance", related.authorName)
        assertEquals(listOf("w"), related.moreByAuthor.map { it.remoteId })
        // Sharing two genres or tags counts as similar; series mates are already in their row.
        assertEquals(listOf("s"), related.similar.map { it.remoteId })
    }

    @Test
    fun `a series container is compared with other series, not with issues`() {
        val container = buildKavitaSeriesContainer(seriesKey = "kavita:library:k::7", issues = listOf(kavitaIssue("1")))
        val related = libraryBookPageRelated(item = container, pool = listOf(container, kavitaIssue("1"), kavitaIssue("2")))

        assertNull(related.seriesName)
        assertTrue(related.moreInSeries.isEmpty())
        assertTrue(related.moreByAuthor.isEmpty())
    }

    @Test
    fun `read and file actions follow the source and the file`() {
        val grimmory = book(id = "g", title = "Book").copy(sourceType = SourceType.GRIMMORY, isLocalImport = false)
        assertEquals(LibraryBookReadAction.MarkFinished, libraryBookReadAction(grimmory, null))
        assertEquals(
            LibraryBookReadAction.MarkFinished,
            libraryBookReadAction(grimmory, ItemProgressSummary(label = "", fraction = 0.4f)),
        )
        assertEquals(
            LibraryBookReadAction.MarkUnread,
            libraryBookReadAction(grimmory, ItemProgressSummary(label = "", fraction = 1f, isFinished = true)),
        )
        val local = book(id = "l", title = "Local")
        assertNull(libraryBookReadAction(local, null))
        assertEquals(listOf(LibraryBookFileAction.RemoveImported), libraryBookFileActions(local, LibraryItemActionContext()))
        assertEquals(
            listOf(LibraryBookFileAction.Downloading),
            libraryBookFileActions(grimmory, LibraryItemActionContext(downloadingItemIds = setOf(grimmory.id))),
        )
        assertTrue(libraryBookFileActions(grimmory, LibraryItemActionContext()).isEmpty())
    }
}

private const val DayMillis = 24L * 60 * 60 * 1000

private fun issue(
    id: String,
    number: String,
    series: String = "Neon Ronin",
    writers: List<String> = emptyList(),
    pencillers: List<String> = emptyList(),
    genres: List<String> = emptyList(),
): ReadableItem = ReadableItem(
    id = "local:item:$id",
    sourceType = SourceType.LOCAL,
    libraryId = "local",
    remoteId = id,
    isLocalImport = true,
    title = "$series $number",
    format = FormatType.CBZ,
    pageCount = 8,
    comicMetadata = ComicMetadata(
        series = series,
        issueNumber = number,
        writers = writers,
        pencillers = pencillers,
        genres = genres,
    ),
)

private fun book(
    id: String,
    title: String,
): ReadableItem = ReadableItem(
    id = "local:item:$id",
    sourceType = SourceType.LOCAL,
    libraryId = "local",
    isLocalImport = true,
    title = title,
    format = FormatType.EPUB,
    bookMetadata = BookMetadata(author = "Mira Kalden"),
)

private fun kavitaIssue(id: String): ReadableItem = ReadableItem(
    id = "kavita:item:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:k",
    remoteId = id,
    remoteFileType = "7",
    title = "Akira $id",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(series = "Akira", issueNumber = id),
)
