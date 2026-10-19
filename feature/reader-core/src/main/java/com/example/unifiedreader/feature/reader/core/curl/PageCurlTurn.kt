package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class PageCurlFollow {
    /** The leaf starts flat and its outer edge follows the finger (forward turns, spread back turns). */
    Edge,

    /** The previous page starts turned over the spine and its roll follows the finger back. */
    Fold,
}

/** One page turn: which leaf turns, where it is hinged, and how the finger drives it. */
data class PageCurlTurn(
    val target: PageCurlTarget,
    val sheet: PageCurlSheet,
    val paperBounds: IntRect,
    val bookBounds: IntRect,
    val follow: PageCurlFollow,
    /** On spreads the back of the turning leaf is the target spread's facing page. */
    val backIsFacingPage: Boolean,
) {
    /** On single pages a backward turn brings the previous page itself back over the current one. */
    val sheetIsAdjacentPage: Boolean
        get() = follow == PageCurlFollow.Fold

    private val maxRadius: Float
        get() = sheet.width * DefaultPageCurlMaxRadiusFraction

    /**
     * The point of the leaf a finger touching at [touchY] picks up. Edge turns take it nearer the
     * closest corner, so an off-centre grab leads with that corner like a real page.
     */
    fun grabPoint(touchY: Float): Offset = when (follow) {
        PageCurlFollow.Edge -> sheet.grabPoint(cornerBiasedY(touchY))
        PageCurlFollow.Fold -> sheet.grabPoint(touchY)
    }

    fun restingEdge(grab: Offset): Offset = when (follow) {
        PageCurlFollow.Edge -> grab
        PageCurlFollow.Fold -> sheet.turnedPoint(grab)
    }

    fun committedEdge(grab: Offset): Offset = when (follow) {
        PageCurlFollow.Edge -> sheet.turnedPoint(grab)
        PageCurlFollow.Fold -> grab
    }

    /**
     * Edge position for a finger at [finger] in a turn that started with the finger at [start]. The
     * page starts at rest and catches up with the finger: the grabbed edge (or, going back over the
     * spine, the roll's leading edge) moves faster than the finger at first and sits under it once
     * the finger has travelled as far as it was from that edge.
     */
    fun edgeForFinger(
        grab: Offset,
        start: Offset,
        finger: Offset,
    ): Offset {
        val travel = ((finger.x - start.x) * towardCommit).coerceAtLeast(0f)
        val edge = when (follow) {
            PageCurlFollow.Edge -> {
                val lead = grab - start
                finger + lead * catchUpRemaining(travel = travel, distance = lead.getDistance())
            }

            PageCurlFollow.Fold -> {
                val lead = sheet.hingeX - start.x
                val rollEdgeX = finger.x + lead * catchUpRemaining(travel = travel, distance = abs(lead))
                val foldFromSpine = foldForRollEdge((rollEdgeX - sheet.hingeX) * sheet.outward)
                val fold = Offset(
                    x = sheet.hingeX + sheet.outward * foldFromSpine,
                    y = grab.y + finger.y - start.y,
                )
                Offset(2f * fold.x - grab.x, 2f * fold.y - grab.y)
            }
        }
        return clampPageCurlEdge(sheet = sheet, grab = grab, edge = edge)
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
        return clampPageCurlEdge(
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
            PageCurlFollow.Edge -> turned
            PageCurlFollow.Fold -> 1f - turned
        }
    }

    /** Finger velocity toward completing the turn, in page widths per second. */
    fun commitVelocity(
        horizontalVelocity: Float,
    ): Float {
        if (sheet.width <= 0f) return 0f
        return horizontalVelocity * towardCommit / sheet.width
    }

    /** +1 or -1: the horizontal direction the finger moves to complete the turn. */
    private val towardCommit: Float
        get() = when (follow) {
            PageCurlFollow.Edge -> -sheet.outward
            PageCurlFollow.Fold -> sheet.outward
        }

    fun settleDurationMillis(
        from: Offset,
        to: Offset,
    ): Int {
        if (sheet.width <= 0f) return MinimumSettleDurationMillis
        val remaining = (abs(to.x - from.x) / (2f * sheet.width)).coerceIn(0f, 1f)
        return MinimumSettleDurationMillis + (remaining * SettleDurationRangeMillis).toInt()
    }

    /**
     * Where the fold sits, measured from the spine, when the roll's leading edge is [rollEdge] from
     * it. The roll (see [calculatePageCurlAxis]) reaches `(pi / 2 - 1) * radius` short of the
     * fold, and its radius grows from zero at the spine.
     */
    private fun foldForRollEdge(
        rollEdge: Float,
    ): Float {
        val edge = rollEdge.coerceAtLeast(0f)
        val fold = if (edge < maxRadius) {
            edge * HalfPi
        } else {
            edge + (HalfPi - 1f) * maxRadius
        }
        return fold.coerceAtMost(sheet.width)
    }

    private fun cornerBiasedY(
        touchY: Float,
    ): Float {
        val halfHeight = (sheet.bottom - sheet.top) / 2f
        if (halfHeight <= 0f) return touchY
        val center = sheet.top + halfHeight
        val fromCenter = ((touchY - center) / halfHeight).coerceIn(-1f, 1f)
        return center + halfHeight * sign(fromCenter) * abs(fromCenter).pow(CornerBiasExponent)
    }

    /** Off-centre automatic turns lead with their corner, like a real page picked up low or high. */
    private fun cornerLiftSlope(
        grab: Offset,
    ): Float {
        val halfHeight = (sheet.bottom - sheet.top) / 2f
        if (halfHeight <= 0f) return 0f
        val fromCenter = (grab.y - (sheet.top + halfHeight)) / halfHeight
        return CornerLiftSlope * fromCenter.coerceIn(-1f, 1f)
    }
}

