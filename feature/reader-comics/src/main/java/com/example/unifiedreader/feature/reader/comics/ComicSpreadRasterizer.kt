package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

internal val ComicReaderBackgroundColor = Color(0xFF0A0D11)
internal val ComicPageBackgroundColor = Color(0xFF0B0F13)

private const val FallbackCompositePageDimension = 1_000
private const val MinimumSpreadPageAspectRatio = 0.5f

internal object ComicSpreadRasterizer {
    fun rasterize(
        spread: ComicPageSpread,
        pageImagesById: Map<String, ImageBitmap>,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int = 0,
    ): ImageBitmap {
        require(canvasSize.width > 0 && canvasSize.height > 0) {
            "canvasSize must be positive"
        }

        val layout = layout(
            spread = spread,
            imageSizesByPageId = pageImagesById.mapValues { (_, image) ->
                IntSize(image.width, image.height)
            },
            canvasSize = canvasSize,
            pageFitMode = pageFitMode,
            stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
        )
        val output = Bitmap.createBitmap(
            canvasSize.width,
            canvasSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = AndroidCanvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        paint.color = ComicReaderBackgroundColor.toArgb()
        canvas.drawRect(0f, 0f, canvasSize.width.toFloat(), canvasSize.height.toFloat(), paint)
        layout.pages.forEach { placement ->
            canvas.save()
            canvas.clipRect(placement.clipBounds.toAndroidRect())
            paint.color = ComicPageBackgroundColor.toArgb()
            canvas.drawRect(placement.pageBounds.toAndroidRect(), paint)

            val image = pageImagesById[placement.page.spec.id]?.asAndroidBitmap()
            if (image != null) {
                val source = placement.sourceWindow.toAndroidRect(
                    imageWidth = image.width,
                    imageHeight = image.height,
                )
                if (!source.isEmpty && placement.destinationBounds.hasArea) {
                    canvas.drawBitmap(
                        image,
                        source,
                        placement.destinationBounds.toAndroidRect(),
                        paint,
                    )
                }
            }
            canvas.restore()
        }

        return output.asImageBitmap()
    }

    fun layout(
        spread: ComicPageSpread,
        imageSizesByPageId: Map<String, IntSize>,
        canvasSize: IntSize,
        pageFitMode: ComicPageFitMode,
        stitchedSpreadOverlapPx: Int = 0,
    ): ComicSpreadRasterLayout {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || spread.pages.isEmpty()) {
            return ComicSpreadRasterLayout(canvasSize = canvasSize, pages = emptyList())
        }

        val isDoublePage = spread.pages.size == 2
        val useFitSpread = isDoublePage &&
            pageFitMode == ComicPageFitMode.FitPage &&
            spread.pages.all { it.segment == ComicPageSegment.Full }
        val overlapPx = stitchedSpreadOverlapPx
            .coerceAtLeast(0)
            .takeIf { isDoublePage }
            ?: 0
        val canvasBounds = ComicRasterRect(
            left = 0,
            top = 0,
            width = canvasSize.width,
            height = canvasSize.height,
        )
        val rowBounds = if (useFitSpread) {
            fitSpreadBounds(
                spread = spread,
                imageSizesByPageId = imageSizesByPageId,
                canvasBounds = canvasBounds,
            )
        } else {
            canvasBounds
        }
        val pageBounds = if (isDoublePage) {
            weightedPageBounds(
                container = rowBounds,
                weights = spread.pages.map { page ->
                    page.layoutWeightForSourceSize(imageSizesByPageId[page.spec.id])
                },
                overlapPx = overlapPx,
            )
        } else {
            listOf(rowBounds)
        }
        val clipContainer = if (useFitSpread) rowBounds else canvasBounds
        val pages = spread.pages.mapIndexedNotNull { index, page ->
            val bounds = pageBounds.getOrNull(index)?.takeIf(ComicRasterRect::hasArea)
                ?: return@mapIndexedNotNull null
            val sourceSize = page.compositeSourceSize(imageSizesByPageId)
            val sourceWindow = page.segment.sourceWindow(
                imageWidth = sourceSize.width,
                imageHeight = sourceSize.height,
            )
            val clipBounds = bounds.intersect(clipContainer)
                ?.intersect(canvasBounds)
                ?: return@mapIndexedNotNull null
            ComicSpreadRasterPagePlacement(
                page = page,
                pageBounds = bounds,
                clipBounds = clipBounds,
                sourceWindow = sourceWindow,
                destinationBounds = fittedDestinationBounds(
                    pageBounds = bounds,
                    sourceWindow = sourceWindow,
                    fitMode = if (useFitSpread) ComicPageFitMode.FitPage else pageFitMode,
                ),
            )
        }

        return ComicSpreadRasterLayout(canvasSize = canvasSize, pages = pages)
    }
}

