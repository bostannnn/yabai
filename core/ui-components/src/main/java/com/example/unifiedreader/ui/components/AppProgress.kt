package com.example.unifiedreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.AppMotion
import kotlin.coroutines.coroutineContext
import kotlin.math.min

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

/**
 * A 2 dp ring of [diameter] around the element (an icon button, such as Downloads): a track, and
 * the accent filling clockwise from 12 o'clock to [progress]; while [progress] is null, a quarter
 * arc turns instead (it stands still when the system turns animations off). Only the draw phase
 * reads the motion, so the turning arc redraws the ring without recomposing.
 */
@Composable
fun Modifier.appProgressRing(
    progress: Float?,
    diameter: Dp = AppProgressRingDefaults.diameter,
): Modifier {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progressColor = MaterialTheme.colorScheme.primary
    val fraction by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = AppMotion.spatial(),
        label = "appProgressRing",
    )
    val indeterminate = progress == null
    val turn = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(indeterminate) {
        if (!indeterminate || coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameMillis { frameMillis ->
                turn.floatValue = (frameMillis % AppProgressRingDefaults.turnMillis).toFloat() / AppProgressRingDefaults.turnMillis
            }
        }
    }
    return drawBehind {
        val stroke = AppProgressRingDefaults.strokeWidth.toPx()
        val ring = min(diameter.toPx(), size.minDimension) - stroke
        val topLeft = Offset((size.width - ring) / 2f, (size.height - ring) / 2f)
        val ringSize = Size(ring, ring)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = ringSize, style = Stroke(stroke))
        val arcStroke = Stroke(stroke, cap = StrokeCap.Round)
        if (indeterminate) {
            drawArc(progressColor, -90f + 360f * turn.floatValue, 90f, false, topLeft, ringSize, style = arcStroke)
        } else if (fraction > 0f) {
            drawArc(progressColor, -90f, 360f * fraction, false, topLeft, ringSize, style = arcStroke)
        }
    }
}

object AppProgressRingDefaults {
    val diameter: Dp = 40.dp
    val strokeWidth: Dp = 2.dp
    const val turnMillis: Long = 1200L
}
