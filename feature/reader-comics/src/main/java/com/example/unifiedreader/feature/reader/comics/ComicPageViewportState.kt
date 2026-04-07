package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

private const val MinimumZoomScale = 1f
private const val MaximumZoomScale = 4f
private const val ZoomEpsilon = 0.01f

@Stable
class ComicPageViewportState internal constructor(
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
) {
    var scale by mutableFloatStateOf(initialScale)
        private set

    var offset by mutableStateOf(Offset(initialOffsetX, initialOffsetY))
        private set

    val isZoomed: Boolean
        get() = scale > MinimumZoomScale + ZoomEpsilon

    fun onGesture(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(MinimumZoomScale, MaximumZoomScale)
        if (abs(nextScale - MinimumZoomScale) <= ZoomEpsilon) {
            scale = MinimumZoomScale
            offset = constrainOffset(
                requestedOffset = Offset.Zero,
                scale = MinimumZoomScale,
                containerSize = containerSize,
                imageSize = imageSize,
                fitMode = fitMode,
            )
        } else {
            scale = nextScale
            offset = constrainOffset(
                requestedOffset = Offset(
                    x = offset.x + panChange.x,
                    y = offset.y + panChange.y,
                ),
                scale = nextScale,
                containerSize = containerSize,
                imageSize = imageSize,
                fitMode = fitMode,
            )
        }
    }

    fun onPan(
        panChange: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        if (!canPan(containerSize, imageSize, fitMode)) return
        onGesture(
            zoomChange = 1f,
            panChange = panChange,
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = fitMode,
        )
    }

    fun toggleZoom(
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        if (isZoomed) {
            scale = MinimumZoomScale
            offset = constrainOffset(
                requestedOffset = Offset.Zero,
                scale = MinimumZoomScale,
                containerSize = containerSize,
                imageSize = imageSize,
                fitMode = fitMode,
            )
            return
        }
        scale = 2f
        offset = constrainOffset(
            requestedOffset = Offset.Zero,
            scale = scale,
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = fitMode,
        )
    }

    fun canPan(
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Boolean {
        val maxOffsets = maxOffsets(
            scale = scale,
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = fitMode,
        )
        return maxOffsets.x > 0.5f || maxOffsets.y > 0.5f
    }

    fun reconcileLayout(
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        offset = constrainOffset(
            requestedOffset = offset,
            scale = scale,
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = fitMode,
        )
    }

    fun reset() {
        scale = MinimumZoomScale
        offset = Offset.Zero
    }

    private fun constrainOffset(
        requestedOffset: Offset,
        scale: Float,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Offset {
        val maxOffsets = maxOffsets(
            scale = scale,
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = fitMode,
        )
        return Offset(
            x = requestedOffset.x.coerceIn(-maxOffsets.x, maxOffsets.x),
            y = requestedOffset.y.coerceIn(-maxOffsets.y, maxOffsets.y),
        )
    }

    private fun maxOffsets(
        scale: Float,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Offset {
        if (containerSize.width <= 0 || containerSize.height <= 0 || imageSize.width <= 0 || imageSize.height <= 0) {
            return Offset.Zero
        }
        val renderedSize = renderedImageSize(
            scale = scale,
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = fitMode,
        )
        return Offset(
            x = ((renderedSize.width - containerSize.width) / 2f).coerceAtLeast(0f),
            y = ((renderedSize.height - containerSize.height) / 2f).coerceAtLeast(0f),
        )
    }

    private fun renderedImageSize(
        scale: Float,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Size {
        val widthScale = containerSize.width / imageSize.width.toFloat()
        val heightScale = containerSize.height / imageSize.height.toFloat()
        val baseScale = when (fitMode) {
            ComicPageFitMode.FitPage -> minOf(widthScale, heightScale)
            ComicPageFitMode.FitWidth -> widthScale
            ComicPageFitMode.FitHeight -> heightScale
        }
        return Size(
            width = imageSize.width * baseScale * scale,
            height = imageSize.height * baseScale * scale,
        )
    }

    companion object {
        val Saver: Saver<ComicPageViewportState, *> = listSaver(
            save = { listOf(it.scale, it.offset.x, it.offset.y) },
            restore = { restored ->
                ComicPageViewportState(
                    initialScale = restored[0],
                    initialOffsetX = restored[1],
                    initialOffsetY = restored[2],
                )
            },
        )
    }
}

@Composable
fun rememberComicPageViewportState(
    pageId: String,
): ComicPageViewportState = rememberSaveable(
    pageId,
    saver = ComicPageViewportState.Saver,
) {
    ComicPageViewportState(
        initialScale = MinimumZoomScale,
        initialOffsetX = 0f,
        initialOffsetY = 0f,
    )
}
