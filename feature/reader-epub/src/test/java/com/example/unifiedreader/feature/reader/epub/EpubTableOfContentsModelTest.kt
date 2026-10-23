package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EpubTableOfContentsModelTest {
    @Test
    fun activeEntryPrefersCurrentHrefWhenPresent() {
        val entries = listOf(
            tocEntry(
                id = "one",
                title = "Chapter 1",
                href = "https://example.com/chapter-1.xhtml",
                progression = 0.1,
            ),
            tocEntry(
                id = "two",
                title = "Chapter 2",
                href = "https://example.com/chapter-2.xhtml",
                progression = 0.6,
            ),
        )

        val activeEntry = entries.activeEpubTocEntry(
            currentProgress = 0.15,
            currentHref = "https://example.com/chapter-2.xhtml",
        )

        assertEquals("two", activeEntry?.id)
    }

    @Test
    fun activeEntryFallsBackToClosestProgressionWhenHrefMissing() {
        val entries = listOf(
            tocEntry(
                id = "one",
                title = "Chapter 1",
                href = "https://example.com/chapter-1.xhtml",
                progression = 0.1,
            ),
            tocEntry(
                id = "two",
                title = "Chapter 2",
                href = "https://example.com/chapter-2.xhtml",
                progression = 0.55,
            ),
            tocEntry(
                id = "three",
                title = "Chapter 3",
                href = "https://example.com/chapter-3.xhtml",
                progression = 0.82,
            ),
        )

        val activeEntry = entries.activeEpubTocEntry(
            currentProgress = 0.6,
            currentHref = null,
        )

        assertEquals("two", activeEntry?.id)
    }

    @Test
    fun activeEntryReturnsNullWithoutHrefOrProgressionMatch() {
        val entries = listOf(
            tocEntry(
                id = "one",
                title = "Front Matter",
                href = "https://example.com/front.xhtml",
                progression = null,
            ),
        )

        val activeEntry = entries.activeEpubTocEntry(
            currentProgress = 0.0,
            currentHref = null,
        )

        assertNull(activeEntry)
    }

    @Test
    fun chapterPositionCountsTopLevelEntriesAndResolvesNestedOnesToTheirChapter() {
        val entries = listOf(
            tocEntry(id = "c1", title = "Chapter 1", href = "https://example.com/1.xhtml", progression = 0.0),
            tocEntry(id = "c1a", title = "Part a", href = "https://example.com/1.xhtml", progression = 0.05, depth = 1),
            tocEntry(id = "c2", title = "Chapter 2", href = "https://example.com/2.xhtml", progression = 0.3),
            tocEntry(id = "c2a", title = "Part a", href = "https://example.com/2.xhtml", progression = 0.35, depth = 1),
            tocEntry(id = "c2b", title = "Part b", href = "https://example.com/2.xhtml", progression = 0.4, depth = 2),
            tocEntry(id = "c3", title = "Chapter 3", href = "https://example.com/3.xhtml", progression = 0.7),
        )

        assertEquals(EpubTocChapterPosition(index = 1, count = 3), entries.epubTocChapterPosition(entries[0]))
        assertEquals(EpubTocChapterPosition(index = 2, count = 3), entries.epubTocChapterPosition(entries[4]))
        assertEquals(EpubTocChapterPosition(index = 3, count = 3), entries.epubTocChapterPosition(entries[5]))
        assertNull(entries.epubTocChapterPosition(null))
        assertNull(
            entries.epubTocChapterPosition(
                tocEntry(id = "elsewhere", title = "x", href = "https://example.com/x.xhtml", progression = 0.9),
            ),
        )
    }

    @Test
    fun chaptersAreTheChildrenOfAnEntryThatWrapsMostOfTheContents() {
        // Девица Кристина: a foreword, the title entry holding I to XVIII, then the notes.
        val chapters = (1..18).map { number ->
            tocEntry(
                id = "ch$number",
                title = "Chapter $number",
                href = "https://example.com/part$number.xhtml",
                progression = number / 20.0,
                depth = 1,
            )
        }
        val entries = listOf(
            tocEntry(id = "foreword", title = "Foreword", href = "https://example.com/foreword.xhtml", progression = 0.01),
            tocEntry(id = "book", title = "Book", href = "https://example.com/part1.xhtml", progression = 0.05),
        ) + chapters + tocEntry(id = "notes", title = "Notes", href = "https://example.com/notes.xhtml", progression = 0.99)

        assertEquals((2..19).toList(), entries.epubTocChapterIndices())
        assertEquals(EpubTocChapterPosition(index = 6, count = 18), entries.epubTocChapterPosition(chapters[5]))
        assertNull(entries.epubTocChapterPosition(entries.first()))
        assertNull(entries.epubTocChapterPosition(entries.last()))
    }

    @Test
    fun chaptersStayTopLevelWhenNoEntryWrapsMostOfTheContents() {
        val entries = (1..3).flatMap { part ->
            listOf(tocEntry(id = "part$part", title = "Part $part", href = "https://example.com/p$part.xhtml", progression = part / 4.0)) +
                (1..3).map { chapter ->
                    tocEntry(
                        id = "part$part-$chapter",
                        title = "Chapter $chapter",
                        href = "https://example.com/p$part-$chapter.xhtml",
                        progression = part / 4.0 + chapter / 20.0,
                        depth = 1,
                    )
                }
        }

        assertEquals(listOf(0, 4, 8), entries.epubTocChapterIndices())
        assertEquals(EpubTocChapterPosition(index = 2, count = 3), entries.epubTocChapterPosition(entries[6]))
    }

    @Test
    fun finishedEntriesStartBeforeTheActiveOneAndNeverIncludeIt() {
        val first = tocEntry(id = "c1", title = "Chapter 1", href = "https://example.com/1.xhtml", progression = 0.0)
        val active = tocEntry(id = "c2", title = "Chapter 2", href = "https://example.com/2.xhtml", progression = 0.3)
        val later = tocEntry(id = "c3", title = "Chapter 3", href = "https://example.com/3.xhtml", progression = 0.7)
        val unknown = tocEntry(id = "c4", title = "Chapter 4", href = "https://example.com/4.xhtml", progression = null)

        assertEquals(true, first.isEpubTocEntryFinished(active))
        assertEquals(false, active.isEpubTocEntryFinished(active))
        assertEquals(false, later.isEpubTocEntryFinished(active))
        assertEquals(false, unknown.isEpubTocEntryFinished(active))
        assertEquals(false, first.isEpubTocEntryFinished(null))
        assertEquals(false, first.isEpubTocEntryFinished(unknown))
    }

    @Test
    fun chapterEntryIsTheChapterAroundTheActiveSection() {
        val one = tocEntry(id = "one", title = "Глава 1", href = "https://example.com/1.xhtml", progression = 0.0)
        val three = tocEntry(id = "three", title = "Глава 3", href = "https://example.com/3.xhtml", progression = 0.4)
        val river = tocEntry(id = "river", title = "The river", href = "https://example.com/3b.xhtml", progression = 0.45, depth = 1)
        val four = tocEntry(id = "four", title = "Глава 4", href = "https://example.com/4.xhtml", progression = 0.6)
        val entries = listOf(one, three, river, four)

        assertEquals("Глава 3", entries.epubTocChapterEntry(river)?.title)
        assertEquals("Глава 1", entries.epubTocChapterEntry(one)?.title)
        assertNull(entries.epubTocChapterEntry(null))
    }

    @Test
    fun entryFractionRunsToTheNextEntryAtItsLevel() {
        val three = tocEntry(id = "three", title = "Chapter 3", href = "https://example.com/3.xhtml", progression = 0.4)
        val river = tocEntry(id = "river", title = "The river", href = "https://example.com/3b.xhtml", progression = 0.45, depth = 1)
        val four = tocEntry(id = "four", title = "Chapter 4", href = "https://example.com/4.xhtml", progression = 0.6)
        val entries = listOf(three, river, four)

        // Chapter 3 spans 0.4 to 0.6, so 0.48 is 40% through it.
        assertEquals(0.4f, entries.epubTocEntryFraction(three, currentProgress = 0.48)!!, 0.001f)
        // The last entry runs to the end of the book.
        assertEquals(0.5f, entries.epubTocEntryFraction(four, currentProgress = 0.8)!!, 0.001f)
        assertEquals("40% through", epubTocThroughLabel(0.4f))
    }

    @Test
    fun labelsForTheBarsAndThePanel() {
        assertEquals("Глава 3 · Анна Лесная", epubReaderSubtitle(chapterTitle = "Глава 3", author = "Анна Лесная"))
        assertEquals("Анна Лесная", epubReaderSubtitle(chapterTitle = null, author = "Анна Лесная"))
        assertNull(epubReaderSubtitle(chapterTitle = " ", author = null))
        assertEquals("p. 37 / 412", buildEpubDockPositionLabel(position = 37, pageCount = 412))
        assertEquals("412 pages", buildEpubDockPositionLabel(position = 0, pageCount = 412))
        assertEquals("A Short History of Rain · 6 chapters", epubTocSubtitle(bookTitle = "A Short History of Rain", chapterCount = 6))
        assertEquals("1 chapter", epubTocSubtitle(bookTitle = "", chapterCount = 1))
    }

    private fun tocEntry(
        id: String,
        title: String,
        href: String,
        progression: Double?,
        position: Int? = null,
        depth: Int = 0,
    ): EpubTocEntry = EpubTocEntry(
        id = id,
        title = title,
        depth = depth,
        href = href,
        progression = progression,
        position = position,
        link = Link(
            href = checkNotNull(Href(href)),
            title = title,
        ),
    )
}
