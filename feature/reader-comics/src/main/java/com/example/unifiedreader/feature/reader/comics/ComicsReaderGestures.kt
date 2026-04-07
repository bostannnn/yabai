package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

private const val NavigationZoneFraction = 0.25f

internal enum class ReaderTapAction {
    Previous,
    Next,
    ToggleOverlay,
}

internal fun Modifier.comicGestureModifier(
    gestureKey: String,
    containerSize: IntSize,
    imageSize: IntSize,
    viewportState: ComicPageViewportState,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    overlayVisible: Boolean,
    onInteraction: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
): Modifier = fillMaxSize()
    .graphicsLayer {
        scaleX = viewportState.scale
        scaleY = viewportState.scale
        translationX = viewportState.offset.x
        translationY = viewportState.offset.y
    }
    .pointerInput(gestureKey) {
        detectTapGestures(
            onTap = { tapOffset ->
                if (containerSize.width == 0) {
                    return@detectTapGestures
                }
                if (!overlayVisible) {
                    onToggleOverlay()
                    return@detectTapGestures
                }
                when (
                    resolveReaderTapAction(
                        tapX = tapOffset.x,
                        containerWidth = containerSize.width.toFloat(),
                        readingDirection = readingDirection,
                        isZoomed = viewportState.isZoomed,
                    )
                ) {
                    ReaderTapAction.Previous -> {
                        onInteraction()
                        onNavigatePrevious()
                    }
                    ReaderTapAction.Next -> {
                        onInteraction()
                        onNavigateNext()
                    }
                    ReaderTapAction.ToggleOverlay -> {
                        onToggleOverlay()
                    }
                }
            },
            onDoubleTap = {
                onInteraction()
                viewportState.toggleZoom(
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
            },
        )
    }
    .pointerInput(gestureKey, containerSize) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var gestureOngoing = true
            var transformActive = false
            while (gestureOngoing) {
                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                val pressedPointers = event.changes.filter { it.pressed }
                if (pressedPointers.size > 1) {
                    transformActive = true
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        onInteraction()
                        viewportState.onGesture(
                            zoomChange = zoom,
                            panChange = pan,
                            containerSize = containerSize,
                            imageSize = imageSize,
                            fitMode = pageFitMode,
                        )
                        event.changes
                            .filter { it.positionChanged() }
                            .forEach { it.consume() }
                    }
                }
                gestureOngoing = event.changes.any { !it.changedToUpIgnoreConsumed() }
            }
            if (transformActive) {
                onInteraction()
            }
        }
    }
    .then(
        if (viewportState.isZoomed) {
            Modifier.pointerInput(gestureKey, containerSize, imageSize, pageFitMode) {
                detectDragGestures { change, dragAmount ->
                    if (!viewportState.canPan(containerSize, imageSize, pageFitMode)) {
                        return@detectDragGestures
                    }
                    onInteraction()
                    change.consume()
                    viewportState.onPan(
                        panChange = Offset(
                            x = dragAmount.x,
                            y = dragAmount.y,
                        ),
                        containerSize = containerSize,
                        imageSize = imageSize,
                        fitMode = pageFitMode,
                    )
                }
            }
        } else {
            Modifier
        },
    )

internal fun resolveReaderTapAction(
    tapX: Float,
    containerWidth: Float,
    readingDirection: ComicReadingDirection,
    isZoomed: Boolean,
): ReaderTapAction {
    if (containerWidth <= 0f || isZoomed) {
        return ReaderTapAction.ToggleOverlay
    }
    val isLeftZone = tapX <= containerWidth * NavigationZoneFraction
    val isRightZone = tapX >= containerWidth * (1f - NavigationZoneFraction)
    return when {
        isLeftZone && readingDirection == ComicReadingDirection.RTL -> ReaderTapAction.Next
        isLeftZone -> ReaderTapAction.Previous
        isRightZone && readingDirection == ComicReadingDirection.RTL -> ReaderTapAction.Previous
        isRightZone -> ReaderTapAction.Next
        else -> ReaderTapAction.ToggleOverlay
    }
}
