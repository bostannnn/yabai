package com.example.unifiedreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The design system's quiet and character themes (README, Colour): a character theme's ground
 * is coloured and its own, and its label accent stays readable on the ground and on panels.
 */
class ThemeCharacterTest {

    private val pickable = ThemeMode.entries.filter { it.isAvailableInSettings && !it.usesDynamicColor }
    private val character = setOf(
        ThemeMode.Terminal95,
        ThemeMode.CrimsonCourt,
        ThemeMode.VhsNight,
        ThemeMode.Blueprint,
        ThemeMode.DetectiveFile,
    )

    @Test
    fun `exactly the five character themes have a character`() {
        pickable.forEach { mode ->
            val isCharacter = unifiedReaderThemeCharacter(mode) != UnifiedReaderQuietCharacter
            assertEquals("$mode", mode in character, isCharacter)
        }
    }

    @Test
    fun `a character theme's ground is coloured`() {
        character.forEach { mode ->
            val chroma = oklab(scheme(mode).background).let { (_, a, b) -> hypot(a, b) }
            assertTrue("$mode ground chroma $chroma is under $MinChroma", chroma >= MinChroma)
        }
    }

    @Test
    fun `a character theme's ground stands apart from every other theme's`() {
        character.forEach { mode ->
            pickable.filter { it != mode }.forEach { other ->
                val distance = distance(scheme(mode).background, scheme(other).background)
                assertTrue("$mode is $distance from $other, under $MinDistance", distance >= MinDistance)
            }
        }
    }

    @Test
    fun `a character theme's label accent reads on its ground and its panels`() {
        character.forEach { mode ->
            val label = unifiedReaderThemeCharacter(mode).labelColor
            assertTrue("$mode label", label.isSpecified)
            val scheme = scheme(mode)
            listOf(scheme.background, scheme.surfaceContainer).forEach { ground ->
                val ratio = contrast(label, ground)
                assertTrue("$mode label on $ground is $ratio:1", ratio >= 4.5)
            }
        }
    }

    private fun scheme(mode: ThemeMode) = staticUnifiedReaderColorScheme(mode, systemDarkTheme = mode.isDark)

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun distance(a: Color, b: Color): Double {
        val (l1, a1, b1) = oklab(a)
        val (l2, a2, b2) = oklab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun oklab(color: Color): Triple<Double, Double, Double> {
        fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Triple(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private companion object {
        const val MinChroma = 0.04
        const val MinDistance = 0.045
    }
}
