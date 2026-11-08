package com.example.unifiedreader.feature.reader.core

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Where a tap on the page lands: a side zone that turns the page, or the middle, which opens the menu. */
enum class ReaderTapSide {
    Left,
    Middle,
    Right,
}

/**
 * The page-turn zones of every reader. Each side zone is a quarter of the width but never wider than
 * [ReaderTapZoneMaxWidth] ([maxZoneWidth] in pixels), the inch from the edge where a thumb holding a
 * tablet rests. Phones keep their quarter; on a tablet the middle of a page, and both pages of a
 * landscape spread, open the menu instead of turning.
 */
fun readerTapSide(
    tapX: Float,
    width: Float,
    maxZoneWidth: Float,
): ReaderTapSide {
    if (width <= 0f) return ReaderTapSide.Middle
    val zoneWidth = readerTapZoneWidth(width, maxZoneWidth)
    return when {
        tapX <= zoneWidth -> ReaderTapSide.Left
        tapX >= width - zoneWidth -> ReaderTapSide.Right
        else -> ReaderTapSide.Middle
    }
}

/** [readerTapSide] with the zone's cap in this density's pixels. */
fun Density.readerTapSide(
    tapX: Float,
    width: Float,
): ReaderTapSide = readerTapSide(tapX, width, ReaderTapZoneMaxWidth.toPx())

/** The width of one side zone on a page [width] wide: a quarter of it, at most [maxZoneWidth]. */
fun readerTapZoneWidth(
    width: Float,
    maxZoneWidth: Float,
): Float = min(width * ReaderTapZoneFraction, maxZoneWidth).coerceAtLeast(0f)

/** One inch: as far from the edge as a page-turn zone reaches. */
val ReaderTapZoneMaxWidth: Dp = 160.dp

private const val ReaderTapZoneFraction = 0.25f
