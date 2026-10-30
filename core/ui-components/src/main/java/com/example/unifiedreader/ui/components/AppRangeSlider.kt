package com.example.unifiedreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A range on one track: [AppSlider]'s 18 dp pill, filled with the accent between two slim
 * 4 x 28 dp handles. Material still handles the drag, the keys and the semantics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRangeSlider(
    values: ClosedFloatingPointRange<Float>,
    onValuesChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else AppSliderDefaults.disabledAlpha
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val handleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val handle: @Composable (RangeSliderState) -> Unit = {
        Box(
            modifier = Modifier
                .size(width = AppSliderDefaults.handleWidth, height = AppSliderDefaults.handleHeight)
                .background(handleColor, RoundedCornerShape(AppSliderDefaults.handleCornerRadius)),
        )
    }
    RangeSlider(
        value = values,
        onValueChange = onValuesChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        startThumb = handle,
        endThumb = handle,
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            fun fractionOf(value: Float): Float =
                if (span > 0f) ((value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            val startFraction = fractionOf(state.activeRangeStart)
            val endFraction = fractionOf(state.activeRangeEnd)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSliderDefaults.trackHeight),
            ) {
                val pill = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f)))
                }
                drawPath(pill, trackColor)
                clipPath(pill) {
                    val left = size.width * if (rtl) 1f - endFraction else startFraction
                    val right = size.width * if (rtl) 1f - startFraction else endFraction
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height),
                    )
                }
            }
        },
        steps = steps,
    )
}

/**
 * Bars over an [AppRangeSlider], one per step of its track: each as tall as the square root of
 * its count against the largest, so a few long books still show beside many short ones. The bars
 * in [highlighted] take the accent, the rest the hairline colour; a count of 0 draws nothing. Inset
 * like the slider's track, so the bars' edges are its steps.
 */
@Composable
fun AppHistogram(
    counts: List<Int>,
    highlighted: IntRange,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AppHistogramDefaults.height)
            .padding(horizontal = AppSliderDefaults.handleWidth / 2),
    ) {
        if (counts.isEmpty()) return@Canvas
        val tallest = sqrt(counts.max().toFloat())
        if (tallest <= 0f) return@Canvas
        val gap = AppHistogramDefaults.gap.toPx()
        val barWidth = (size.width - gap * (counts.size - 1)) / counts.size
        val corner = CornerRadius(AppHistogramDefaults.cornerRadius.toPx())
        counts.forEachIndexed { index, count ->
            if (count <= 0) return@forEachIndexed
            val height = maxOf(AppHistogramDefaults.minBarHeight.toPx(), size.height * sqrt(count.toFloat()) / tallest)
            val slot = if (rtl) counts.lastIndex - index else index
            val left = slot * (barWidth + gap)
            val bar = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = size.height - height,
                        right = left + barWidth,
                        bottom = size.height,
                        topLeftCornerRadius = corner,
                        topRightCornerRadius = corner,
                    ),
                )
            }
            drawPath(bar, if (index in highlighted) highlightColor else barColor)
        }
    }
}

/**
 * Labels under a slider, each at its fraction of the track (0 the start, 1 the end) and inset like
 * the track; the end ones keep inside the edges.
 */
@Composable
fun AppSliderScale(
    marks: List<Pair<Float, String>>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(
        content = { marks.forEach { (_, label) -> Text(text = label, style = style, color = color, maxLines = 1) } },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSliderDefaults.handleWidth / 2),
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable -> measurable.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { index, placeable ->
                val fraction = marks[index].first.let { if (rtl) 1f - it else it }
                val x = (fraction * width - placeable.width / 2f).roundToInt()
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

object AppHistogramDefaults {
    val height: Dp = 56.dp
    val gap: Dp = 2.dp
    val cornerRadius: Dp = 3.dp
    val minBarHeight: Dp = 2.dp
}
