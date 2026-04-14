package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private const val TerminalSwipeHorizontalDominanceRatio = 1.2f

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
        var horizontalDrag = 0f
        var verticalDrag = 0f
        var triggered = false
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == firstDown.id }
                ?: event.changes.firstOrNull()
            if (change != null && isAtLastSpread && !isZoomed && !triggered) {
                val drag = change.position - change.previousPosition
                horizontalDrag += drag.x
                verticalDrag += drag.y
                if (
                    isTerminalForwardSwipe(
                        horizontalDrag = horizontalDrag,
                        verticalDrag = verticalDrag,
                        readingDirection = readingDirection,
                        touchSlop = viewConfiguration.touchSlop,
                    )
                ) {
                    triggered = true
                    onNavigatePastEnd()
                }
            }
            if (event.changes.all { change -> !change.pressed || change.changedToUpIgnoreConsumed() }) {
                break
            }
        }
    }
}

internal fun isTerminalForwardSwipe(
    horizontalDrag: Float,
    verticalDrag: Float,
    readingDirection: ComicReadingDirection,
    touchSlop: Float,
): Boolean {
    if (abs(horizontalDrag) <= touchSlop) return false
    if (abs(horizontalDrag) < abs(verticalDrag) * TerminalSwipeHorizontalDominanceRatio) return false
    return when (readingDirection) {
        ComicReadingDirection.LTR -> horizontalDrag < 0f
        ComicReadingDirection.RTL -> horizontalDrag > 0f
    }
}
