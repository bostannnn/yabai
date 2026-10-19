package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val DefaultPageCurlMaxRadiusFraction = 0.12f

enum class PageCurlTarget {
    Previous,
    Next,
}

/**
 * The fold the shader draws: points with `dot(p - origin, direction) > 0` have lifted off the page and
 * wrap around a cylinder of [radius] whose base sits on that line.
 */
data class PageCurlAxis(
    val originX: Float,
    val originY: Float,
    val directionX: Float,
    val directionY: Float,
    val radius: Float,
)

/**
 * A page that turns like a book leaf: it is attached along the vertical spine at [hingeX] and, while
 * flat, lies between [hingeX] and `hingeX + outward * width`.
 */
data class PageCurlSheet(
    val hingeX: Float,
    val top: Float,
    val bottom: Float,
    val width: Float,
    val outward: Float,
) {
    /** The point on the outer edge that the reader picks up, at the height they touched. */
    fun grabPoint(y: Float): Offset = Offset(hingeX + outward * width, y.coerceIn(top, bottom))

    /** Where [grab] lies once the page is turned all the way over the spine. */
    fun turnedPoint(grab: Offset): Offset = Offset(2f * hingeX - grab.x, grab.y)

    /** 0 while the page is flat, 1 once it has turned over the spine. */
    fun turnFraction(
        grab: Offset,
        edge: Offset,
    ): Float {
        if (width <= 0f) return 0f
        return ((grab.x - edge.x) * outward / (2f * width)).coerceIn(0f, 1f)
    }
}

/** The page a horizontal drag past [touchSlop] turns toward, or null while it is still a tap. */
fun resolvePageCurlTarget(
    horizontalDrag: Float,
    rightToLeft: Boolean,
    touchSlop: Float,
): PageCurlTarget? {
    if (abs(horizontalDrag) <= touchSlop) return null
    val isForwardDrag = if (rightToLeft) horizontalDrag > 0f else horizontalDrag < 0f
    return if (isForwardDrag) PageCurlTarget.Next else PageCurlTarget.Previous
}

/**
 * Keeps the grabbed edge where a page hinged at the spine can actually reach: no point of the spine
 * may end up on the folded side, which holds exactly when the edge stays within reach of both spine
 * ends (the fold's distance test is affine along the spine).
 */
fun clampPageCurlEdge(
    sheet: PageCurlSheet,
    grab: Offset,
    edge: Offset,
): Offset {
    val topAnchor = Offset(sheet.hingeX, sheet.top)
    val bottomAnchor = Offset(sheet.hingeX, sheet.bottom)
    val topReach = (grab - topAnchor).getDistance()
    val bottomReach = (grab - bottomAnchor).getDistance()
    var clamped = edge
    repeat(EdgeClampIterations) {
        clamped = clamped.projectIntoDisk(topAnchor, topReach)
        clamped = clamped.projectIntoDisk(bottomAnchor, bottomReach)
    }
    return clamped
}

/**
 * Places the fold so the paper point picked up at [grab] is drawn at [edge] after wrapping around the
 * curl cylinder. The radius shrinks to zero as the fold nears the spine so the spine never lifts.
 */
fun calculatePageCurlAxis(
    sheet: PageCurlSheet,
    grab: Offset,
    edge: Offset,
    maxRadius: Float = sheet.width * DefaultPageCurlMaxRadiusFraction,
): PageCurlAxis {
    val deltaX = grab.x - edge.x
    val deltaY = grab.y - edge.y
    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
    if (distance < MinimumCurlDistancePx) {
        return PageCurlAxis(
            originX = grab.x + sheet.outward * (maxRadius + 1f),
            originY = grab.y,
            directionX = sheet.outward,
            directionY = 0f,
            radius = maxRadius,
        )
    }

    val directionX = deltaX / distance
    val directionY = deltaY / distance
    val grabCoordinate = grab.x * directionX + grab.y * directionY
    val bisectorCoordinate = grabCoordinate - distance / 2f
    val spineCoordinate = max(
        sheet.hingeX * directionX + sheet.top * directionY,
        sheet.hingeX * directionX + sheet.bottom * directionY,
    )
    val spineMargin = (bisectorCoordinate - spineCoordinate).coerceAtLeast(0f)
    val radius = min(maxRadius, 2f * spineMargin / PI.toFloat())
    val lineCoordinate = if (distance >= PI.toFloat() * radius) {
        bisectorCoordinate - PI.toFloat() * radius / 2f
    } else {
        grabCoordinate - solvePageCurlRollArc(distance = distance, radius = radius)
    }
    val shift = lineCoordinate - bisectorCoordinate
    return PageCurlAxis(
        originX = (grab.x + edge.x) / 2f + directionX * shift,
        originY = (grab.y + edge.y) / 2f + directionY * shift,
        directionX = directionX,
        directionY = directionY,
        radius = radius,
    )
}

/** Paper length `s` on the roll whose projection reaches [distance]: solves `s - r sin(s / r) = distance`. */
fun solvePageCurlRollArc(
    distance: Float,
    radius: Float,
): Float {
    if (radius <= 0f) return 0f
    var low = 0f
    var high = PI.toFloat() * radius
    repeat(RollArcIterations) {
        val middle = (low + high) / 2f
        if (middle - radius * sin(middle / radius) < distance) {
            low = middle
        } else {
            high = middle
        }
    }
    return (low + high) / 2f
}

private fun Offset.projectIntoDisk(
    center: Offset,
    radius: Float,
): Offset {
    val offset = this - center
    val distance = offset.getDistance()
    if (distance <= radius || distance <= 0f) return this
    return center + offset * (radius / distance)
}

private const val EdgeClampIterations = 6
private const val RollArcIterations = 24
private const val MinimumCurlDistancePx = 0.01f
