package com.example.unifiedreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCoverColorsTest {
    private val dusk = AppCoverSwatches(ambient = 0xFF2B2F4E.toInt(), ambientSecond = 0xFF5D3C50.toInt(), accent = 0xFFE07A62.toInt())
    private val themePrimary = Color(0xFFB7C7D9)
    private val themeOnPrimary = Color(0xFF1B2733)

    @Test
    fun `hsl survives a round trip`() {
        listOf(0xFF2B2F4E, 0xFFF0AA9E, 0xFF000000, 0xFFFFFFFF, 0xFF3A8F2C).map(Long::toInt).forEach { argb ->
            val back = Hsl.of(argb).toArgb()
            listOf(16, 8, 0).forEach { shift ->
                val channelDelta = ((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF)
                assertTrue("channel off by $channelDelta for ${Integer.toHexString(argb)}", kotlin.math.abs(channelDelta) <= 1)
            }
        }
    }

    @Test
    fun `dark themes get deep tints and a light accent with dark text`() {
        val colors = appCoverColors(dusk, dark = true, fallbackAccent = themePrimary, fallbackOnAccent = themeOnPrimary)

        assertEquals(0.25f, Hsl.of(colors.tintStart.toArgb()).l, 0.01f)
        assertEquals(0.30f, Hsl.of(colors.tintEnd.toArgb()).l, 0.01f)
        assertEquals(0.75f, Hsl.of(colors.accent.toArgb()).l, 0.01f)
        assertTrue(contrastRatio(colors.accent.toArgb(), colors.onAccent.toArgb()) >= 4.5)
        assertTrue(contrastRatio(colors.tintEnd.toArgb(), colors.content.toArgb()) >= 4.5)
        // The accent keeps the cover's hue.
        assertEquals(Hsl.of(0xFFE07A62.toInt()).h, Hsl.of(colors.accent.toArgb()).h, 3f)
    }

    @Test
    fun `light themes get pale tints and a dark accent with white text`() {
        val colors = appCoverColors(dusk, dark = false, fallbackAccent = themePrimary, fallbackOnAccent = themeOnPrimary)

        assertEquals(0.90f, Hsl.of(colors.tintStart.toArgb()).l, 0.01f)
        assertEquals(0.35f, Hsl.of(colors.accent.toArgb()).l, 0.01f)
        assertEquals(Color.White, colors.onAccent)
        assertTrue(contrastRatio(colors.tintStart.toArgb(), colors.content.toArgb()) >= 4.5)
    }

    @Test
    fun `a cover without an accent keeps the theme's`() {
        val colors = appCoverColors(dusk.copy(accent = null), dark = true, fallbackAccent = themePrimary, fallbackOnAccent = themeOnPrimary)

        assertEquals(themePrimary.toArgb(), colors.accent.toArgb())
        assertEquals(themeOnPrimary.toArgb(), colors.onAccent.toArgb())
    }

    @Test
    fun `an accent whose text would be too faint keeps the theme's`() {
        // Pure yellow lightened to L 0.35 on a light theme still sits too close to white.
        val colors = appCoverColors(
            dusk.copy(accent = 0xFFFFFF00.toInt()),
            dark = false,
            fallbackAccent = themePrimary,
            fallbackOnAccent = themeOnPrimary,
        )

        assertTrue(contrastRatio(colors.accent.toArgb(), colors.onAccent.toArgb()) >= 4.5)
    }

    @Test
    fun `contrast ratio follows WCAG`() {
        assertEquals(21.0, contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, contrastRatio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
    }
}
