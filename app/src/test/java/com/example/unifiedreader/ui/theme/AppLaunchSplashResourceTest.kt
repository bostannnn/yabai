package com.example.unifiedreader.ui.theme

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AppLaunchSplashResourceTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val res = File("src/main/res")

    @Test
    fun `older Android has a static icon and Android 12 has a finite native animation`() {
        assertEquals("vector", read("drawable/launch_mark.xml").tagName)
        val animated = read("drawable-v31/launch_mark.xml")
        assertEquals("animated-vector", animated.tagName)
        val targets = elements(animated, "target")
        val names = (elements(animated, "group") + elements(animated, "path"))
            .map { it.getAttributeNS(android, "name") }.filter { it.isNotBlank() }
        assertEquals("Every animation target must resolve unambiguously", names.size, names.toSet().size)
        targets.forEach { assertTrue(it.getAttributeNS(android, "name") in names) }
        elements(animated, "objectAnimator").forEach {
            assertTrue(it.getAttributeNS(android, "duration").toInt() in 1..1000)
            assertTrue(it.getAttributeNS(android, "repeatCount") in listOf("", "0"))
        }
        assertTrue(targets.any { it.getAttributeNS(android, "name") == "page_back" })
        assertTrue(targets.any { it.getAttributeNS(android, "name") == "character" })
    }

    @Test
    fun `face is opaque and foreground cover hides the body behind the rim`() {
        val vector = read("drawable/launch_mark.xml")
        val paths = elements(vector, "path").associateBy { it.getAttributeNS(android, "name") }
        assertEquals("?attr/launchMarkColor", paths.getValue("head_solid_0").getAttributeNS(android, "fillColor"))
        assertEquals("?attr/launchPaperColor", paths.getValue("eyes_open_0").getAttributeNS(android, "fillColor"))
        assertEquals("?attr/launchPaperColor", paths.getValue("book_front_paper_0").getAttributeNS(android, "fillColor"))
        assertFalse(paths.getValue("head_solid_0").hasAttributeNS(android, "fillAlpha") &&
            paths.getValue("head_solid_0").getAttributeNS(android, "fillAlpha") == "0")
        val groups = elements(vector, "group").map { it.getAttributeNS(android, "name") }
        assertTrue(groups.indexOf("book_back") < groups.indexOf("character"))
        assertTrue(groups.indexOf("character") < groups.indexOf("book_front"))
        assertTrue(groups.indexOf("book_front") < groups.indexOf("paws"))
    }

    @Test
    fun `all animation channels return to the starting pose`() {
        elements(read("drawable-v31/launch_mark.xml"), "propertyValuesHolder").forEach { holder ->
            val frames = elements(holder, "keyframe")
            assertEquals("0", frames.first().getAttributeNS(android, "fraction"))
            assertEquals("1", frames.last().getAttributeNS(android, "fraction"))
            assertEquals(holder.getAttributeNS(android, "propertyName"),
                frames.first().getAttributeNS(android, "value").toFloat(),
                frames.last().getAttributeNS(android, "value").toFloat(), .00001f)
        }
    }

    private fun read(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(res, path)).documentElement

    private fun elements(root: Element, tag: String): List<Element> = root.getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
