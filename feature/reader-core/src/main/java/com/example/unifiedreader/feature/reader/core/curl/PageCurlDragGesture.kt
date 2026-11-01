package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A turn the finger is driving: the leaf and the point of it the finger picked up. */
interface PageCurlDrag {
    val turn: PageCurlTurn
    val grab: Offset

    /** False if preparing the page failed. No turn may commit before its images are ready. */
    suspend fun awaitReady(): Boolean = true
}

/**
 * Owns horizontal swipes in curl mode. Preparing/settling turns are serialized with taps, while
 * pointer samples keep arriving, so a quick second swipe is retained rather than discarded.
 * Vertical gestures, selection handles and long presses stay with the content. A long press only
 * yields its own gesture; [isSelectingText] supplies the content's actual selection state.
 */
fun Modifier.pageCurlDragGesture(
    enabled: Boolean,
    rightToLeft: Boolean,
    state: PageCurlState,
    gestureScope: CoroutineScope,
    turner: PageCurlTapTurner,
    canStartTurn: (PageCurlTarget) -> Boolean = { true },
    startTurn: suspend (target: PageCurlTarget, down: Offset) -> PageCurlDrag?,
    onTurnSettled: suspend (drag: PageCurlDrag, committed: Boolean) -> Unit,
    onTurnEnded: (drag: PageCurlDrag) -> Unit,
    yieldToLongPress: Boolean = false,
    isSelectingText: () -> Boolean = { false },
    vararg keys: Any?,
): Modifier = composed {
    // Page navigation changes these callbacks, not the lifetime of an in-flight/queued gesture.
    val latestStart by rememberUpdatedState(startTurn)
    val latestCanStart by rememberUpdatedState(canStartTurn)
    val latestSettled by rememberUpdatedState(onTurnSettled)
    val latestEnded by rememberUpdatedState(onTurnEnded)
    val latestSelectingText by rememberUpdatedState(isSelectingText)

    pointerInput(enabled, rightToLeft, state, gestureScope, turner, yieldToLongPress, *keys) {
        val jobs = mutableSetOf<Job>()
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startedBusy = enabled && turner.isBusy
                if (startedBusy) down.consume()
                val velocityTracker = VelocityTracker().apply { addPointerInputChange(down) }
                var yielded = !enabled || latestSelectingText()
                var movedPastSlop = false
                var request: PageCurlDragRequest? = null
                var requestJob: Job? = null

                try {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val multiplePointers = event.changes.count { it.pressed } > 1
                        // Compose represents ACTION_CANCEL as consumed synthetic up events.
                        // Check before consuming our own event, or cancellation looks like a flick.
                        val cancelled = change?.isConsumed == true && request != null
                        if (multiplePointers || change == null || cancelled) {
                            yielded = true
                            requestJob?.cancel()
                        }
                        if (change != null && !cancelled) {
                            velocityTracker.addPointerInputChange(change)
                            val movement = change.position - down.position
                            if (request == null && !yielded) {
                                if (yieldToLongPress && !movedPastSlop &&
                                    change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
                                ) {
                                    yielded = true
                                }
                                movedPastSlop = movedPastSlop || movement.getDistance() > viewConfiguration.touchSlop
                                // Re-check because Android may start selection after the initial down.
                                if (latestSelectingText()) yielded = true
                                val vertical = abs(movement.y) > viewConfiguration.touchSlop &&
                                    abs(movement.y) > abs(movement.x) * 1.5f
                                if (vertical) yielded = true
                                val target = resolvePageCurlTarget(movement.x, rightToLeft, viewConfiguration.touchSlop)
                                if (target != null && !startedBusy && !latestCanStart(target)) yielded = true
                                if (!yielded && target != null) {
                                    val input = PageCurlDragRequest(
                                        target = target,
                                        down = down.position,
                                        start = pageCurlDragStart(down.position, change.position, viewConfiguration.touchSlop),
                                        finger = change.position,
                                    )
                                    request = input
                                    val job = gestureScope.launch(start = CoroutineStart.LAZY) {
                                        turner.withTurn {
                                            val settle = latestSettled
                                            val end = latestEnded
                                            val drag = latestStart(input.target, input.down) ?: return@withTurn
                                            drivePageCurlDrag(input, drag, state, settle, end)
                                        }
                                    }
                                    jobs += job
                                    job.invokeOnCompletion { jobs -= job }
                                    requestJob = job
                                    job.start()
                                }
                            }
                            val maximum = viewConfiguration.maximumFlingVelocity
                            request?.sample?.value = PageCurlDragSample(
                                finger = change.position,
                                released = !change.pressed,
                                horizontalVelocity = if (change.pressed) 0f else velocityTracker.calculateVelocity(
                                    maximumVelocity = Velocity(maximum, maximum),
                                ).x,
                            )
                        }
                        if (startedBusy || request != null) event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                } finally {
                    if (request?.sample?.value?.released != true) requestJob?.cancel()
                }
            }
        } finally {
            // A layout/mode change or disposal must also cancel preparation and restore the page.
            jobs.toList().forEach { it.cancel() }
        }
    }
}

/** Serializes tap and drag turns against the same navigator. */
@Stable
class PageCurlTapTurner {
    private val turns = Mutex()
    var turnHandler: (suspend (PageCurlTarget) -> Boolean)? = null
    val isBusy: Boolean get() = turns.isLocked

    internal suspend fun <T> withTurn(block: suspend () -> T): T = turns.withLock { block() }

    suspend fun turn(target: PageCurlTarget): Boolean = withTurn {
        turnHandler?.invoke(target) ?: false
    }
}
