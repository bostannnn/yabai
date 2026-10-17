package com.example.unifiedreader.feature.reader.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colors the reader chrome uses by role. Every value comes from the theme; see [readerOverlayTokens]. */
@Immutable
data class ReaderOverlayColors(
    val container: Color,
    val elevatedContainer: Color,
    val selectedContainer: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val mutedText: Color,
    val track: Color,
    val progress: Color,
    val accent: Color,
    val fallbackPage: Color,
)

/**
 * Reader chrome shapes, on the app's three radii: bars, buttons, and selected rows at the
 * control radius; panes and the preview strip at the container radius; thumbnails at the
 * cover radius.
 */
@Immutable
data class ReaderOverlayShapes(
    val button: Shape,
    val controlBar: Shape,
    val sheet: Shape,
    val selectedRow: Shape,
    val previewStrip: Shape,
    val previewItem: Shape,
)

@Immutable
data class ReaderOverlaySpacing(
    val smallGap: Dp = 2.dp,
)

@Immutable
data class ReaderOverlayTokens(
    val colors: ReaderOverlayColors,
    val shapes: ReaderOverlayShapes,
    val spacing: ReaderOverlaySpacing = ReaderOverlaySpacing(),
)

@Composable
fun readerOverlayTokens(): ReaderOverlayTokens {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val container = if (isDark) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh
    val elevatedContainer = if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerHighest
    // MaterialTheme.shapes maps extraSmall/medium/large to the cover, control, and container radii.
    val shapes = MaterialTheme.shapes
    return ReaderOverlayTokens(
        colors = ReaderOverlayColors(
            container = container,
            elevatedContainer = elevatedContainer,
            selectedContainer = colorScheme.primaryContainer,
            primaryText = colorScheme.onSurface,
            secondaryText = colorScheme.onSurfaceVariant,
            tertiaryText = colorScheme.outline,
            mutedText = colorScheme.outline,
            track = colorScheme.outlineVariant,
            progress = colorScheme.onSurface,
            accent = colorScheme.primary,
            fallbackPage = colorScheme.background,
        ),
        shapes = ReaderOverlayShapes(
            button = shapes.medium,
            controlBar = shapes.medium,
            sheet = shapes.large,
            selectedRow = shapes.medium,
            previewStrip = shapes.large,
            previewItem = shapes.extraSmall,
        ),
    )
}
