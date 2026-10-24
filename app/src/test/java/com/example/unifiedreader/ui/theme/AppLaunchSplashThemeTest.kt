package com.example.unifiedreader.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.example.unifiedreader.feature.settings.ThemeMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The splash styles repeat each theme's background and primary in XML (the splash is drawn before
 * the app runs); these checks keep them equal to UnifiedReaderTheme.kt's colour schemes. App unit
 * tests have no Android resources, so the resource files are read from the source tree.
 */
class AppLaunchSplashThemeTest {

    private val res = File("src/main/res")
    private val styles = readStyles(File(res, "values/themes.xml"))
    private val colors = readColors(File(res, "values/launch_colors.xml"))
    private val nightColors = colors + readColors(File(res, "values-night/launch_colors.xml"))

    @Test
    fun `every theme's splash has its background and its primary for the mark`() {
        ThemeMode.entries.filterNot { it == ThemeMode.SystemDynamic }.forEach { mode ->
            val style = "Theme.UnifiedReader.Launch.${mode.launchSplash.name}"
            val scheme = staticUnifiedReaderColorScheme(mode, systemDarkTheme = mode.isDark)
            assertEquals("$style background", scheme.background.toArgb(), color(style, "windowSplashScreenBackground", colors))
            assertEquals("$style mark", scheme.primary.toArgb(), color(style, "launchMarkColor", colors))
            assertEquals("$style bar icons", (!mode.isDark).toString(), item(style, "android:windowLightStatusBar"))
        }
    }

    @Test
    fun `System Dynamic without dynamic colour follows the device's light and dark schemes`() {
        val style = "Theme.UnifiedReader.Launch.System"
        val light = staticUnifiedReaderColorScheme(ThemeMode.SystemDynamic, systemDarkTheme = false)
        val dark = staticUnifiedReaderColorScheme(ThemeMode.SystemDynamic, systemDarkTheme = true)
        assertEquals(light.background.toArgb(), color(style, "windowSplashScreenBackground", colors))
        assertEquals(light.primary.toArgb(), color(style, "launchMarkColor", colors))
        assertEquals(dark.background.toArgb(), color(style, "windowSplashScreenBackground", nightColors))
        assertEquals(dark.primary.toArgb(), color(style, "launchMarkColor", nightColors))
    }

    @Test
    fun `before Android 13 the splash is Paper on a light device and Graphite on a dark one`() {
        val style = "Theme.UnifiedReader.Launch"
        val paper = staticUnifiedReaderColorScheme(ThemeMode.Paper, systemDarkTheme = false)
        val graphite = staticUnifiedReaderColorScheme(ThemeMode.Graphite, systemDarkTheme = true)
        assertEquals(paper.background.toArgb(), color(style, "windowSplashScreenBackground", colors))
        assertEquals(paper.primary.toArgb(), color(style, "launchMarkColor", colors))
        assertEquals(graphite.background.toArgb(), color(style, "windowSplashScreenBackground", nightColors))
        assertEquals(graphite.primary.toArgb(), color(style, "launchMarkColor", nightColors))
    }

    @Test
    fun `every splash style draws the mark and hands over to the app theme`() {
        AppLaunchSplash.entries.forEach { splash ->
            val style = "Theme.UnifiedReader.Launch.${splash.name}"
            assertEquals("$style icon", "@drawable/launch_mark", item(style, "windowSplashScreenAnimatedIcon"))
            assertEquals("$style next theme", "@style/Theme.UnifiedReader", item(style, "postSplashScreenTheme"))
            assertTrue("$style is used by a theme", ThemeMode.entries.any { it.launchSplash == splash })
        }
    }

    private fun item(style: String, name: String): String {
        var current: String? = style
        while (current != null) {
            val found = styles[current] ?: error("No style $current in themes.xml")
            found.items[name]?.let { return it }
            current = found.parent
        }
        error("$style has no $name")
    }

    private fun color(style: String, name: String, palette: Map<String, String>): Int {
        var value = item(style, name)
        while (value.startsWith("@color/")) {
            value = palette[value.removePrefix("@color/")] ?: error("No colour $value")
        }
        return (0xFF000000 or value.removePrefix("#").toLong(16)).toInt()
    }

    private class Style(val parent: String?, val items: Map<String, String>)

    private fun readStyles(file: File): Map<String, Style> =
        elements(file, "style").associate { style ->
            val name = style.getAttribute("name")
            val parent = style.getAttribute("parent").ifEmpty { name.substringBeforeLast('.', "").ifEmpty { null } }
            name to Style(
                parent = parent?.takeUnless { it.startsWith("Theme.AppCompat") || it == "Theme.SplashScreen" },
                items = elements(style, "item").associate { it.getAttribute("name") to it.textContent.trim() },
            )
        }

    private fun readColors(file: File): Map<String, String> =
        elements(file, "color").associate { it.getAttribute("name") to it.textContent.trim() }

    private fun elements(file: File, tag: String): List<Element> =
        elements(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement, tag)

    private fun elements(parent: Element, tag: String): List<Element> {
        val nodes = parent.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
