package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The one container for grouped content: cards, panels, sheet bodies, dialog bodies.
 * Feature code uses this instead of Material's `Card`, `OutlinedCard`, or `Surface`.
 */
@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    shape: Shape = AppPanelDefaults.shape,
    color: Color = AppPanelDefaults.containerColor,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Column(content = content)
    }
}

/** A clickable [AppPanel]. */
@Composable
fun AppPanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppPanelDefaults.shape,
    color: Color = AppPanelDefaults.containerColor,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        Column(content = content)
    }
}

object AppPanelDefaults {
    /** The theme's card corner radius. */
    val shape: Shape
        @Composable get() = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.cardCornerRadius)

    /** The raised surface every grouped panel sits on (shelves, settings, journal). */
    val containerColor: Color
        @Composable get() = UnifiedReaderThemeTokens.colors.surfaceRaised

    /** Container color of a Material outlined card. */
    val outlinedContainerColor: Color
        @Composable get() = CardDefaults.outlinedCardColors().containerColor

    /** Border of a Material outlined card. */
    val outlinedBorder: BorderStroke
        @Composable get() = CardDefaults.outlinedCardBorder()
}

/** A full-screen background surface for a route or a screen-sized sheet. */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(color),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        content = content,
    )
}

/** A small status surface: counts, read state, rating, connectivity. */
@Composable
fun AppBadge(
    modifier: Modifier = Modifier,
    shape: Shape = AppBadgeDefaults.shape,
    color: Color = AppBadgeDefaults.containerColor,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

object AppBadgeDefaults {
    val shape: Shape
        @Composable get() = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.chipCornerRadius)

    val containerColor: Color
        @Composable get() = UnifiedReaderThemeTokens.colors.surfaceSelected
}
