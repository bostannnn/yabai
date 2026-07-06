package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

internal enum class ComicPageCurlSheetSide {
    Left,
    Right,
}

internal data class ComicPageCurlSheetBounds(
    val bounds: ComicRasterRect,
    val side: ComicPageCurlSheetSide,
)

internal data class ComicPageCurlSheetGeometry(
    val paperBounds: ComicRasterRect,
    val renderBounds: ComicRasterRect,
    val side: ComicPageCurlSheetSide,
)

internal enum class ComicPageCurlTransitionPolicy {
    UseCurl,
    YieldToPager,
}

internal fun calculateComicPageCurlAxisForSheet(
    progress: Float,
    tilt: Float,
    sheetBounds: ComicRasterRect,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): ComicPageCurlAxis {
    val axis = calculateComicPageCurlAxis(
        progress = progress,
        tilt = tilt,
        containerWidth = sheetBounds.width.toFloat(),
        containerHeight = sheetBounds.height.toFloat(),
        readingDirection = readingDirection,
        target = target,
    )
    return axis.copy(
        originX = sheetBounds.left + axis.originX,
        originY = sheetBounds.top + axis.originY,
    )
}

internal fun resolveComicPageCurlOutgoingSheetSide(
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): ComicPageCurlSheetSide = when (target) {
    ComicPageCurlTarget.Next -> when (readingDirection) {
        ComicReadingDirection.LTR -> ComicPageCurlSheetSide.Right
        ComicReadingDirection.RTL -> ComicPageCurlSheetSide.Left
    }

    ComicPageCurlTarget.Previous -> when (readingDirection) {
        ComicReadingDirection.LTR -> ComicPageCurlSheetSide.Left
        ComicReadingDirection.RTL -> ComicPageCurlSheetSide.Right
    }
}

internal fun resolveComicPageCurlSheetBounds(
    layout: ComicSpreadRasterLayout,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): ComicPageCurlSheetBounds? = resolveComicPageCurlSheetGeometry(
    layout = layout,
    readingDirection = readingDirection,
    target = target,
)?.let { geometry ->
    ComicPageCurlSheetBounds(
        bounds = geometry.paperBounds,
        side = geometry.side,
    )
}

internal fun resolveComicPageCurlSheetGeometry(
    layout: ComicSpreadRasterLayout,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): ComicPageCurlSheetGeometry? {
    if (layout.canvasSize.width <= 0 || layout.canvasSize.height <= 0) return null

    val side = resolveComicPageCurlOutgoingSheetSide(
        readingDirection = readingDirection,
        target = target,
    )
    val canvasBounds = ComicRasterRect(
        left = 0,
        top = 0,
        width = layout.canvasSize.width,
        height = layout.canvasSize.height,
    )

    val paperBounds = if (layout.pages.size >= 2) {
        val pageIndex = when (side) {
            ComicPageCurlSheetSide.Left -> 0
            ComicPageCurlSheetSide.Right -> layout.pages.lastIndex
        }
        layout.pages
            .getOrNull(pageIndex)
            ?.pageBounds
            ?.intersect(canvasBounds)
    } else {
        layout.pages
            .singleOrNull()
            ?.let { placement ->
                placement.destinationBounds
                    .intersect(placement.clipBounds)
                    ?.intersect(canvasBounds)
                    ?: placement.pageBounds.intersect(canvasBounds)
            }
    } ?: return null

    val renderBounds = resolveComicPageCurlRenderBounds(
        layout = layout,
        paperBounds = paperBounds,
        canvasBounds = canvasBounds,
    ) ?: return null

    return ComicPageCurlSheetGeometry(
        paperBounds = paperBounds,
        renderBounds = renderBounds,
        side = side,
    )
}

internal fun resolveComicPageCurlTransitionPolicy(
    currentLayout: ComicSpreadRasterLayout?,
    targetLayout: ComicSpreadRasterLayout?,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): ComicPageCurlTransitionPolicy {
    val currentKind = currentLayout?.comicPageCurlLayoutKind()
        ?: return ComicPageCurlTransitionPolicy.YieldToPager
    val targetKind = targetLayout?.comicPageCurlLayoutKind()
        ?: return ComicPageCurlTransitionPolicy.YieldToPager
    if (currentKind != targetKind) {
        return ComicPageCurlTransitionPolicy.YieldToPager
    }

    val currentGeometry = resolveComicPageCurlSheetGeometry(
        layout = currentLayout,
        readingDirection = readingDirection,
        target = target,
    ) ?: return ComicPageCurlTransitionPolicy.YieldToPager
    val targetGeometry = resolveComicPageCurlSheetGeometry(
        layout = targetLayout,
        readingDirection = readingDirection,
        target = target,
    ) ?: return ComicPageCurlTransitionPolicy.YieldToPager

    return if (currentGeometry.paperBounds.isCompatibleCurlPaperBounds(targetGeometry.paperBounds)) {
        ComicPageCurlTransitionPolicy.UseCurl
    } else {
        ComicPageCurlTransitionPolicy.YieldToPager
    }
}

