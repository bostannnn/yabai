package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.Url
import org.robolectric.RobolectricTestRunner

// Readium's Url parses through android.net.Uri.
@RunWith(RobolectricTestRunner::class)
class EpubFootnoteSheetTest {
    @Test
    fun `a numbered note is headed with its number and read without it`() {
        val heading = epubFootnoteHeading("12. The ferry to Kessel stopped running in 1974.")

        assertEquals("Note 12", heading.title)
        assertEquals("The ferry to Kessel stopped running in 1974.", heading.text)
    }

    @Test
    fun `bracketed numbers count as the note's mark`() {
        assertEquals(EpubFootnoteHeading("Note 3", "See the map."), epubFootnoteHeading("[3] See the map."))
    }

    @Test
    fun `a note without a number keeps its text as it is`() {
        assertEquals(EpubFootnoteHeading("Note", "* Translated by the author."), epubFootnoteHeading("* Translated by the author."))
        assertEquals(EpubFootnoteHeading("Note", "In 1974 the bridge opened."), epubFootnoteHeading("  In 1974 the bridge opened. "))
    }

    @Test
    fun `a note in another file of the book is an endnote, whatever the anchors`() {
        val chapter = requireNotNull(Url.fromDecodedPath("OEBPS/chapter-1.xhtml"))
        val sameFileNote = requireNotNull(Url("OEBPS/chapter-1.xhtml#fn12"))
        val notesFile = requireNotNull(Url("OEBPS/notes.xhtml#n12"))

        assertTrue(isSameEpubResource(sameFileNote, chapter))
        assertFalse(isSameEpubResource(notesFile, chapter))
    }

    @Test
    fun `the way back names the page it returns to`() {
        assertEquals("Back to page 4", epubNoteReturnLabel(4))
        assertEquals("Back to where you were", epubNoteReturnLabel(0))
    }
}
