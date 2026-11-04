package com.example.unifiedreader.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

// The journal's grid sizes its rows by asking each panel how tall it is at its width. These
// layouts split the width themselves and answer that question exactly, so a calendar is never
// asked about a narrower width than it gets (and drawn shorter than it is).

/**
 * Children side by side, [weights] of the width each ([gap] between), all as tall as the tallest;
 * a row of the journal's grid, or a calendar's week.
 */
@Composable
internal fun JournalSplitRow(
    weights: List<Int>,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val policy = remember(weights, gap) { SplitRowPolicy(weights, gap) }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

/** [count] children of equal width, [gap] apart. */
@Composable
internal fun JournalEqualRow(
    count: Int,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = JournalSplitRow(weights = remember(count) { List(count) { 1 } }, gap = gap, modifier = modifier, content = content)

/**
 * Panels one over another, [gap] apart. Given more height than they need (a taller neighbour in
 * the row), they share the rest, so the stack ends flush with its row.
 */
@Composable
internal fun JournalStack(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val policy = remember(gap) { StackPolicy(gap) }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

private class SplitRowPolicy(private val weights: List<Int>, private val gap: Dp) : MeasurePolicy {
    private fun IntrinsicMeasureScope.widths(total: Int, count: Int): List<Int> {
        val shares = weights.take(count).ifEmpty { return emptyList() }
        val free = (total - gap.roundToPx() * (shares.size - 1)).coerceAtLeast(0)
        val sum = shares.sum().coerceAtLeast(1)
        val widths = shares.map { share -> free * share / sum }.toMutableList()
        // What rounding leaves goes to the last child, so the row spans its width exactly.
        widths[widths.lastIndex] += free - widths.sum()
        return widths
    }

    private fun IntrinsicMeasureScope.rowHeight(measurables: List<IntrinsicMeasurable>, width: Int, max: Boolean): Int {
        val widths = widths(width, measurables.size)
        return measurables.zip(widths).maxOfOrNull { (child, childWidth) ->
            if (max) child.maxIntrinsicHeight(childWidth) else child.minIntrinsicHeight(childWidth)
        } ?: 0
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val width = constraints.maxWidth
        val widths = widths(width, measurables.size)
        val natural = rowHeight(measurables, width, max = false)
        val height = if (constraints.hasBoundedHeight) natural.coerceIn(constraints.minHeight, constraints.maxHeight) else natural.coerceAtLeast(constraints.minHeight)
        val placeables = measurables.zip(widths).map { (child, childWidth) -> child.measure(Constraints.fixed(childWidth, height)) }
        val gapPx = gap.roundToPx()
        return layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.place(x, 0)
                x += placeable.width + gapPx
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        rowHeight(measurables, width, max = false)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        rowHeight(measurables, width, max = true)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.minIntrinsicWidth(height) } + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.maxIntrinsicWidth(height) } + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)
}

private class StackPolicy(private val gap: Dp) : MeasurePolicy {
    private fun IntrinsicMeasureScope.heights(measurables: List<IntrinsicMeasurable>, width: Int): List<Int> =
        measurables.map { it.minIntrinsicHeight(width) }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val heights = heights(measurables, width)
        val natural = heights.sum() + gapPx * (measurables.size - 1).coerceAtLeast(0)
        val height = if (constraints.hasBoundedHeight) natural.coerceIn(constraints.minHeight, constraints.maxHeight) else natural.coerceAtLeast(constraints.minHeight)
        val extra = (height - natural).coerceAtLeast(0)
        val share = if (measurables.isEmpty()) 0 else extra / measurables.size
        val placeables = measurables.mapIndexed { index, child ->
            val bonus = if (index == measurables.lastIndex) extra - share * (measurables.size - 1) else share
            child.measure(Constraints.fixed(width, heights[index] + bonus))
        }
        return layout(width, height) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height + gapPx
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        heights(measurables, width).sum() + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.sumOf { it.maxIntrinsicHeight(width) } + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0
}
