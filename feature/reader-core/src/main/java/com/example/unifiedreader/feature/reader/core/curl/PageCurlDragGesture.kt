package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A turn the finger is driving: the leaf and the point of it the finger picked up. */
interface PageCurlDrag {
    val turn: PageCurlTurn
    val grab: Offset
}

/**
 * Turns pages with a horizontal drag. Pointer events are watched before the content sees them; once a
 * drag passes the touch slop toward a page, [startTurn] decides whether it curls (null leaves the
 * gesture to the content), and from then on the leaf follows the finger and the content gets nothing.
 * On release the leaf settles on the target or back at rest, [onTurnSettled] runs with which, and
 * [onTurnEnded] always runs last. Vertical drags and multi-touch stay with the content. With
 * [yieldToLongPress], a press held still past the long-press timeout starts text selection: it and
 * every gesture after it stay with the content (dragging the selection handles) until a plain tap,
 * which is also what dismisses the selection.
 */
fun Modifier.pageCurlDragGesture(
    enabled: Boolean,
    rightToLeft: Boolean,
    state: PageCurlState,
    gestureScope: CoroutineScope,
    isTurnActive: () -> Boolean,
    startTurn: (target: PageCurlTarget, down: Offset) -> PageCurlDrag?,
    onTurnSettled: suspend (drag: PageCurlDrag, committed: Boolean) -> Unit,
    onTurnEnded: (drag: PageCurlDrag) -> Unit,
    yieldToLongPress: Boolean = false,
    vararg keys: Any?,
): Modifier = pointerInput(enabled, rightToLeft, state, gestureScope, yieldToLongPress, *keys) {
    var openDrag: PageCurlDrag? = null
    var finishJob: Job? = null
    var selectingText = false

    try {
        awaitEachGesture {
            val firstDown = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val blocked = openDrag != null || finishJob?.isActive == true || isTurnActive()
            if (blocked) {
                firstDown.consume()
            }
            val velocityTracker = VelocityTracker().apply {
                addPointerInputChange(firstDown)
            }
            var accumulatedDrag = Offset.Zero
            var activeDrag: PageCurlDrag? = null
            // Where the finger was when the turn started; the page follows the drag from there.
            var turnStart = firstDown.position
            val startedSelectingText = selectingText
            var yielded = blocked || !enabled || startedSelectingText
            var movedPastSlop = false
            var longPressed = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                if (blocked || activeDrag != null) {
                    event.changes.forEach { change -> change.consume() }
                }
                val change = event.changes.firstOrNull { it.id == firstDown.id }
                    ?: event.changes.firstOrNull()
                if (event.changes.count { it.pressed } > 1 && activeDrag == null) {
                    yielded = true
                }
                val drag = activeDrag
                if (change != null && drag == null) {
                    // A finger held still sends no events, so the hold is judged on the first one after
                    // it, before that event's own movement counts.
                    if (yieldToLongPress && !movedPastSlop &&
                        change.uptimeMillis - firstDown.uptimeMillis > viewConfiguration.longPressTimeoutMillis
                    ) {
                        longPressed = true
                        selectingText = true
                        yielded = true
                    }
                    accumulatedDrag += change.position - change.previousPosition
                    movedPastSlop = movedPastSlop || accumulatedDrag.getDistance() > viewConfiguration.touchSlop
                }
                if (change != null && drag == null && !yielded) {
                    val target = resolvePageCurlTarget(
                        horizontalDrag = accumulatedDrag.x,
                        rightToLeft = rightToLeft,
                        touchSlop = viewConfiguration.touchSlop,
                    )
                    val isMostlyVertical = abs(accumulatedDrag.y) > viewConfiguration.touchSlop &&
                        abs(accumulatedDrag.y) > abs(accumulatedDrag.x)
                    when {
                        isMostlyVertical -> yielded = true
                        target != null -> {
                            val started = startTurn(target, firstDown.position)
                            if (started == null) {
                                yielded = true
                            } else {
                                turnStart = change.position
                                state.snapTo(started.turn.restingEdge(started.grab))
                                activeDrag = started
                                openDrag = started
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    }
                } else if (change != null && drag != null) {
                    state.snapTo(
                        drag.turn.edgeForFinger(
                            grab = drag.grab,
                            start = turnStart,
                            finger = change.position,
                        ),
                    )
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }

                if (event.changes.count { it.pressed } == 1) {
                    event.changes.firstOrNull { it.pressed }?.let(velocityTracker::addPointerInputChange)
                }
                if (event.changes.all { change -> !change.pressed || change.changedToUpIgnoreConsumed() }) {
                    break
                }
            }

            val drag = activeDrag
            if (drag == null) {
                if (startedSelectingText && !movedPastSlop && !longPressed) {
                    selectingText = false
                }
                return@awaitEachGesture
            }
            val maximumVelocity = viewConfiguration.maximumFlingVelocity
            val horizontalVelocity = velocityTracker.calculateVelocity(
                maximumVelocity = Velocity(maximumVelocity, maximumVelocity),
            ).x
            finishJob = gestureScope.launch {
                try {
                    val turn = drag.turn
                    val releasedEdge = state.edge
                    val commit = shouldCommitPageCurl(
                        progress = turn.commitProgress(grab = drag.grab, edge = releasedEdge),
                        velocity = turn.commitVelocity(horizontalVelocity),
                    )
                    val settledEdge = if (commit) turn.committedEdge(drag.grab) else turn.restingEdge(drag.grab)
                    state.animateTo(
                        target = settledEdge,
                        durationMillis = turn.settleDurationMillis(from = releasedEdge, to = settledEdge),
                    )
                    onTurnSettled(drag, commit)
                } finally {
                    openDrag = null
                    finishJob = null
                    onTurnEnded(drag)
                    state.reset()
                }
            }
        }
    } finally {
        // The gesture was torn down mid-turn (keys changed, or the node left the composition).
        val drag = openDrag
        if (drag != null && finishJob?.isActive != true) {
            openDrag = null
            gestureScope.launch {
                onTurnEnded(drag)
                state.reset()
            }
        }
    }
}

/** Lets tap navigation play a curl instead of the content's own page change. */
@Stable
class PageCurlTapTurner {
    var turnHandler: (suspend (PageCurlTarget) -> Boolean)? = null

    /** True when the tap was handled: played as a curl, or ignored because a turn is in progress. */
    suspend fun turn(
        target: PageCurlTarget,
    ): Boolean = turnHandler?.invoke(target) ?: false
}
