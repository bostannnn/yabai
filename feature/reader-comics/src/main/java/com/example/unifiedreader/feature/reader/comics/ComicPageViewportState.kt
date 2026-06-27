package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.core.ReaderZoomLayout
import com.example.unifiedreader.feature.reader.core.ReaderZoomViewportState
import kotlinx.coroutines.CoroutineScope

private const val MinimumZoomScale = 1f
private const val MaximumZoomScale = 4f

@Stable
class ComicPageViewportState internal constructor(
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    initialNormalizedOffset: Offset? = null,
) {
    private val viewport = ReaderZoomViewportState(
        initialScale = initialScale,
        initialOffset = Offset(initialOffsetX, initialOffsetY),
        initialNormalizedOffset = initialNormalizedOffset,
        minimumScale = MinimumZoomScale,
        maximumScale = MaximumZoomScale,
        doubleTapScale = 2f,
    )

    val scale: Float
        get() = viewport.scale

    val offset: Offset
        get() = viewport.offset

    val isZoomed: Boolean
        get() = viewport.isZoomed

    fun onGesture(
        centroid: Offset,
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        updateLayout(containerSize, imageSize, fitMode)
        viewport.onGesture(
            centroid = centroid,
            zoomChange = zoomChange,
            panChange = panChange,
        )
    }

    fun onPan(
        panChange: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Offset {
        updateLayout(containerSize, imageSize, fitMode)
        return viewport.onPan(panChange)
    }

    internal fun canPan(
        panIntent: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Boolean {
        updateLayout(containerSize, imageSize, fitMode)
        return viewport.canPan(panIntent)
    }

    internal fun canConsumePan(
        panChange: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Boolean {
        updateLayout(containerSize, imageSize, fitMode)
        return viewport.canConsumePan(panChange)
    }

    internal fun zoomTo(
        targetScale: Float,
        focalPoint: Offset,
        resetOffsetAtMinimum: Boolean,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        updateLayout(containerSize, imageSize, fitMode)
        viewport.zoomTo(
            targetScale = targetScale,
            focalPoint = focalPoint,
            resetOffsetAtMinimum = resetOffsetAtMinimum,
        )
    }

    fun animateZoomToggle(
        coroutineScope: CoroutineScope,
        focalPoint: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        updateLayout(containerSize, imageSize, fitMode)
        viewport.animateZoomToggle(
            coroutineScope = coroutineScope,
            focalPoint = focalPoint,
        )
    }

    fun animateZoomBy(
        coroutineScope: CoroutineScope,
        zoomFactor: Float,
        focalPoint: Offset,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        updateLayout(containerSize, imageSize, fitMode)
        viewport.animateZoomBy(
            coroutineScope = coroutineScope,
            zoomFactor = zoomFactor,
            focalPoint = focalPoint,
        )
    }

    fun animateReset(
        coroutineScope: CoroutineScope,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        updateLayout(containerSize, imageSize, fitMode)
        viewport.animateReset(
            coroutineScope = coroutineScope,
        )
    }

    fun launchFling(
        coroutineScope: CoroutineScope,
        initialVelocity: Offset,
        density: Density,
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ): Offset {
        updateLayout(containerSize, imageSize, fitMode)
        return viewport.launchFling(
            coroutineScope = coroutineScope,
            initialVelocity = initialVelocity,
            density = density,
        )
    }

    fun stopAnimation() = viewport.stopAnimation()

    fun reconcileLayout(
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        updateLayout(containerSize, imageSize, fitMode)
    }

    fun reset() = viewport.reset()

    private fun updateLayout(
        containerSize: IntSize,
        imageSize: IntSize,
        fitMode: ComicPageFitMode,
    ) {
        if (
            containerSize.width <= 0 || containerSize.height <= 0 ||
            imageSize.width <= 0 || imageSize.height <= 0
        ) {
            viewport.updateLayout(ReaderZoomLayout.Empty)
            return
        }
        viewport.updateLayout(
            ReaderZoomLayout(
                viewportSize = containerSize,
                contentSize = renderedImageSize(containerSize, imageSize, fitMode),
            ),
        )
    }

    private fun renderedImageSize(
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
            width = imageSize.width * baseScale,
            height = imageSize.height * baseScale,
        )
    }

    companion object {
        val Saver: Saver<ComicPageViewportState, *> = listSaver(
            save = {
                listOf(
                    2f,
                    it.scale,
                    it.viewport.normalizedOffset.x,
                    it.viewport.normalizedOffset.y,
                )
            },
            restore = { restored ->
                if (restored.size >= 4 && restored[0] == 2f) {
                    ComicPageViewportState(
                        initialScale = restored[1],
                        initialOffsetX = 0f,
                        initialOffsetY = 0f,
                        initialNormalizedOffset = Offset(restored[2], restored[3]),
                    )
                } else {
                    ComicPageViewportState(
                        initialScale = restored[0],
                        initialOffsetX = restored[1],
                        initialOffsetY = restored[2],
                    )
                }
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
