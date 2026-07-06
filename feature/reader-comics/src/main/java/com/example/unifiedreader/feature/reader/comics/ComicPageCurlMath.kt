package com.example.unifiedreader.feature.reader.comics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

private const val DefaultCurlRadiusFraction = 0.09f
private const val DefaultCurlTiltScale = 0.7f

internal enum class ComicPageCurlTarget {
    Previous,
    Next,
}

internal data class ComicPageCurlAxis(
    val originX: Float,
    val originY: Float,
    val directionX: Float,
    val directionY: Float,
    val radius: Float,
)

internal fun resolveComicPageCurlTarget(
    horizontalDrag: Float,
    readingDirection: ComicReadingDirection,
    touchSlop: Float,
): ComicPageCurlTarget? {
    if (abs(horizontalDrag) <= touchSlop) return null
    val isForwardDrag = when (readingDirection) {
        ComicReadingDirection.LTR -> horizontalDrag < 0f
        ComicReadingDirection.RTL -> horizontalDrag > 0f
    }
    return if (isForwardDrag) ComicPageCurlTarget.Next else ComicPageCurlTarget.Previous
}

internal fun comicPageCurlProgressDelta(
    dragAmount: Float,
    containerWidth: Float,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): Float {
    if (containerWidth <= 0f) return 0f
    return dragAmount * comicPageCurlProgressSign(readingDirection, target) / containerWidth
}

internal fun applyComicPageCurlProgressDelta(
    progress: Float,
    dragAmount: Float,
    containerWidth: Float,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): Float = (progress + comicPageCurlProgressDelta(
    dragAmount = dragAmount,
    containerWidth = containerWidth,
    readingDirection = readingDirection,
    target = target,
)).coerceIn(0f, 1f)

internal fun comicPageCurlTiltForGrab(
    grabY: Float,
    containerHeight: Float,
): Float {
    if (containerHeight <= 0f) return 0f
    return ((grabY / containerHeight) - 0.5f) * DefaultCurlTiltScale
}

internal fun calculateComicPageCurlAxis(
    progress: Float,
    tilt: Float,
    containerWidth: Float,
    containerHeight: Float,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
    radiusFraction: Float = DefaultCurlRadiusFraction,
): ComicPageCurlAxis {
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return ComicPageCurlAxis(
            originX = 0f,
            originY = 0f,
            directionX = 1f,
            directionY = 0f,
            radius = 0f,
        )
    }

    val radius = (containerWidth * radiusFraction).coerceAtLeast(1f)
    val directionXSign = comicPageCurlDirectionXSign(readingDirection, target)
    val directionLength = sqrt(1f + tilt * tilt)
    val directionX = directionXSign / directionLength
    val directionY = tilt / directionLength
    val margin = abs(tilt) * containerHeight / 2f
    val axisProgress = progress.coerceIn(0f, 1f)
    val startX: Float
    val endX: Float
    if (directionXSign > 0f) {
        startX = containerWidth + radius + margin
        endX = -(containerWidth + PI.toFloat() * radius + margin)
    } else {
        startX = -(radius + margin)
        endX = containerWidth + (containerWidth + PI.toFloat() * radius + margin)
    }

    return ComicPageCurlAxis(
        originX = startX + (endX - startX) * axisProgress,
        originY = containerHeight / 2f,
        directionX = directionX,
        directionY = directionY,
        radius = radius,
    )
}

private fun comicPageCurlProgressSign(
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): Float = when (target) {
    ComicPageCurlTarget.Next -> when (readingDirection) {
        ComicReadingDirection.LTR -> -1f
        ComicReadingDirection.RTL -> 1f
    }

    ComicPageCurlTarget.Previous -> when (readingDirection) {
        ComicReadingDirection.LTR -> 1f
        ComicReadingDirection.RTL -> -1f
    }
}

private fun comicPageCurlDirectionXSign(
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): Float = when (target) {
    ComicPageCurlTarget.Next -> when (readingDirection) {
        ComicReadingDirection.LTR -> 1f
        ComicReadingDirection.RTL -> -1f
    }

    ComicPageCurlTarget.Previous -> when (readingDirection) {
        ComicReadingDirection.LTR -> -1f
        ComicReadingDirection.RTL -> 1f
    }
}
