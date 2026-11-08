package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubSameFileLinksTest {
    private val chapter = requireNotNull(Url("OEBPS/Text/part0005.html"))

    @Test
    fun `an anchor in the page's file becomes a link into that file`() {
        val link = requireNotNull(epubSameFileLink(chapter, "#ref1fm1"))
        assertEquals("OEBPS/Text/part0005.html#ref1fm1", link.url().toString())
        assertEquals("ref1fm1", link.url().fragment)
        assertEquals(chapter.toString(), link.url().removeFragment().toString())
    }

    @Test
    fun `an encoded anchor keeps its id`() {
        assertEquals("note-é", epubSameFileLink(chapter, "#note-%C3%A9")?.url()?.fragment)
    }

    @Test
    fun `no anchor, no link`() {
        assertNull(epubSameFileLink(chapter, ""))
        assertNull(epubSameFileLink(chapter, "#"))
        assertNull(epubSameFileLink(chapter, "ref1fm1"))
    }

    @Test
    fun `the bridge hands the link on`() {
        val received = mutableListOf<Link>()
        val bridge = EpubSameFileLinkBridge(resource = chapter, onLink = received::add)
        bridge.onLink("#ref2fm1")
        bridge.onLink("")
        assertEquals(listOf("OEBPS/Text/part0005.html#ref2fm1"), received.map { link -> link.url().toString() })
    }
}
