package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Detects coordinated one-finger pan, pinch zoom, pointer-count handoff, and release velocity. */
enum class ReaderPanAxis {
    Horizontal,
    Vertical,
}

suspend fun PointerInputScope.detectReaderZoomGestures(
    viewportCenter: () -> Offset,
    canPan: (Offset) -> Boolean,
    onPointerDown: () -> Unit,
    onTransformStart: () -> Unit,
    onPan: (Offset) -> Offset,
    onPanRemainder: (
        axis: ReaderPanAxis,
        consumed: Offset,
        available: Offset,
    ) -> Unit = { _, _, _ -> },
    onTransform: (centroid: Offset, panChange: Offset, zoomChange: Float) -> Unit,
    onTransformEnd: (velocity: Offset, panAxis: ReaderPanAxis?) -> Unit,
) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        onPointerDown()
        val velocityTracker = VelocityTracker().apply {
            addPointerInputChange(firstDown)
        }
        var accumulatedZoom = 1f
        var accumulatedPan = Offset.Zero
        var gestureMode = ReaderZoomGestureMode.Undecided
        var panAxis = ReaderPanAxis.Horizontal
        var previousPointerCount = 1
        var lastActivePointerCount = 1

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Main)
            val pressedPointers = event.changes.filter { it.pressed }
            val pointerCount = pressedPointers.size
            if (pointerCount > 0 && pointerCount != previousPointerCount) {
                velocityTracker.resetTracking()
                pressedPointers.firstOrNull()?.let(velocityTracker::addPointerInputChange)
                accumulatedZoom = 1f
                accumulatedPan = Offset.Zero
            }
            if (pointerCount > 0) {
                lastActivePointerCount = pointerCount
                previousPointerCount = pointerCount
            }

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            if (gestureMode == ReaderZoomGestureMode.Pan && pointerCount > 1) {
                gestureMode = ReaderZoomGestureMode.Transform
            }
            if (gestureMode == ReaderZoomGestureMode.Undecided) {
                accumulatedZoom *= zoomChange
                accumulatedPan += panChange
                val zoomMotion = abs(1f - accumulatedZoom) *
                    event.calculateCentroidSize(useCurrent = false)
                val panMotion = accumulatedPan.getDistance()
                when {
                    zoomMotion > viewConfiguration.touchSlop -> {
                        gestureMode = ReaderZoomGestureMode.Transform
                        onTransformStart()
                    }
                    panMotion > viewConfiguration.touchSlop -> {
                        panAxis = accumulatedPan.primaryAxis
                        gestureMode = when {
                            pointerCount > 1 -> ReaderZoomGestureMode.Transform
                            canPan(accumulatedPan.primaryAxisDelta()) -> ReaderZoomGestureMode.Pan
                            else -> ReaderZoomGestureMode.Yielded
                        }
                        if (gestureMode.isActive) {
                            onTransformStart()
                        }
                    }
                }
            }

            when (gestureMode) {
                ReaderZoomGestureMode.Pan -> {
                    if (panChange != Offset.Zero) {
                        val consumed = onPan(panChange)
                        onPanRemainder(
                            panAxis,
                            consumed,
                            panChange - consumed,
                        )
                    }
                }
                ReaderZoomGestureMode.Transform -> {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                            .takeIf { it.isFinite }
                            ?: viewportCenter()
                        onTransform(
                            centroid,
                            panChange,
                            zoomChange,
                        )
                    }
                }
                ReaderZoomGestureMode.Undecided,
                ReaderZoomGestureMode.Yielded,
                -> Unit
            }
            if (gestureMode.isActive) {
                event.changes
                    .filter { it.positionChanged() }
                    .forEach { it.consume() }
            }

            if (pointerCount == 1) {
                pressedPointers.firstOrNull()?.let(velocityTracker::addPointerInputChange)
            }
            if (event.changes.all { !it.pressed || it.changedToUpIgnoreConsumed() }) break
        }

        if (gestureMode.isActive && lastActivePointerCount == 1) {
            val maximumVelocity = viewConfiguration.maximumFlingVelocity
            val minimumVelocity = 50.dp.toPx()
            val velocity = velocityTracker.calculateVelocity(
                maximumVelocity = Velocity(maximumVelocity, maximumVelocity),
            )
            onTransformEnd(
                Offset(
                    x = velocity.x.takeIf { abs(it) >= minimumVelocity } ?: 0f,
                    y = velocity.y.takeIf { abs(it) >= minimumVelocity } ?: 0f,
                ),
                panAxis.takeIf { gestureMode == ReaderZoomGestureMode.Pan },
            )
        }
    }
}

private enum class ReaderZoomGestureMode {
    Undecided,
    Yielded,
    Pan,
    Transform,
}

private val ReaderZoomGestureMode.isActive: Boolean
    get() = this == ReaderZoomGestureMode.Pan || this == ReaderZoomGestureMode.Transform

private val Offset.isFinite: Boolean
    get() = x.isFinite() && y.isFinite()

private val Offset.primaryAxis: ReaderPanAxis
    get() = if (abs(x) >= abs(y)) ReaderPanAxis.Horizontal else ReaderPanAxis.Vertical

private fun Offset.primaryAxisDelta(): Offset = if (primaryAxis == ReaderPanAxis.Horizontal) {
    Offset(x = x, y = 0f)
} else {
    Offset(x = 0f, y = y)
}