/**
 * A turn of the leaf lying flat on [paperBounds], hinged on its left edge when [outward] is positive
 * (the leaf turns over to the left) and on its right edge otherwise; it is drawn inside [bookBounds].
 * Null when either rect is empty.
 */
fun buildPageCurlTurn(
    target: PageCurlTarget,
    paperBounds: IntRect,
    bookBounds: IntRect,
    outward: Float,
    follow: PageCurlFollow,
    backIsFacingPage: Boolean,
): PageCurlTurn? {
    if (paperBounds.width <= 0 || paperBounds.height <= 0) return null
    if (bookBounds.width <= 0 || bookBounds.height <= 0) return null
    return PageCurlTurn(
        target = target,
        sheet = PageCurlSheet(
            hingeX = if (outward > 0f) paperBounds.left.toFloat() else paperBounds.right.toFloat(),
            top = paperBounds.top.toFloat(),
            bottom = paperBounds.bottom.toFloat(),
            width = paperBounds.width.toFloat(),
            outward = outward,
        ),
        paperBounds = paperBounds,
        bookBounds = bookBounds,
        follow = follow,
        backIsFacingPage = backIsFacingPage,
    )
}

/**
 * Whether a released turn completes: a flick toward the target (or away from it) decides, otherwise
 * how far the leaf has turned. [progress] and [velocity] come from [PageCurlTurn.commitProgress] and
 * [PageCurlTurn.commitVelocity].
 */
fun shouldCommitPageCurl(
    progress: Float,
    velocity: Float = 0f,
    progressThreshold: Float = PageCurlCommitProgressThreshold,
    velocityThreshold: Float = PageCurlCommitVelocityThreshold,
): Boolean = when {
    velocity > velocityThreshold -> true
    velocity < -velocityThreshold -> false
    else -> progress > progressThreshold
}

const val PageCurlTapGrabFraction = 0.82f
const val PageCurlTapTurnDurationMillis = 380

/** Share of the start-to-edge offset still left after [travel] of a [distance]-long catch-up. */
private fun catchUpRemaining(
    travel: Float,
    distance: Float,
): Float {
    if (distance <= 0f) return 0f
    val left = 1f - (travel / distance).coerceIn(0f, 1f)
    return left * left
}

private const val CornerLiftSlope = 0.22f
private const val PageCurlCommitProgressThreshold = 0.25f
private const val PageCurlCommitVelocityThreshold = 1.0f
private const val CornerBiasExponent = 0.7f
private const val HalfPi = (PI / 2).toFloat()
private const val MinimumSettleDurationMillis = 140
private const val SettleDurationRangeMillis = 220
