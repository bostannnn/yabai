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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ComicPageCurlInteraction(
    val target: ComicPageCurlTarget,
    val targetSpreadIndex: Int,
    val paperBounds: ComicRasterRect,
    val renderBounds: ComicRasterRect,
    val currentPage: ImageBitmap,
    val underneathPage: ImageBitmap,
)

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
                            val currentCurlComposite = currentComposite()
                            val underneathCurlComposite = when (target) {
                                ComicPageCurlTarget.Previous -> previousComposite()
                                ComicPageCurlTarget.Next -> nextComposite()
                            }
                            val sheetGeometry = currentCurlComposite?.layout?.let { layout ->
                                resolveComicPageCurlSheetGeometry(
                                    layout = layout,
                                    readingDirection = readingDirection,
                                    target = target,
                                )
                            }
                            val transitionPolicy = resolveComicPageCurlTransitionPolicy(
                                currentLayout = currentCurlComposite?.layout,
                                targetLayout = underneathCurlComposite?.layout,
                                readingDirection = readingDirection,
                                target = target,
                            )
                            when (
                                resolveComicPageCurlGestureStartPolicy(
                                    targetSpreadIndex = targetSpreadIndex,
                                    hasCurrentComposite = currentCurlComposite != null,
                                    hasUnderneathComposite = underneathCurlComposite != null,
                                    sheetBounds = sheetGeometry?.paperBounds,
                                    downPosition = firstDown.position,
                                    transitionPolicy = transitionPolicy,
                                )
                            ) {
                                ComicPageCurlGestureStartPolicy.YieldToPager -> yielded = true
                                ComicPageCurlGestureStartPolicy.StartCurl -> {
                                    val resolvedCurrentComposite = checkNotNull(currentCurlComposite)
                                    val resolvedUnderneathComposite = checkNotNull(underneathCurlComposite)
                                    val resolvedSheetGeometry = checkNotNull(sheetGeometry)
                                    val resolvedTargetSpreadIndex = checkNotNull(targetSpreadIndex)
                                    state.startDrag(
                                        target = target,
                                        grabY = firstDown.position.y - resolvedSheetGeometry.paperBounds.top,
                                        containerHeight = resolvedSheetGeometry.paperBounds.height.toFloat(),
                                    )
                                    activeProgress = applyComicPageCurlProgressDelta(
                                        progress = 0f,
                                        dragAmount = accumulatedDrag.x,
                                        containerWidth = resolvedSheetGeometry.paperBounds.width.toFloat(),
                                        readingDirection = readingDirection,
                                        target = target,
                                    )
                                    progressJob?.cancel()
                                    progressJob = gestureScope.launch {
                                        state.progress.snapTo(activeProgress)
                                    }
                                    val interaction = ComicPageCurlInteraction(
                                        target = target,
                                        targetSpreadIndex = resolvedTargetSpreadIndex,
                                        paperBounds = resolvedSheetGeometry.paperBounds,
                                        renderBounds = resolvedSheetGeometry.renderBounds,
                                        currentPage = resolvedCurrentComposite.image,
                                        underneathPage = resolvedUnderneathComposite.image,
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
                    }
                } else if (change != null && activeInteraction != null) {
                    val drag = change.position - change.previousPosition
                    activeProgress = applyComicPageCurlProgressDelta(
                        progress = activeProgress,
                        dragAmount = drag.x,
                        containerWidth = activeInteraction.paperBounds.width.toFloat(),
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
            val progressVelocity = comicPageCurlProgressVelocity(
                horizontalVelocity = horizontalVelocity,
                containerWidth = interaction.paperBounds.width.toFloat(),
                readingDirection = readingDirection,
                target = interaction.target,
            )
            progressJob?.cancel()
            finishJob = gestureScope.launch {
                try {
                    state.progress.snapTo(activeProgress)
                    if (shouldCommitComicPageCurl(progress = activeProgress, velocity = progressVelocity)) {
                        state.animateToCommitted()
                        onTurnCommitted(interaction.targetSpreadIndex)
                    } else {
                        state.animateToRest()
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
