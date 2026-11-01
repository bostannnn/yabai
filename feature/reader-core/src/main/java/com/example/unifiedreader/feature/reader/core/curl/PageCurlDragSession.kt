package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class PageCurlDragSample(
    val finger: Offset,
    val released: Boolean = false,
    val horizontalVelocity: Float = 0f,
)

internal class PageCurlDragRequest(
    val target: PageCurlTarget,
    val down: Offset,
    val start: Offset,
    finger: Offset,
) {
    val sample = MutableStateFlow(PageCurlDragSample(finger))
}

/** Remove only the recognition slop, preserving any movement beyond it in the first event. */
internal fun pageCurlDragStart(down: Offset, finger: Offset, touchSlop: Float): Offset {
    val delta = finger - down
    if (abs(delta.x) <= touchSlop) return finger
    return down + delta * (touchSlop / abs(delta.x))
}

internal suspend fun drivePageCurlDrag(
    request: PageCurlDragRequest,
    drag: PageCurlDrag,
    state: PageCurlState,
    onSettled: suspend (PageCurlDrag, Boolean) -> Unit,
    onEnded: (PageCurlDrag) -> Unit,
) {
    var settled = false
    try {
        if (!drag.awaitReady()) return
        val turn = drag.turn
        // A flick released during loading/another turn still gets a visible animation from rest.
        val releasedBeforeReady = request.sample.value.released
        val release = request.sample.first { sample ->
            state.snapTo(turn.edgeForFinger(drag.grab, request.start, sample.finger))
            sample.released
        }
        val commit = shouldCommitPageCurl(
            progress = turn.commitProgress(drag.grab, state.edge),
            velocity = turn.commitVelocity(release.horizontalVelocity),
        )
        if (releasedBeforeReady) {
            state.snapTo(turn.restingEdge(drag.grab))
            if (commit) {
                state.animateAlong(PageCurlTapTurnDurationMillis) { fraction ->
                    turn.automaticTurnEdge(drag.grab, fraction)
                }
            }
        } else {
            val target = if (commit) turn.committedEdge(drag.grab) else turn.restingEdge(drag.grab)
            state.animateTo(target, turn.settleDurationMillis(state.edge, target))
        }
        onSettled(drag, commit)
        settled = true
    } finally {
        // EPUB preparation moves the live navigator underneath the overlay. Even cancellation
        // during loading must restore it before dropping that overlay.
        withContext(NonCancellable) {
            try {
                if (!settled) onSettled(drag, false)
            } finally {
                onEnded(drag)
                state.reset()
            }
        }
    }
}
