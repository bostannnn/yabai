package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

internal val ComicStitchedSpreadOverlap = 1.dp

private const val StitchedSpreadMaxDimensionDeltaFraction = 0.05f
private const val StitchedSpreadSampleCount = 24
private const val StitchedSpreadMaxAverageEdgeDistance = 0.08f

internal fun ComicPageSpread.isLikelyStitchedSpread(
    loadedImagesByPageId: Map<String, ImageBitmap?>,
): Boolean {
    if (pages.size != 2 || pages.any { it.segment != ComicPageSegment.Full }) return false
    val leftImage = loadedImagesByPageId[pages[0].spec.id] ?: return false
    val rightImage = loadedImagesByPageId[pages[1].spec.id] ?: return false
    val heightsAreAligned = abs(leftImage.height - rightImage.height) <=
        maxOf(leftImage.height, rightImage.height) * StitchedSpreadMaxDimensionDeltaFraction
    val widthsAreAligned = abs(leftImage.width - rightImage.width) <=
        maxOf(leftImage.width, rightImage.width) * StitchedSpreadMaxDimensionDeltaFraction
    if (!heightsAreAligned || !widthsAreAligned) return false

    val leftPixels = leftImage.toPixelMap()
    val rightPixels = rightImage.toPixelMap()
    val sampleCount = minOf(
        StitchedSpreadSampleCount,
        leftImage.height,
        rightImage.height,
    )
    if (sampleCount <= 0) return false

    val leftX = leftImage.width - 1
    val rightX = 0
    var totalDistance = 0f
    for (sampleIndex in 0 until sampleCount) {
        val y = if (sampleCount == 1) {
            0
        } else {
            ((sampleIndex.toFloat() / (sampleCount - 1)) * (minOf(leftImage.height, rightImage.height) - 1))
                .toInt()
        }
        totalDistance += leftPixels[leftX, y].distanceTo(rightPixels[rightX, y])
    }
    val averageDistance = totalDistance / sampleCount
    return averageDistance <= StitchedSpreadMaxAverageEdgeDistance
}

private fun Color.distanceTo(
    other: Color,
): Float {
    val redDelta = red - other.red
    val greenDelta = green - other.green
    val blueDelta = blue - other.blue
    return sqrt((redDelta * redDelta) + (greenDelta * greenDelta) + (blueDelta * blueDelta))
}
