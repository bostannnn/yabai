package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.core.detectReaderZoomGestures
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

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
    animationScope: CoroutineScope,
    nestedScrollDispatcher: NestedScrollDispatcher,
    onInteraction: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
): Modifier = fillMaxSize()
    .clipToBounds()
    .nestedScroll(ReaderZoomNestedScrollConnection, nestedScrollDispatcher)
    .pointerInput(gestureKey, containerSize, imageSize, readingDirection, pageFitMode) {
        detectTapGestures(
            onTap = { tapOffset ->
                if (containerSize.width == 0) {
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
            onDoubleTap = { tapOffset ->
                onInteraction()
                viewportState.animateZoomToggle(
                    coroutineScope = animationScope,
                    focalPoint = tapOffset,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
            },
        )
    }
    .pointerInput(gestureKey, containerSize, imageSize, pageFitMode) {
        detectReaderZoomGestures(
            viewportCenter = { containerSize.center },
            canPan = { panIntent ->
                viewportState.canPan(
                    panIntent = panIntent,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
            },
            onPointerDown = viewportState::stopAnimation,
            onTransformStart = onInteraction,
            onPan = { panChange ->
                viewportState.onPan(
                    panChange = panChange,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
            },
            onTransform = { centroid, panChange, zoomChange ->
                viewportState.onGesture(
                    centroid = centroid,
                    zoomChange = zoomChange,
                    panChange = panChange,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
            },
            onTransformEnd = { flingVelocity, _ ->
                viewportState.launchFling(
                    coroutineScope = animationScope,
                    initialVelocity = flingVelocity,
                    density = this,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
            },
        )
    }
    .semantics {
        stateDescription = "${(viewportState.scale * 100f).roundToInt()}% zoom"
        customActions = listOf(
            CustomAccessibilityAction(label = "Zoom in") {
                viewportState.animateZoomBy(
                    coroutineScope = animationScope,
                    zoomFactor = 1.5f,
                    focalPoint = containerSize.center,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
                true
            },
            CustomAccessibilityAction(label = "Zoom out") {
                viewportState.animateZoomBy(
                    coroutineScope = animationScope,
                    zoomFactor = 1f / 1.5f,
                    focalPoint = containerSize.center,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
                true
            },
            CustomAccessibilityAction(label = "Reset zoom") {
                viewportState.animateReset(
                    coroutineScope = animationScope,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                )
                true
            },
        )
    }
    .graphicsLayer {
        scaleX = viewportState.scale
        scaleY = viewportState.scale
        translationX = viewportState.offset.x
        translationY = viewportState.offset.y
    }

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

private val IntSize.center: Offset
    get() = Offset(width / 2f, height / 2f)

private object ReaderZoomNestedScrollConnection : NestedScrollConnection
