package com.example.unifiedreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A bar row whose middle (the library's search field) is centred on the bar, so it shares one
 * centre with the page under it, whatever sits at either end: the menu on one side and two
 * buttons on the other, or nothing at the start beside the rail. The middle is [centreMaxWidth]
 * at most; where the bar is too narrow to centre it, it takes the room between the ends,
 * [AppTopBarCentredRowDefaults.gap] from each (design system, AppTopBar).
 */
@Composable
fun AppTopBarCentredRow(
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    centreMaxWidth: Dp = AppTopBarCentredRowDefaults.centreMaxWidth,
    centre: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(leading, centre, trailing),
        modifier = modifier,
    ) { (leadingMeasurables, centreMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leadingPlaceables = leadingMeasurables.map { it.measure(loose) }
        val trailingPlaceables = trailingMeasurables.map { it.measure(loose) }
        val barWidth = constraints.maxWidth
        val placement = appTopBarCentredPlacement(
            barWidth = barWidth,
            leadingWidth = leadingPlaceables.sumOf { it.width },
            trailingWidth = trailingPlaceables.sumOf { it.width },
            maxCentreWidth = centreMaxWidth.roundToPx(),
            gap = AppTopBarCentredRowDefaults.gap.roundToPx(),
        )
        val centrePlaceables = centreMeasurables.map {
            it.measure(Constraints.fixedWidth(placement.width).copy(minHeight = 0, maxHeight = constraints.maxHeight))
        }
        val height = (leadingPlaceables + trailingPlaceables + centrePlaceables).maxOfOrNull { it.height }
            ?.coerceIn(constraints.minHeight, constraints.maxHeight)
            ?: constraints.minHeight
        layout(barWidth, height) {
            var x = 0
            leadingPlaceables.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width
            }
            centrePlaceables.forEach { placeable ->
                placeable.placeRelative(placement.x, (height - placeable.height) / 2)
            }
            var end = barWidth
            trailingPlaceables.asReversed().forEach { placeable ->
                end -= placeable.width
                placeable.placeRelative(end, (height - placeable.height) / 2)
            }
        }
    }
}

/** Where the middle of an [AppTopBarCentredRow] goes, in px from the bar's start. */
data class AppTopBarCentredPlacement(val x: Int, val width: Int)

/**
 * Centred on the bar, [maxCentreWidth] at most, but never closer than [gap] to either end's
 * buttons: when centring would overlap them, it slides toward the roomier side, and when there
 * isn't room for the whole width, it fills what there is.
 */
fun appTopBarCentredPlacement(
    barWidth: Int,
    leadingWidth: Int,
    trailingWidth: Int,
    maxCentreWidth: Int,
    gap: Int,
): AppTopBarCentredPlacement {
    val start = if (leadingWidth > 0) leadingWidth + gap else 0
    val end = barWidth - if (trailingWidth > 0) trailingWidth + gap else 0
    val width = minOf(maxCentreWidth, (end - start).coerceAtLeast(0))
    val centred = (barWidth - width) / 2
    val x = centred.coerceIn(start, (end - width).coerceAtLeast(start))
    return AppTopBarCentredPlacement(x = x, width = width)
}

object AppTopBarCentredRowDefaults {
    /** Past this the library's field stops growing: on a wide window it still reads as one field. */
    val centreMaxWidth: Dp = 640.dp

    /** Between the middle and the buttons at either end. */
    val gap: Dp = 4.dp
}
