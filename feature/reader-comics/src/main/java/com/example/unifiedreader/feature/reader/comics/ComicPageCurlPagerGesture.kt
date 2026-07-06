package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ComicPageCurlInteraction(
    val target: ComicPageCurlTarget,
    val targetSpreadIndex: Int,
    val currentPage: ImageBitmap,
    val underneathPage: ImageBitmap,
)

internal fun Modifier.comicPageCurlPagerGesture(
    enabled: Boolean,
    currentSpreadIndex: Int,
    spreadCount: Int,
    containerSize: IntSize,
    readingDirection: ComicReadingDirection,
    currentComposite: () -> ImageBitmap?,
    previousComposite: () -> ImageBitmap?,
    nextComposite: () -> ImageBitmap?,
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
            state.reset()
            onInteractionEnded()
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
            var accumulatedDrag = Offset.Zero
            var activeInteraction: ComicPageCurlInteraction? = null
            var activeProgress = 0f
            var progressJob: Job? = null
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
                if (change != null && activeInteraction == null && !yielded) {
                    val drag = change.position - change.previousPosition
                    accumulatedDrag += drag
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
                            val targetSpreadIndex = resolveComicPageCurlTargetSpreadIndex(
                                currentSpreadIndex = currentSpreadIndex,
                                spreadCount = spreadCount,
                                target = target,
                            )
                            val currentPage = currentComposite()
                            val underneathPage = when (target) {
                                ComicPageCurlTarget.Previous -> previousComposite()
                                ComicPageCurlTarget.Next -> nextComposite()
                            }
                            if (targetSpreadIndex == null || currentPage == null || underneathPage == null) {
                                yielded = true
                            } else {
                                state.startDrag(
                                    target = target,
                                    grabY = firstDown.position.y,
                                    containerHeight = containerSize.height.toFloat(),
                                )
                                activeProgress = applyComicPageCurlProgressDelta(
                                    progress = 0f,
                                    dragAmount = accumulatedDrag.x,
                                    containerWidth = containerSize.width.toFloat(),
                                    readingDirection = readingDirection,
                                    target = target,
                                )
                                progressJob?.cancel()
                                progressJob = gestureScope.launch {
                                    state.progress.snapTo(activeProgress)
                                }
                                val interaction = ComicPageCurlInteraction(
                                    target = target,
                                    targetSpreadIndex = targetSpreadIndex,
                                    currentPage = currentPage,
                                    underneathPage = underneathPage,
                                )
                                activeInteraction = interaction
                                interactionOpen = true
                                onInteractionStarted(interaction)
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    }
                } else if (change != null && activeInteraction != null) {
                    val drag = change.position - change.previousPosition
                    activeProgress = applyComicPageCurlProgressDelta(
                        progress = activeProgress,
                        dragAmount = drag.x,
                        containerWidth = containerSize.width.toFloat(),
                        readingDirection = readingDirection,
                        target = activeInteraction.target,
                    )
                    progressJob?.cancel()
                    progressJob = gestureScope.launch {
                        state.progress.snapTo(activeProgress)
                    }
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }

                if (event.changes.all { change -> !change.pressed || change.changedToUpIgnoreConsumed() }) {
                    break
                }
            }

            val interaction = activeInteraction ?: return@awaitEachGesture
            progressJob?.cancel()
            finishJob = gestureScope.launch {
                try {
                    state.progress.snapTo(activeProgress)
                    if (shouldCommitComicPageCurl(progress = activeProgress)) {
                        state.animateToCommitted()
                        onTurnCommitted(interaction.targetSpreadIndex)
                    } else {
                        state.animateToRest()
                    }
                } finally {
                    state.reset()
                    interactionOpen = false
                    finishJob = null
                    onInteractionEnded()
                }
            }
        }
    } finally {
        clearOpenInteraction()
    }
}
