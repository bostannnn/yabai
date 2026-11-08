package com.example.unifiedreader.feature.reader.epub

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubNoteParserTest {
    @Test
    fun `a parser that refuses the safety switches still parses book pages`() {
        val factory = AndroidLikeDocumentBuilderFactory().hardenedForBookPages()

        assertTrue(factory.isNamespaceAware)
        val page = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="ref1">1. A note.</p></body></html>"""
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(page.toByteArray()))
        assertEquals("1. A note.", document.documentElement.textContent)
    }

    // Android's DocumentBuilderFactory throws on every feature it doesn't know and on any
    // setXIncludeAware call; the JVM's (which Robolectric uses) accepts them all.
    private class AndroidLikeDocumentBuilderFactory : DocumentBuilderFactory() {
        override fun newDocumentBuilder(): DocumentBuilder =
            DocumentBuilderFactory.newInstance()
                .also { real -> real.isNamespaceAware = isNamespaceAware }
                .newDocumentBuilder()

        override fun setXIncludeAware(state: Boolean): Unit = throw UnsupportedOperationException("XInclude")

        override fun setFeature(name: String, value: Boolean): Unit = throw ParserConfigurationException(name)

        override fun getFeature(name: String): Boolean = throw ParserConfigurationException(name)

        override fun setAttribute(name: String, value: Any?): Unit = throw IllegalArgumentException(name)

        override fun getAttribute(name: String): Any = throw IllegalArgumentException(name)
    }
}