internal data class ComicSpreadRasterLayout(
    val canvasSize: IntSize,
    val pages: List<ComicSpreadRasterPagePlacement>,
)

internal data class ComicSpreadRasterPagePlacement(
    val page: ComicSpreadPage,
    val pageBounds: ComicRasterRect,
    val clipBounds: ComicRasterRect,
    val sourceWindow: ComicPageSourceWindow,
    val destinationBounds: ComicRasterRect,
)

internal data class ComicRasterRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int
        get() = left + width

    val bottom: Int
        get() = top + height

    val hasArea: Boolean
        get() = width > 0 && height > 0

    fun intersect(
        other: ComicRasterRect,
    ): ComicRasterRect? {
        val intersectionLeft = max(left, other.left)
        val intersectionTop = max(top, other.top)
        val intersectionRight = minOf(right, other.right)
        val intersectionBottom = minOf(bottom, other.bottom)
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return null
        }
        return ComicRasterRect(
            left = intersectionLeft,
            top = intersectionTop,
            width = intersectionRight - intersectionLeft,
            height = intersectionBottom - intersectionTop,
        )
    }

    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)
}

internal fun comicSpreadCompositeImageSize(
    spread: ComicPageSpread,
    imageSizesByPageId: Map<String, IntSize> = emptyMap(),
): IntSize {
    val pageSizes = spread.pages.map { page ->
        val sourceSize = page.compositeSourceSize(imageSizesByPageId)
        IntSize(
            width = (sourceSize.width * page.segment.widthFraction)
                .roundToInt()
                .coerceAtLeast(1),
            height = sourceSize.height,
        )
    }
    return equalHeightCompositeImageSize(
        imageSizes = pageSizes,
        minimumAspectRatio = MinimumSpreadPageAspectRatio.takeIf { spread.pages.size > 1 },
    )
}

internal fun equalHeightCompositeImageSize(
    imageSizes: List<IntSize>,
    minimumAspectRatio: Float? = null,
): IntSize {
    val validSizes = imageSizes
        .filter { it.width > 0 && it.height > 0 }
        .map { size ->
            if (minimumAspectRatio == null) {
                size
            } else {
                IntSize(
                    width = max(
                        size.width,
                        (size.height * minimumAspectRatio).roundToInt(),
                    ),
                    height = size.height,
                )
            }
        }
    if (validSizes.isEmpty()) return IntSize.Zero

    val commonHeight = validSizes.maxOf(IntSize::height)
    val combinedWidth = validSizes.sumOf { size ->
        (size.width.toDouble() * commonHeight / size.height)
            .roundToInt()
            .coerceAtLeast(1)
    }
    return IntSize(width = combinedWidth, height = commonHeight)
}

internal fun ComicSpreadPage.layoutWeightForSourceSize(
    sourceSize: IntSize?,
): Float = adjustedAspectRatio(sourceSize)
    .coerceAtLeast(MinimumSpreadPageAspectRatio)

