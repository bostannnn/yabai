package com.example.unifiedreader.feature.reader.epub

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EpubFootnotesTest {
    @Test
    fun `contents, chapter and notes-section targets navigate`() {
        val chapter = page("""<section id="ch5"><h1 id="start">Chapter Five</h1><p>1. It was a dark night.</p></section>""")
        assertNull(extractEpubNoteText(chapter, targetId = "ch5"))
        assertNull(extractEpubNoteText(chapter, targetId = "start"))

        val notesChapter = page(
            """<section id="notes" epub:type="footnotes"><h1>Notes</h1><p id="n1">1. A long enough note.</p></section>""",
        )
        assertNull(extractEpubNoteText(notesChapter, targetId = "notes"))

        // A cross-reference to a body paragraph, and a back-link from a note to its reference.
        val body = page("""<p id="para3">Just a paragraph of body text.</p><p>A claim<sup id="ref12">12</sup>, more text.</p>""")
        assertNull(extractEpubNoteText(body, targetId = "para3"))
        assertNull(extractEpubNoteText(body, targetId = "ref12"))
    }

    @Test
    fun `marked, numbered and marker-led notes are shown`() {
        val marked = page("""<p>Text<a href="#fn1">1</a>.</p><aside epub:type="footnote" id="fn1"><p>The marked note.</p></aside>""")
        assertEquals("The marked note.", extractEpubNoteText(marked, targetId = "fn1"))
        // Found by its markup alone, inside a page that declares <!DOCTYPE html> and uses &nbsp;.
        val markedById = page("""<p id="source-a" role="doc-endnote">Source:&nbsp;the archive.</p>""")
        assertEquals("Source: the archive.", extractEpubNoteText(markedById, targetId = "source-a"))

        val numbered = page("""<ol><li id="n12">A plain endnote in a numbered list.</li></ol>""")
        assertEquals("A plain endnote in a numbered list.", extractEpubNoteText(numbered, targetId = "n12"))

        val markerLed = page("""<p id="x7">7. The source is the 1923 archive.</p>""")
        assertEquals("7. The source is the 1923 archive.", extractEpubNoteText(markerLed, targetId = "x7"))
    }

    @Test
    fun `web links open in another app and other schemes are ignored`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertTrue(activity.openEpubExternalLink("https://example.com/source"))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/source", intent.dataString)

        assertFalse(activity.openEpubExternalLink("javascript:alert(1)"))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private fun page(body: String) = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>t</title></head>
<body>$body</body></html>"""
}
