package com.example.unifiedreader.feature.reader.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance

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
 * Reader chrome shapes on the app's radii: buttons and controls at the control radius,
 * thumbnails at the cover radius. The bars' 28 dp shape is [ReaderChromeDefaults.barShape].
 */
@Immutable
data class ReaderOverlayShapes(
    val button: Shape,
    val controlBar: Shape,
    val previewItem: Shape,
)

@Immutable
data class ReaderOverlayTokens(
    val colors: ReaderOverlayColors,
    val shapes: ReaderOverlayShapes,
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
            previewItem = shapes.extraSmall,
        ),
    )
}
