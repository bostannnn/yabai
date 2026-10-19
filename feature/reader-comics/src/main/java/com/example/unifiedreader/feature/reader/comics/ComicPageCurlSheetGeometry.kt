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

internal enum class ComicPageCurlTransitionPolicy {
    UseCurl,
    YieldToPager,
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
): ComicPageCurlSheetBounds? {
    val canvasBounds = layout.canvasBounds() ?: return null
    val side = resolveComicPageCurlOutgoingSheetSide(
        readingDirection = readingDirection,
        target = target,
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
        layout.singlePagePaperBounds(canvasBounds)
    } ?: return null
    return ComicPageCurlSheetBounds(
        bounds = paperBounds,
        side = side,
    )
}

/**
 * Resolves the leaf that turns. On spreads it is the current page on the outgoing side, hinged at the
 * gutter, and its back is the target spread's facing page. On single pages the leaf is hinged on the
 * reading-start edge: a forward turn lifts the current page, a backward turn brings the previous page
 * back over the spine. The leaf is drawn between the book's outer edges but may swing above and below
 * the pages, which a tilted fold does with its corners.
 */
internal fun resolveComicPageCurlTurn(
    currentLayout: ComicSpreadRasterLayout,
    targetLayout: ComicSpreadRasterLayout,
    readingDirection: ComicReadingDirection,
    target: ComicPageCurlTarget,
): ComicPageCurlTurn? {
    val policy = resolveComicPageCurlTransitionPolicy(
        currentLayout = currentLayout,
        targetLayout = targetLayout,
        readingDirection = readingDirection,
        target = target,
    )
    if (policy != ComicPageCurlTransitionPolicy.UseCurl) return null
    val canvasBounds = currentLayout.canvasBounds() ?: return null

    if (currentLayout.pages.size >= 2) {
        val sheetBounds = resolveComicPageCurlSheetBounds(
            layout = currentLayout,
            readingDirection = readingDirection,
            target = target,
        ) ?: return null
        val bookBounds = currentLayout.pages
            .mapNotNull { placement -> placement.pageBounds.intersect(canvasBounds) }
            .unionOrNull()
            ?.spanningHeightOf(canvasBounds)
            ?: return null
        val outward = when (sheetBounds.side) {
            ComicPageCurlSheetSide.Left -> -1f
            ComicPageCurlSheetSide.Right -> 1f
        }
        return comicPageCurlTurn(
            target = target,
            paperBounds = sheetBounds.bounds,
            bookBounds = bookBounds,
            outward = outward,
            follow = ComicPageCurlFollow.Edge,
            backIsFacingPage = true,
        )
    }

    val currentPaper = currentLayout.singlePagePaperBounds(canvasBounds) ?: return null
    val targetPaper = targetLayout.singlePagePaperBounds(canvasBounds) ?: return null
    val bookBounds = listOf(currentPaper, targetPaper)
        .unionOrNull()
        ?.spanningHeightOf(canvasBounds)
        ?: return null
    val follow = when (target) {
        ComicPageCurlTarget.Next -> ComicPageCurlFollow.Edge
        ComicPageCurlTarget.Previous -> ComicPageCurlFollow.Fold
    }
    return comicPageCurlTurn(
        target = target,
        paperBounds = if (follow == ComicPageCurlFollow.Edge) currentPaper else targetPaper,
        bookBounds = bookBounds,
        outward = when (readingDirection) {
            ComicReadingDirection.LTR -> 1f
            ComicReadingDirection.RTL -> -1f
        },
        follow = follow,
        backIsFacingPage = false,
    )
}

private fun comicPageCurlTurn(
    target: ComicPageCurlTarget,
    paperBounds: ComicRasterRect,
    bookBounds: ComicRasterRect,
    outward: Float,
    follow: ComicPageCurlFollow,
    backIsFacingPage: Boolean,
): ComicPageCurlTurn? {
    if (!paperBounds.hasArea || !bookBounds.hasArea) return null
    return ComicPageCurlTurn(
        target = target,
        sheet = ComicPageCurlSheet(
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

    val currentSheet = resolveComicPageCurlSheetBounds(
        layout = currentLayout,
        readingDirection = readingDirection,
        target = target,
    ) ?: return ComicPageCurlTransitionPolicy.YieldToPager
    val targetSheet = resolveComicPageCurlSheetBounds(
        layout = targetLayout,
        readingDirection = readingDirection,
        target = target,
    ) ?: return ComicPageCurlTransitionPolicy.YieldToPager

    return if (currentSheet.bounds.isCompatibleCurlPaperBounds(targetSheet.bounds)) {
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

private fun ComicSpreadRasterLayout.canvasBounds(): ComicRasterRect? {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null
    return ComicRasterRect(
        left = 0,
        top = 0,
        width = canvasSize.width,
        height = canvasSize.height,
    )
}

// A single page's paper is its visible image, not the letterboxed page slot.
private fun ComicSpreadRasterLayout.singlePagePaperBounds(
    canvasBounds: ComicRasterRect,
): ComicRasterRect? = pages
    .singleOrNull()
    ?.let { placement ->
        placement.destinationBounds
            .intersect(placement.clipBounds)
            ?.intersect(canvasBounds)
            ?: placement.pageBounds.intersect(canvasBounds)
    }

private fun ComicRasterRect.spanningHeightOf(
    canvasBounds: ComicRasterRect,
): ComicRasterRect = ComicRasterRect(
    left = left,
    top = canvasBounds.top,
    width = width,
    height = canvasBounds.height,
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
