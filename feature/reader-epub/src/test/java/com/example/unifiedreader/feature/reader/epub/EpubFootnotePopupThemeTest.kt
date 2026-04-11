package com.example.unifiedreader.feature.reader.epub

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubFootnotePopupThemeTest {
    @Test
    fun `popup colors are derived from light material scheme`() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF0057D9),
            surfaceContainerHigh = Color(0xFFF7F2FA),
            outlineVariant = Color(0xFFD0C9D6),
            onSurface = Color(0xFF201A23),
            onSurfaceVariant = Color(0xFF4D4454),
            scrim = Color(0xFF1A1420),
        )

        val colors = epubFootnotePopupColors(colorScheme)

        assertEquals(colorScheme.surfaceContainerHigh, colors.container)
        assertEquals(colorScheme.outlineVariant.copy(alpha = 0.92f), colors.border)
        assertEquals(colorScheme.onSurfaceVariant, colors.title)
        assertEquals(colorScheme.onSurface, colors.body)
        assertEquals(colorScheme.primary, colors.action)
        assertEquals(colorScheme.scrim.copy(alpha = 0.32f), colors.scrim)
    }

    @Test
    fun `popup colors are derived from dark material scheme`() {
        val colorScheme = darkColorScheme(
            primary = Color(0xFF9EC2FF),
            surfaceContainerHigh = Color(0xFF221F26),
            outlineVariant = Color(0xFF4E4657),
            onSurface = Color(0xFFE9E0EC),
            onSurfaceVariant = Color(0xFFD1C3D8),
            scrim = Color(0xFF000000),
        )

        val colors = epubFootnotePopupColors(colorScheme)

        assertEquals(colorScheme.surfaceContainerHigh, colors.container)
        assertEquals(colorScheme.outlineVariant.copy(alpha = 0.92f), colors.border)
        assertEquals(colorScheme.onSurfaceVariant, colors.title)
        assertEquals(colorScheme.onSurface, colors.body)
        assertEquals(colorScheme.primary, colors.action)
        assertEquals(colorScheme.scrim.copy(alpha = 0.32f), colors.scrim)
    }
}