private fun fitSpreadBounds(
    spread: ComicPageSpread,
    imageSizesByPageId: Map<String, IntSize>,
    canvasBounds: ComicRasterRect,
): ComicRasterRect {
    val compositeSize = comicSpreadCompositeImageSize(
        spread = spread,
        imageSizesByPageId = imageSizesByPageId,
    )
    val combinedAspectRatio = if (compositeSize.width > 0 && compositeSize.height > 0) {
        compositeSize.width / compositeSize.height.toFloat()
    } else {
        spread.pages
            .sumOf { page -> page.layoutWeightForSourceSize(imageSizesByPageId[page.spec.id]).toDouble() }
            .toFloat()
            .coerceAtLeast(1f)
    }
    val containerAspectRatio = canvasBounds.width / canvasBounds.height.toFloat()
    val spreadSize = if (combinedAspectRatio >= containerAspectRatio) {
        IntSize(
            width = canvasBounds.width,
            height = max(1, (canvasBounds.width / combinedAspectRatio).roundToInt()),
        )
    } else {
        IntSize(
            width = max(1, (canvasBounds.height * combinedAspectRatio).roundToInt()),
            height = canvasBounds.height,
        )
    }
    return ComicRasterRect(
        left = canvasBounds.left + ((canvasBounds.width - spreadSize.width) / 2f).roundToInt(),
        top = canvasBounds.top + ((canvasBounds.height - spreadSize.height) / 2f).roundToInt(),
        width = spreadSize.width,
        height = spreadSize.height,
    )
}

private fun weightedPageBounds(
    container: ComicRasterRect,
    weights: List<Float>,
    overlapPx: Int,
): List<ComicRasterRect> {
    val totalWeight = weights.sum().takeIf { it > 0f } ?: weights.size.toFloat()
    var consumedWeight = 0f
    return weights.mapIndexed { index, weight ->
        val start = (container.width * consumedWeight / totalWeight).roundToInt()
        consumedWeight += weight.takeIf { it > 0f } ?: 1f
        val end = if (index == weights.lastIndex) {
            container.width
        } else {
            (container.width * consumedWeight / totalWeight).roundToInt()
        }
        ComicRasterRect(
            left = container.left + start - if (index > 0) overlapPx else 0,
            top = container.top,
            width = (end - start).coerceAtLeast(0),
            height = container.height,
        )
    }
}

private fun fittedDestinationBounds(
    pageBounds: ComicRasterRect,
    sourceWindow: ComicPageSourceWindow,
    fitMode: ComicPageFitMode,
): ComicRasterRect {
    val widthScale = pageBounds.width / sourceWindow.width.toFloat()
    val heightScale = pageBounds.height / sourceWindow.height.toFloat()
    val drawScale = when (fitMode) {
        ComicPageFitMode.FitPage -> minOf(widthScale, heightScale)
        ComicPageFitMode.FitWidth -> widthScale
        ComicPageFitMode.FitHeight -> heightScale
    }
    val drawWidth = max(1, (sourceWindow.width * drawScale).roundToInt())
    val drawHeight = max(1, (sourceWindow.height * drawScale).roundToInt())
    return ComicRasterRect(
        left = pageBounds.left + ((pageBounds.width - drawWidth) / 2f).roundToInt(),
        top = pageBounds.top + ((pageBounds.height - drawHeight) / 2f).roundToInt(),
        width = drawWidth,
        height = drawHeight,
    )
}

private fun ComicSpreadPage.adjustedAspectRatio(
    sourceSize: IntSize?,
): Float {
    val baseAspectRatio = sourceSize.safeAspectRatio() ?: metadata?.aspectRatio ?: 1f
    return baseAspectRatio * segment.widthFraction
}

private fun ComicSpreadPage.compositeSourceSize(
    imageSizesByPageId: Map<String, IntSize>,
): IntSize {
    val sourceSize = imageSizesByPageId[spec.id]
    val sourceWidth = sourceSize?.width
        ?: metadata?.width?.takeIf { it > 0 }
        ?: FallbackCompositePageDimension
    val sourceHeight = sourceSize?.height
        ?: metadata?.height?.takeIf { it > 0 }
        ?: FallbackCompositePageDimension
    return IntSize(
        width = sourceWidth.coerceAtLeast(1),
        height = sourceHeight.coerceAtLeast(1),
    )
}

private fun IntSize?.safeAspectRatio(): Float? = this
    ?.takeIf { it.height > 0 && it.width > 0 }
    ?.let { it.width / it.height.toFloat() }

private fun ComicPageSourceWindow.toAndroidRect(
    imageWidth: Int,
    imageHeight: Int,
): Rect {
    val left = offsetX.coerceIn(0, imageWidth.coerceAtLeast(1) - 1)
    val right = (left + width).coerceIn(left + 1, imageWidth.coerceAtLeast(left + 1))
    val bottom = height.coerceIn(1, imageHeight.coerceAtLeast(1))
    return Rect(left, 0, right, bottom)
}