internal fun ComicRasterRect.contains(
    position: Offset,
): Boolean = hasArea &&
    position.x >= left &&
    position.x <= right &&
    position.y >= top &&
    position.y <= bottom

private enum class ComicPageCurlLayoutKind {
    SinglePage,
    Spread,
}

private fun ComicSpreadRasterLayout.comicPageCurlLayoutKind(): ComicPageCurlLayoutKind? = when (pages.size) {
    1 -> ComicPageCurlLayoutKind.SinglePage
    in 2..Int.MAX_VALUE -> ComicPageCurlLayoutKind.Spread
    else -> null
}

private fun resolveComicPageCurlRenderBounds(
    layout: ComicSpreadRasterLayout,
    paperBounds: ComicRasterRect,
    canvasBounds: ComicRasterRect,
): ComicRasterRect? {
    val rawBounds = if (layout.pages.size >= 2) {
        layout.pages
            .mapNotNull { placement -> placement.pageBounds.intersect(canvasBounds) }
            .unionOrNull()
    } else {
        paperBounds.expand(
            horizontal = paperBounds.width / 2,
            vertical = 0,
        )
    } ?: return null
    return rawBounds
        .intersect(canvasBounds)
        ?.takeIf(ComicRasterRect::hasArea)
}

private fun ComicRasterRect.expand(
    horizontal: Int,
    vertical: Int,
): ComicRasterRect = ComicRasterRect(
    left = left - horizontal.coerceAtLeast(0),
    top = top - vertical.coerceAtLeast(0),
    width = width + horizontal.coerceAtLeast(0) * 2,
    height = height + vertical.coerceAtLeast(0) * 2,
)

private fun List<ComicRasterRect>.unionOrNull(): ComicRasterRect? {
    if (isEmpty()) return null
    val unionLeft = minOf { it.left }
    val unionTop = minOf { it.top }
    val unionRight = maxOf { it.right }
    val unionBottom = maxOf { it.bottom }
    if (unionRight <= unionLeft || unionBottom <= unionTop) return null
    return ComicRasterRect(
        left = unionLeft,
        top = unionTop,
        width = unionRight - unionLeft,
        height = unionBottom - unionTop,
    )
}

private fun ComicRasterRect.isCompatibleCurlPaperBounds(
    other: ComicRasterRect,
): Boolean {
    if (!hasArea || !other.hasArea) return false
    val horizontalTolerance = max(
        CurlBoundsMinimumTolerancePx,
        max(width, other.width) * CurlBoundsPositionToleranceFraction,
    )
    val verticalTolerance = max(
        CurlBoundsMinimumTolerancePx,
        max(height, other.height) * CurlBoundsPositionToleranceFraction,
    )
    return relativeDifference(width, other.width) <= CurlBoundsSizeToleranceFraction &&
        relativeDifference(height, other.height) <= CurlBoundsSizeToleranceFraction &&
        relativeAspectDifference(other) <= CurlBoundsAspectToleranceFraction &&
        abs(left - other.left) <= horizontalTolerance &&
        abs(right - other.right) <= horizontalTolerance &&
        abs(top - other.top) <= verticalTolerance &&
        abs(bottom - other.bottom) <= verticalTolerance
}

private fun relativeDifference(
    first: Int,
    second: Int,
): Float = abs(first - second).toFloat() / max(first, second).coerceAtLeast(1)

private fun ComicRasterRect.relativeAspectDifference(
    other: ComicRasterRect,
): Float {
    val aspect = width / height.toFloat()
    val otherAspect = other.width / other.height.toFloat()
    return abs(aspect - otherAspect) / max(aspect, otherAspect).coerceAtLeast(0.0001f)
}

private const val CurlBoundsMinimumTolerancePx = 2f
private const val CurlBoundsPositionToleranceFraction = 0.06f
private const val CurlBoundsSizeToleranceFraction = 0.08f
private const val CurlBoundsAspectToleranceFraction = 0.08f
