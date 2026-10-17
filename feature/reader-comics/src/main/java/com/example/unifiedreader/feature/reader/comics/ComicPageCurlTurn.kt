package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

internal enum class ComicPageCurlFollow {
    /** The leaf starts flat and its outer edge follows the finger (forward turns, spread back turns). */
    Edge,

    /** The previous page starts turned over the spine and its fold follows the finger back. */
    Fold,
}

/** One page turn: which leaf turns, where it is hinged, and how the finger drives it. */
internal data class ComicPageCurlTurn(
    val target: ComicPageCurlTarget,
    val sheet: ComicPageCurlSheet,
    val paperBounds: ComicRasterRect,
    val bookBounds: ComicRasterRect,
    val follow: ComicPageCurlFollow,
    /** On spreads the back of the turning leaf is the target spread's facing page. */
    val backIsFacingPage: Boolean,
) {
    /** On single pages a backward turn brings the previous page itself back over the current one. */
    val sheetIsAdjacentPage: Boolean
        get() = follow == ComicPageCurlFollow.Fold

    private val maxRadius: Float
        get() = sheet.width * DefaultCurlMaxRadiusFraction

    fun grabPoint(downY: Float): Offset = sheet.grabPoint(downY)

    fun restingEdge(grab: Offset): Offset = when (follow) {
        ComicPageCurlFollow.Edge -> grab
        ComicPageCurlFollow.Fold -> sheet.turnedPoint(grab)
    }

    fun committedEdge(grab: Offset): Offset = when (follow) {
        ComicPageCurlFollow.Edge -> sheet.turnedPoint(grab)
        ComicPageCurlFollow.Fold -> grab
    }

    fun edgeForFinger(
        grab: Offset,
        down: Offset,
        finger: Offset,
    ): Offset {
        val dragX = finger.x - down.x
        val dragY = finger.y - down.y
        val edge = when (follow) {
            ComicPageCurlFollow.Edge -> Offset(
                x = grab.x + dragX,
                y = grab.y + dragY - cornerLiftSlope(grab) * abs(dragX),
            )

            ComicPageCurlFollow.Fold -> {
                // Keep the roll's leading edge under the finger: the drawn roll sits slightly behind
                // the fold's bisector, so lead it in over the first radius of travel.
                val lead = RollLeadFraction * maxRadius * (abs(dragX) / maxRadius).coerceAtMost(1f)
                val fold = Offset(
                    x = sheet.hingeX + dragX + sheet.outward * lead,
                    y = grab.y + dragY,
                )
                Offset(2f * fold.x - grab.x, 2f * fold.y - grab.y)
            }
        }
        return clampComicPageCurlEdge(sheet = sheet, grab = grab, edge = edge)
    }

    /** Edge position for an automatic (tap) turn at [fraction] of the way from rest to committed. */
    fun automaticTurnEdge(
        grab: Offset,
        fraction: Float,
    ): Offset {
        val start = restingEdge(grab)
        val end = committedEdge(grab)
        val t = fraction.coerceIn(0f, 1f)
        val lift = cornerLiftSlope(grab) * sheet.width * sin(PI.toFloat() * t)
        return clampComicPageCurlEdge(
            sheet = sheet,
            grab = grab,
            edge = Offset(
                x = start.x + (end.x - start.x) * t,
                y = start.y + (end.y - start.y) * t - lift,
            ),
        )
    }

    /** 0 at rest, 1 once the turn has fully reached its target page. */
    fun commitProgress(
        grab: Offset,
        edge: Offset,
    ): Float {
        val turned = sheet.turnFraction(grab = grab, edge = edge)
        return when (follow) {
            ComicPageCurlFollow.Edge -> turned
            ComicPageCurlFollow.Fold -> 1f - turned
        }
    }

    /** Finger velocity toward completing the turn, in page widths per second. */
    fun commitVelocity(
        horizontalVelocity: Float,
    ): Float {
        if (sheet.width <= 0f) return 0f
        val towardCommit = when (follow) {
            ComicPageCurlFollow.Edge -> -sheet.outward
            ComicPageCurlFollow.Fold -> sheet.outward
        }
        return horizontalVelocity * towardCommit / sheet.width
    }

    fun settleDurationMillis(
        from: Offset,
        to: Offset,
    ): Int {
        if (sheet.width <= 0f) return MinimumSettleDurationMillis
        val remaining = (abs(to.x - from.x) / (2f * sheet.width)).coerceIn(0f, 1f)
        return MinimumSettleDurationMillis + (remaining * SettleDurationRangeMillis).toInt()
    }

    /** Off-centre grabs lead with their corner, like a real page picked up low or high. */
    private fun cornerLiftSlope(
        grab: Offset,
    ): Float {
        val halfHeight = (sheet.bottom - sheet.top) / 2f
        if (halfHeight <= 0f) return 0f
        val fromCenter = (grab.y - (sheet.top + halfHeight)) / halfHeight
        return CornerLiftSlope * fromCenter.coerceIn(-1f, 1f)
    }
}

internal const val ComicPageCurlTapGrabFraction = 0.82f
internal const val ComicPageCurlTapTurnDurationMillis = 380

private const val CornerLiftSlope = 0.22f
private const val RollLeadFraction = (PI / 2 - 1).toFloat()
private const val MinimumSettleDurationMillis = 140
private const val SettleDurationRangeMillis = 220
