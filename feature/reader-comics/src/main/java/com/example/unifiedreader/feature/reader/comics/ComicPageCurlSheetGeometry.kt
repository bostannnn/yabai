package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset

internal enum class ComicPageCurlSheetSide {
    Left,
    Right,
}

internal data class ComicPageCurlSheetBounds(
    val bounds: ComicRasterRect,
    val side: ComicPageCurlSheetSide,
)

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
): ComicPageCurlSheetBounds? {
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

    val bounds = if (layout.pages.size >= 2) {
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

    return ComicPageCurlSheetBounds(
        bounds = bounds,
        side = side,
    )
}

internal fun ComicRasterRect.contains(
    position: Offset,
): Boolean = hasArea &&
    position.x >= left &&
    position.x <= right &&
    position.y >= top &&
    position.y <= bottom
