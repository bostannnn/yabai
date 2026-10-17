package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class ComicPageCurlGestureStartPolicy {
    StartCurl,
    YieldToPager,
}

internal fun resolveComicPageCurlGestureStartPolicy(
    targetSpreadIndex: Int?,
    hasCurrentComposite: Boolean,
    hasUnderneathComposite: Boolean,
    sheetBounds: ComicRasterRect?,
    downPosition: Offset,
    transitionPolicy: ComicPageCurlTransitionPolicy = ComicPageCurlTransitionPolicy.UseCurl,
): ComicPageCurlGestureStartPolicy {
    if (targetSpreadIndex == null) return ComicPageCurlGestureStartPolicy.YieldToPager
    if (transitionPolicy != ComicPageCurlTransitionPolicy.UseCurl) {
        return ComicPageCurlGestureStartPolicy.YieldToPager
    }
    if (!hasCurrentComposite || !hasUnderneathComposite || sheetBounds?.hasArea != true) {
        return ComicPageCurlGestureStartPolicy.YieldToPager
    }
    return if (sheetBounds.contains(downPosition)) {
        ComicPageCurlGestureStartPolicy.StartCurl
    } else {
        ComicPageCurlGestureStartPolicy.YieldToPager
    }
}

internal fun Modifier.comicPageCurlPagerGesture(
    enabled: Boolean,
    currentSpreadIndex: Int,
    spreadCount: Int,
    containerSize: IntSize,
    readingDirection: ComicReadingDirection,
    currentComposite: () -> ComicPageCurlComposite?,
    previousComposite: () -> ComicPageCurlComposite?,
    nextComposite: () -> ComicPageCurlComposite?,
    isInteractionActive: () -> Boolean,
    state: ComicPageCurlState,
    gestureScope: CoroutineScope,
    onInteractionStarted: (ComicPageCurlInteraction) -> Unit,
    onInteractionEnded: () -> Unit,
    onTurnCommitted: suspend (targetSpreadIndex: Int) -> Unit,
): Modifier = pointerInput(
    enabled,
    currentSpreadIndex,
    spreadCount,
    containerSize,
    readingDirection,
    state,
    gestureScope,
) {
    var interactionOpen = false
    var finishJob: Job? = null
    fun clearOpenInteraction() {
        if (!interactionOpen) return
        if (finishJob?.isActive == true) return
        interactionOpen = false
        gestureScope.launch {
            onInteractionEnded()
            state.reset()
        }
    }

    try {
        awaitEachGesture {
            val firstDown = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val blockedByOpenInteraction = interactionOpen ||
                finishJob?.isActive == true ||
                isInteractionActive()
            if (blockedByOpenInteraction) {
                firstDown.consume()
            }
            val velocityTracker = VelocityTracker().apply {
                addPointerInputChange(firstDown)
            }
            var accumulatedDrag = Offset.Zero
            var activeInteraction: ComicPageCurlInteraction? = null
            var yielded = blockedByOpenInteraction

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                if (blockedByOpenInteraction || activeInteraction != null) {
                    event.changes.forEach { change -> change.consume() }
                }
                val change = event.changes.firstOrNull { it.id == firstDown.id }
                    ?: event.changes.firstOrNull()
                if (event.changes.count { it.pressed } > 1 && activeInteraction == null) {
                    yielded = true
                }
                val interaction = activeInteraction
                if (change != null && interaction == null && !yielded) {
                    accumulatedDrag += change.position - change.previousPosition
                    val target = resolveComicPageCurlTarget(
                        horizontalDrag = accumulatedDrag.x,
                        readingDirection = readingDirection,
                        touchSlop = viewConfiguration.touchSlop,
                    )
                    val isMostlyVertical = abs(accumulatedDrag.y) > viewConfiguration.touchSlop &&
                        abs(accumulatedDrag.y) > abs(accumulatedDrag.x)
                    when {
                        !enabled ||
                            containerSize.width <= 0 ||
                            containerSize.height <= 0 ||
                            isMostlyVertical -> {
                            yielded = true
                        }
                        target != null -> {
                            val started = startComicPageCurlInteraction(
                                target = target,
                                currentSpreadIndex = currentSpreadIndex,
                                spreadCount = spreadCount,
                                readingDirection = readingDirection,
                                currentComposite = currentComposite(),
                                adjacentComposite = when (target) {
                                    ComicPageCurlTarget.Previous -> previousComposite()
                                    ComicPageCurlTarget.Next -> nextComposite()
                                },
                                downPosition = firstDown.position,
                            )
                            if (started == null) {
                                yielded = true
                            } else {
                                state.snapTo(
                                    started.turn.edgeForFinger(
                                        grab = started.grab,
                                        down = firstDown.position,
                                        finger = change.position,
                                    ),
                                )
                                activeInteraction = started
                                interactionOpen = true
                                onInteractionStarted(started)
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    }
                } else if (change != null && interaction != null) {
                    state.snapTo(
                        interaction.turn.edgeForFinger(
                            grab = interaction.grab,
                            down = firstDown.position,
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

            val interaction = activeInteraction ?: return@awaitEachGesture
            val maximumVelocity = viewConfiguration.maximumFlingVelocity
            val horizontalVelocity = velocityTracker.calculateVelocity(
                maximumVelocity = Velocity(maximumVelocity, maximumVelocity),
            ).x
            finishJob = gestureScope.launch {
                try {
                    val turn = interaction.turn
                    val releasedEdge = state.edge
                    val commit = shouldCommitComicPageCurl(
                        progress = turn.commitProgress(grab = interaction.grab, edge = releasedEdge),
                        velocity = turn.commitVelocity(horizontalVelocity),
                    )
                    val settledEdge = if (commit) {
                        turn.committedEdge(interaction.grab)
                    } else {
                        turn.restingEdge(interaction.grab)
                    }
                    state.animateTo(
                        target = settledEdge,
                        durationMillis = turn.settleDurationMillis(from = releasedEdge, to = settledEdge),
                    )
                    if (commit) {
                        onTurnCommitted(interaction.targetSpreadIndex)
                    }
                } finally {
                    interactionOpen = false
                    finishJob = null
                    onInteractionEnded()
                    state.reset()
                }
            }
        }
    } finally {
        clearOpenInteraction()
    }
}

/** Resolves a curl turn for [target], or null when the pager should handle it instead. */
internal fun startComicPageCurlInteraction(
    target: ComicPageCurlTarget,
    currentSpreadIndex: Int,
    spreadCount: Int,
    readingDirection: ComicReadingDirection,
    currentComposite: ComicPageCurlComposite?,
    adjacentComposite: ComicPageCurlComposite?,
    downPosition: Offset?,
): ComicPageCurlInteraction? {
    val targetSpreadIndex = resolveComicPageCurlTargetSpreadIndex(
        currentSpreadIndex = currentSpreadIndex,
        spreadCount = spreadCount,
        target = target,
    )
    val turn = if (currentComposite != null && adjacentComposite != null) {
        resolveComicPageCurlTurn(
            currentLayout = currentComposite.layout,
            targetLayout = adjacentComposite.layout,
            readingDirection = readingDirection,
            target = target,
        )
    } else {
        null
    }
    val startPolicy = resolveComicPageCurlGestureStartPolicy(
        targetSpreadIndex = targetSpreadIndex,
        hasCurrentComposite = currentComposite != null,
        hasUnderneathComposite = adjacentComposite != null,
        sheetBounds = turn?.paperBounds,
        downPosition = downPosition ?: turn?.paperBounds?.center() ?: Offset.Zero,
        transitionPolicy = if (turn != null) {
            ComicPageCurlTransitionPolicy.UseCurl
        } else {
            ComicPageCurlTransitionPolicy.YieldToPager
        },
    )
    if (startPolicy != ComicPageCurlGestureStartPolicy.StartCurl) return null
    val resolvedTurn = checkNotNull(turn)
    val grabY = downPosition?.y ?: (
        resolvedTurn.sheet.top +
            (resolvedTurn.sheet.bottom - resolvedTurn.sheet.top) * ComicPageCurlTapGrabFraction
        )
    return buildComicPageCurlInteraction(
        turn = resolvedTurn,
        targetSpreadIndex = checkNotNull(targetSpreadIndex),
        grab = resolvedTurn.grabPoint(grabY),
        currentPage = checkNotNull(currentComposite).image,
        adjacentPage = checkNotNull(adjacentComposite).image,
    )
}

private fun ComicRasterRect.center(): Offset = Offset(
    x = left + width / 2f,
    y = top + height / 2f,
)
