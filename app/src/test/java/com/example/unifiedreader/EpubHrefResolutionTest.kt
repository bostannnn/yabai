package com.example.unifiedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubHrefResolutionTest {
    @Test
    fun `href resolves against the package directory`() {
        assertEquals("OPS/images/cover.jpg", resolveEpubHref("OPS/content.opf", "images/cover.jpg"))
    }

    @Test
    fun `raw space in href is kept`() {
        assertEquals("OPS/images/my cover.png", resolveEpubHref("OPS/content.opf", "images/my cover.png"))
    }

    @Test
    fun `percent escapes are decoded`() {
        assertEquals("OPS/images/my cover.png", resolveEpubHref("OPS/content.opf", "images/my%20cover.png"))
        assertEquals("OPS/images/обложка.jpg", resolveEpubHref("OPS/content.opf", "images/%D0%BE%D0%B1%D0%BB%D0%BE%D0%B6%D0%BA%D0%B0.jpg"))
    }

    @Test
    fun `percent sign that is not an escape is kept`() {
        assertEquals("OPS/images/100%.jpg", resolveEpubHref("OPS/content.opf", "images/100%.jpg"))
        assertEquals("OPS/images/50%off.jpg", resolveEpubHref("OPS/content.opf", "images/50%off.jpg"))
    }

    @Test
    fun `parent and current segments are normalized`() {
        assertEquals("images/cover.jpg", resolveEpubHref("OPS/content.opf", "../images/cover.jpg"))
        assertEquals("OPS/images/cover.jpg", resolveEpubHref("OPS/content.opf", "./images/./cover.jpg"))
    }

    @Test
    fun `package at the archive root and absolute hrefs`() {
        assertEquals("images/cover.jpg", resolveEpubHref("content.opf", "images/cover.jpg"))
        assertEquals("OEBPS/cover.jpg", resolveEpubHref("OPS/content.opf", "/OEBPS/cover.jpg"))
    }

    @Test
    fun `fragment is dropped and empty href gives nothing`() {
        assertEquals("OPS/cover.xhtml", resolveEpubHref("OPS/content.opf", "cover.xhtml#top"))
        assertNull(resolveEpubHref("OPS/content.opf", "#top"))
    }
}
