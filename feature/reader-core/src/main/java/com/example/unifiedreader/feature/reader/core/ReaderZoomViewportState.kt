package com.example.unifiedreader.feature.reader.core

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val ZoomEpsilon = 0.01f
private const val PanEpsilon = 0.5f
private const val ZoomAnimationDurationMillis = 220

@Immutable
data class ReaderZoomLayout(
    val viewportSize: IntSize,
    val contentSize: Size,
) {
    val isReady: Boolean
        get() = viewportSize.width > 0 && viewportSize.height > 0 &&
            contentSize.width > 0f && contentSize.height > 0f

    fun maxOffsets(scale: Float): Offset {
        if (!isReady) return Offset.Zero
        return Offset(
            x = ((contentSize.width * scale - viewportSize.width) / 2f).coerceAtLeast(0f),
            y = ((contentSize.height * scale - viewportSize.height) / 2f).coerceAtLeast(0f),
        )
    }

    companion object {
        val Empty = ReaderZoomLayout(IntSize.Zero, Size.Zero)
    }
}

/**
 * Zoom and pan state whose offsets are always expressed in viewport pixels.
 *
 * Callers update [ReaderZoomLayout] when their fitted content geometry changes. Keeping the
 * transform itself here gives each reader the same focal zoom, animation, fling, and normalized
 * position preservation while allowing gesture handoff to remain reader-specific.
 */
@Stable
class ReaderZoomViewportState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    initialNormalizedOffset: Offset? = null,
    private val minimumScale: Float = 1f,
    private val maximumScale: Float = 4f,
    private val doubleTapScale: Float = 2f,
) {
    private var animationJob: Job? = null
    private var layout: ReaderZoomLayout = ReaderZoomLayout.Empty
    private var pendingNormalizedOffset: Offset? = initialNormalizedOffset?.coerceToUnitBounds()

    var scale by mutableFloatStateOf(initialScale.coerceIn(minimumScale, maximumScale))
        private set

    var offset by mutableStateOf(initialOffset)
        private set

    val isZoomed: Boolean
        get() = scale > minimumScale + ZoomEpsilon

    val normalizedOffset: Offset
        get() = pendingNormalizedOffset ?: normalizedOffset(
            offset = offset,
            maxOffsets = layout.maxOffsets(scale),
        )

    fun updateLayout(newLayout: ReaderZoomLayout) {
        if (layout == newLayout) return
        stopAnimation()
        val oldLayout = layout
        val normalized = pendingNormalizedOffset ?: normalizedOffset(
            offset = offset,
            maxOffsets = oldLayout.maxOffsets(scale),
        )
        layout = newLayout
        if (!newLayout.isReady) {
            if (oldLayout.isReady || pendingNormalizedOffset != null) {
                pendingNormalizedOffset = normalized
            }
            return
        }
        offset = if (oldLayout.isReady || pendingNormalizedOffset != null) {
            offsetFromNormalized(normalized, newLayout.maxOffsets(scale))
        } else {
            constrainOffset(offset, newLayout.maxOffsets(scale))
        }
        pendingNormalizedOffset = null
    }

    fun onGesture(
        centroid: Offset,
        zoomChange: Float,
        panChange: Offset,
    ) {
        stopAnimation()
        val nextScale = (scale * zoomChange).coerceIn(minimumScale, maximumScale)
        val safeCentroid = centroid.takeIf { it.isFinite } ?: layout.viewportSize.center
        val requestedOffset = offsetForFocalZoom(
            currentOffset = offset,
            oldScale = scale,
            newScale = nextScale,
            focalPoint = safeCentroid,
            viewportSize = layout.viewportSize,
        ) + panChange
        setTransform(nextScale, requestedOffset)
    }

    fun onPan(
        panChange: Offset,
    ): Offset {
        val previousOffset = offset
        setTransform(scale, offset + panChange)
        return offset - previousOffset
    }

    fun canPan(panIntent: Offset): Boolean {
        val maxOffsets = layout.maxOffsets(scale)
        return if (abs(panIntent.x) >= abs(panIntent.y)) {
            maxOffsets.x > PanEpsilon
        } else {
            maxOffsets.y > PanEpsilon
        }
    }

    fun canConsumePan(panChange: Offset): Boolean {
        if (panChange == Offset.Zero) return false
        val constrained = constrainOffset(offset + panChange, layout.maxOffsets(scale))
        val consumed = constrained - offset
        return abs(consumed.x) > PanEpsilon || abs(consumed.y) > PanEpsilon
    }

    fun zoomTo(
        targetScale: Float,
        focalPoint: Offset,
        resetOffsetAtMinimum: Boolean,
    ) {
        val nextScale = targetScale.coerceIn(minimumScale, maximumScale)
        val requestedOffset = if (resetOffsetAtMinimum && nextScale <= minimumScale + ZoomEpsilon) {
            Offset.Zero
        } else {
            offsetForFocalZoom(offset, scale, nextScale, focalPoint, layout.viewportSize)
        }
        setTransform(nextScale, requestedOffset)
    }

    fun animateZoomToggle(
        coroutineScope: CoroutineScope,
        focalPoint: Offset,
    ) {
        animateZoomTo(
            coroutineScope = coroutineScope,
            targetScale = if (isZoomed) minimumScale else doubleTapScale,
            focalPoint = focalPoint,
            resetOffsetAtMinimum = isZoomed,
        )
    }

    fun animateZoomBy(
        coroutineScope: CoroutineScope,
        zoomFactor: Float,
        focalPoint: Offset,
    ) {
        val targetScale = (scale * zoomFactor).coerceIn(minimumScale, maximumScale)
        animateZoomTo(
            coroutineScope = coroutineScope,
            targetScale = targetScale,
            focalPoint = focalPoint,
            resetOffsetAtMinimum = targetScale <= minimumScale + ZoomEpsilon,
        )
    }

    fun animateReset(
        coroutineScope: CoroutineScope,
    ) {
        animateZoomTo(
            coroutineScope = coroutineScope,
            targetScale = minimumScale,
            focalPoint = layout.viewportSize.center,
            resetOffsetAtMinimum = true,
        )
    }

    fun launchFling(
        coroutineScope: CoroutineScope,
        initialVelocity: Offset,
        density: Density,
    ): Offset {
        val eligibleVelocity = Offset(
            x = initialVelocity.x.takeIf {
                canConsumePan(Offset(x = it, y = 0f))
            } ?: 0f,
            y = initialVelocity.y.takeIf {
                canConsumePan(Offset(x = 0f, y = it))
            } ?: 0f,
        )
        if (eligibleVelocity == Offset.Zero) return Offset.Zero
        stopAnimation()
        animationJob = coroutineScope.launch {
            var previous = Offset.Zero
            AnimationState(
                typeConverter = Offset.VectorConverter,
                initialValue = Offset.Zero,
                initialVelocity = eligibleVelocity,
            ).animateDecay(splineBasedDecay(density)) {
                val consumedPan = onPan(value - previous)
                previous = value
                if (abs(consumedPan.x) <= PanEpsilon && abs(consumedPan.y) <= PanEpsilon) {
                    cancelAnimation()
                }
            }
        }
        return eligibleVelocity
    }

    fun reset() {
        stopAnimation()
        pendingNormalizedOffset = null
        scale = minimumScale
        offset = Offset.Zero
    }

    fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private fun animateZoomTo(
        coroutineScope: CoroutineScope,
        targetScale: Float,
        focalPoint: Offset,
        resetOffsetAtMinimum: Boolean,
    ) {
        if (!layout.isReady) return
        stopAnimation()
        val startScale = scale
        val startOffset = offset
        val resolvedTargetScale = targetScale.coerceIn(minimumScale, maximumScale)
        val resolvedTargetOffset = if (
            resetOffsetAtMinimum && resolvedTargetScale <= minimumScale + ZoomEpsilon
        ) {
            Offset.Zero
        } else {
            constrainOffset(
                offsetForFocalZoom(
                    currentOffset = startOffset,
                    oldScale = startScale,
                    newScale = resolvedTargetScale,
                    focalPoint = focalPoint,
                    viewportSize = layout.viewportSize,
                ),
                layout.maxOffsets(resolvedTargetScale),
            )
        }
        animationJob = coroutineScope.launch {
            AnimationState(initialValue = 0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ZoomAnimationDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) {
                setTransform(
                    requestedScale = lerp(startScale, resolvedTargetScale, value),
                    requestedOffset = Offset(
                        x = lerp(startOffset.x, resolvedTargetOffset.x, value),
                        y = lerp(startOffset.y, resolvedTargetOffset.y, value),
                    ),
                )
            }
        }
    }

    private fun setTransform(
        requestedScale: Float,
        requestedOffset: Offset,
    ) {
        pendingNormalizedOffset = null
        scale = requestedScale.coerceIn(minimumScale, maximumScale)
        offset = constrainOffset(requestedOffset, layout.maxOffsets(scale))
    }
}

