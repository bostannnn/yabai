package com.example.unifiedreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The floating glass top bar. It overlays the screen's content, which should keep
 * [AppTopBarDefaults.contentInset] of top padding so nothing starts hidden under it. With
 * [surfaceVisible] false the glass fades away and only the content (icons) stays, for a screen
 * whose own large title sits under the bar until it scrolls away. A [containerColor] replaces
 * the glass with that solid color (the library's selection bar).
 *
 * While the bar is visible the status bar and the gap above the bar fade from the screen's
 * background, so content scrolling up under the bar doesn't show as a strip above it.
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    surfaceVisible: Boolean = true,
    containerColor: Color? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val shape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.cardCornerRadius)
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (surfaceVisible) 1f else 0f,
        animationSpec = AppMotion.effects(),
        label = "appTopBarSurface",
    )
    val background = MaterialTheme.colorScheme.background
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (surfaceAlpha > 0f) {
                    val gapBottom = AppTopBarDefaults.topMargin.toPx()
                    val top = -statusBarTop
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to background,
                            // Solid through the status bar and half the gap, then fading out.
                            ((statusBarTop + gapBottom / 2f) / (statusBarTop + gapBottom)).coerceIn(0f, 1f) to background,
                            1f to background.copy(alpha = 0f),
                            startY = top,
                            endY = gapBottom,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, gapBottom - top),
                        alpha = surfaceAlpha,
                    )
                }
            }
            .padding(
                start = AppTopBarDefaults.sideMargin,
                end = AppTopBarDefaults.sideMargin,
                top = AppTopBarDefaults.topMargin,
                bottom = AppTopBarDefaults.bottomMargin,
            ),
    ) {
        if (surfaceAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = surfaceAlpha }
                    .shadow(
                        elevation = AppTopBarDefaults.shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    )
                    .then(
                        if (containerColor != null) Modifier.background(containerColor, shape) else Modifier.appGlass(shape = shape),
                    ),
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppTopBarDefaults.height)
                .padding(horizontal = AppTopBarDefaults.contentPadding),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

object AppTopBarDefaults {
    val height: Dp = 56.dp
    val sideMargin: Dp = 16.dp
    val topMargin: Dp = 12.dp
    val bottomMargin: Dp = 8.dp
    val contentPadding: Dp = 8.dp
    val shadowElevation: Dp = 6.dp

    /** Top padding a screen's scrolling content needs so its first row clears the bar. */
    val contentInset: Dp = topMargin + height + bottomMargin
}
