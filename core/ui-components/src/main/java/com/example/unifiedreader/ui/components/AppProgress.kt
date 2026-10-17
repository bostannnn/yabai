package com.example.unifiedreader.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Determinate linear progress. */
@Composable
fun AppLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** Indeterminate linear progress. */
@Composable
fun AppLinearProgress(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** Indeterminate circular progress. */
@Composable
fun AppCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
    )
}

/**
 * Draws a thin reading-progress fill behind the content: a track with a solid fill from the
 * start edge to [fraction]. Used for cover and row progress bars; clip the shape before it.
 */
fun Modifier.appProgressFill(
    fraction: Float,
    progressColor: Color,
    trackColor: Color,
): Modifier = drawBehind {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    drawRect(trackColor)
    drawRect(progressColor, size = Size(size.width * clampedFraction, size.height))
}