private fun offsetForFocalZoom(
    currentOffset: Offset,
    oldScale: Float,
    newScale: Float,
    focalPoint: Offset,
    viewportSize: IntSize,
): Offset {
    if (oldScale <= 0f || viewportSize.width <= 0 || viewportSize.height <= 0) {
        return currentOffset
    }
    val scaleRatio = newScale / oldScale
    val focalFromCenter = focalPoint - viewportSize.center
    return currentOffset * scaleRatio + focalFromCenter * (1f - scaleRatio)
}

private fun constrainOffset(requestedOffset: Offset, maxOffsets: Offset): Offset = Offset(
    x = requestedOffset.x.coerceIn(-maxOffsets.x.coerceAtLeast(0f), maxOffsets.x.coerceAtLeast(0f)),
    y = requestedOffset.y.coerceIn(-maxOffsets.y.coerceAtLeast(0f), maxOffsets.y.coerceAtLeast(0f)),
)

private val IntSize.center: Offset
    get() = Offset(width / 2f, height / 2f)

private val Offset.isFinite: Boolean
    get() = x.isFinite() && y.isFinite()

private fun Offset.coerceToUnitBounds(): Offset = Offset(
    x = x.coerceIn(-1f, 1f),
    y = y.coerceIn(-1f, 1f),
)

private fun normalizedOffset(offset: Offset, maxOffsets: Offset): Offset = Offset(
    x = if (maxOffsets.x > PanEpsilon) offset.x / maxOffsets.x else 0f,
    y = if (maxOffsets.y > PanEpsilon) offset.y / maxOffsets.y else 0f,
).coerceToUnitBounds()

private fun offsetFromNormalized(normalized: Offset, maxOffsets: Offset): Offset = Offset(
    x = normalized.x * maxOffsets.x,
    y = normalized.y * maxOffsets.y,
)

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
