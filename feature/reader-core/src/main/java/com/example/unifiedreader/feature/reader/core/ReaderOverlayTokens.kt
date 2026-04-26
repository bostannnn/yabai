package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ReaderOverlayColors(
    val container: Color = Color(0xF2111418),
    val elevatedContainer: Color = Color(0xFA171A1F),
    val selectedContainer: Color = Color(0x29F5F7FA),
    val selectedBorder: Color = Color(0x24F5F7FA),
    val divider: Color = Color(0x33F5F7FA),
    val primaryText: Color = Color(0xFFF5F7FA),
    val secondaryText: Color = Color(0xFFD6E0E8),
    val tertiaryText: Color = Color(0xFFB7C4CE),
    val mutedText: Color = Color(0xFF8B9AAA),
    val track: Color = Color(0x55F5F7FA),
    val progress: Color = Color(0xFFE9EEF3),
    val accent: Color = Color(0xFF9DBBFF),
    val fallbackPage: Color = Color(0xFF05090C),
)

@Immutable
data class ReaderOverlayShapes(
    val button: RoundedCornerShape = RoundedCornerShape(8.dp),
    val controlBar: RoundedCornerShape = RoundedCornerShape(8.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(8.dp),
    val selectedRow: RoundedCornerShape = RoundedCornerShape(8.dp),
    val previewStrip: RoundedCornerShape = RoundedCornerShape(8.dp),
    val previewItem: RoundedCornerShape = RoundedCornerShape(4.dp),
)

@Immutable
data class ReaderOverlaySpacing(
    val smallGap: Dp = 2.dp,
)

@Immutable
data class ReaderOverlayTokens(
    val colors: ReaderOverlayColors = ReaderOverlayColors(),
    val shapes: ReaderOverlayShapes = ReaderOverlayShapes(),
    val spacing: ReaderOverlaySpacing = ReaderOverlaySpacing(),
)

@Composable
fun readerOverlayTokens(): ReaderOverlayTokens {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val container = if (isDark) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh
    val elevatedContainer = if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerHighest
    val selectedContainer = colorScheme.primaryContainer
    val selectedBorder = colorScheme.primary.copy(alpha = if (isDark) 0.7f else 0.62f)
    val primaryText = colorScheme.onSurface
    val secondaryText = colorScheme.onSurfaceVariant
    return ReaderOverlayTokens(
        colors = ReaderOverlayColors(
            container = container,
            elevatedContainer = elevatedContainer,
            selectedContainer = selectedContainer,
            selectedBorder = selectedBorder,
            divider = colorScheme.outlineVariant,
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = secondaryText.copy(alpha = 0.78f),
            mutedText = secondaryText.copy(alpha = 0.62f),
            track = colorScheme.outlineVariant,
            progress = primaryText,
            accent = colorScheme.primary,
            fallbackPage = colorScheme.background,
        ),
    )
}
