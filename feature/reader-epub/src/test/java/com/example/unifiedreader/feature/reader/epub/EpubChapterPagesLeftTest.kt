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
class EpubChapterPagesLeftTest {
    // Three files: positions 1-10, 11-30, 31-40; the book has 40.
    private val files = listOf(
        EpubResourcePositions(firstPosition = 1, count = 10),
        EpubResourcePositions(firstPosition = 11, count = 20),
        EpubResourcePositions(firstPosition = 31, count = 10),
    )

    @Test
    fun `a chapter that is the open file counts its own pages exactly`() {
        // Page 3 of 8 (index 2): five more turns.
        assertEquals(5, pagesLeft(pageIndex = 2, totalPages = 8, position = 3, chapterEnd = 11))
        assertEquals(0, pagesLeft(pageIndex = 7, totalPages = 8, position = 10, chapterEnd = 11))
    }

    @Test
    fun `a chapter's later files are estimated at the open file's pages per position`() {
        // 4 pages for 10 positions here, and 20 more positions in the chapter: 8 more turns.
        assertEquals(3 + 8, pagesLeft(pageIndex = 0, totalPages = 4, position = 1, chapterEnd = 31))
    }

    @Test
    fun `a chapter ending inside the open file counts up to it, never past the file`() {
        // 10 pages for 10 positions; the next chapter starts at position 7.
        assertEquals(3, pagesLeft(pageIndex = 2, totalPages = 10, position = 3, chapterEnd = 7))
        assertEquals(0, pagesLeft(pageIndex = 6, totalPages = 10, position = 7, chapterEnd = 7))
    }

    @Test
    fun `nothing until the file's pages are known or the position is in the file`() {
        assertNull(epubChapterPagesLeft(EpubResourcePage(0, null), files[0], 1, 11))
        assertNull(epubChapterPagesLeft(EpubResourcePage(-1, 8), files[0], 1, 11))
        assertNull(epubChapterPagesLeft(EpubResourcePage(0, 8), files[0], 12, 11))
        assertNull(epubChapterPagesLeft(EpubResourcePage(0, 8), files[0], 1, null))
    }

    @Test
    fun `the file is the one whose positions hold the page`() {
        assertEquals(files[1], files.epubResourceAt(11))
        assertEquals(files[1], files.epubResourceAt(30))
        assertNull(files.epubResourceAt(41))
    }

    @Test
    fun `a chapter ends where the next one at its level starts, the last at the book's end`() {
        val entries = listOf(
            entry("one", "a.xhtml", position = 1),
            entry("one-a", "b.xhtml", position = 11, depth = 1),
            entry("two", "c.xhtml", position = 31),
        )
        assertEquals(31, entries.epubChapterEndPosition(currentProgress = 0.4, currentHref = "b.xhtml", pageCount = 40))
        assertEquals(41, entries.epubChapterEndPosition(currentProgress = 0.9, currentHref = "c.xhtml", pageCount = 40))
        assertNull(emptyList<EpubTocEntry>().epubChapterEndPosition(0.5, "a.xhtml", pageCount = 40))
    }

    @Test
    fun `the corner says how many turns are left, shorter on a phone`() {
        assertEquals("6 left in chapter", epubChapterPagesLeftLabel(6, short = false))
        assertEquals("6 left", epubChapterPagesLeftLabel(6, short = true))
        assertEquals("End of chapter", epubChapterPagesLeftLabel(0, short = false))
        assertEquals("1 page left in the chapter", epubChapterPagesLeftDescription(1))
    }

    private fun pagesLeft(pageIndex: Int, totalPages: Int, position: Int, chapterEnd: Int): Int? =
        epubChapterPagesLeft(
            resourcePage = EpubResourcePage(pageIndex = pageIndex, totalPages = totalPages),
            resource = files.epubResourceAt(position),
            currentPosition = position,
            chapterEndPosition = chapterEnd,
        )

    private fun entry(id: String, href: String, position: Int, depth: Int = 0): EpubTocEntry = EpubTocEntry(
        id = id,
        title = id,
        depth = depth,
        href = href,
        progression = (position - 1) / 40.0,
        position = position,
        link = Link(href = checkNotNull(Href(href)), title = id),
    )
}
