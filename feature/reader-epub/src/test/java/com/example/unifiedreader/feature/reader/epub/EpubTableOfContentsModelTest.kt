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
