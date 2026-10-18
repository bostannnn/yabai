package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val TerminalSwipeHorizontalDominanceRatio = 1.2f
private val TerminalSwipeMinDistance = 48.dp

internal fun Modifier.comicTerminalSwipeNavigation(
    isAtLastSpread: Boolean,
    isZoomed: Boolean,
    readingDirection: ComicReadingDirection,
    onNavigatePastEnd: () -> Unit,
): Modifier = pointerInput(isAtLastSpread, isZoomed, readingDirection) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val gesture = ComicTerminalSwipeGesture(
            readingDirection = readingDirection,
            minDistance = maxOf(viewConfiguration.touchSlop, TerminalSwipeMinDistance.toPx()),
        )
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == firstDown.id }
                ?: event.changes.firstOrNull()
            gesture.onPointerEvent(
                pressedPointerCount = event.changes.count { it.pressed },
                drag = change?.let { it.position - it.previousPosition } ?: Offset.Zero,
            )
            if (event.changes.all { change -> !change.pressed || change.changedToUpIgnoreConsumed() }) {
                break
            }
        }
        if (isAtLastSpread && !isZoomed && gesture.isForwardSwipeOnRelease()) {
            onNavigatePastEnd()
        }
    }
}

/**
 * A swipe past the last spread, decided when the gesture ends: the first finger of a pinch, or a
 * short drag that the pager would treat as no page turn, must not end the book.
 */
internal class ComicTerminalSwipeGesture(
    private val readingDirection: ComicReadingDirection,
    private val minDistance: Float,
) {
    private var horizontalDrag = 0f
    private var verticalDrag = 0f
    private var sawMultiplePointers = false

    fun onPointerEvent(pressedPointerCount: Int, drag: Offset) {
        if (pressedPointerCount > 1) sawMultiplePointers = true
        horizontalDrag += drag.x
        verticalDrag += drag.y
    }

    fun isForwardSwipeOnRelease(): Boolean = !sawMultiplePointers &&
        isTerminalForwardSwipe(
            horizontalDrag = horizontalDrag,
            verticalDrag = verticalDrag,
            readingDirection = readingDirection,
            minDistance = minDistance,
        )
}

internal fun isTerminalForwardSwipe(
    horizontalDrag: Float,
    verticalDrag: Float,
    readingDirection: ComicReadingDirection,
    minDistance: Float,
): Boolean {
    if (abs(horizontalDrag) <= minDistance) return false
    if (abs(horizontalDrag) < abs(verticalDrag) * TerminalSwipeHorizontalDominanceRatio) return false
    return when (readingDirection) {
        ComicReadingDirection.LTR -> horizontalDrag < 0f
        ComicReadingDirection.RTL -> horizontalDrag > 0f
    }
}
