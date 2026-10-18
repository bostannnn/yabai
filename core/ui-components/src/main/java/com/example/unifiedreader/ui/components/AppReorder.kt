package com.example.unifiedreader.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a short vertical list laid out in a plain `Column`. Each item gets
 * [appReorderableItem]; the part the user grabs gets [appReorderHandle]. When the dragged item's
 * centre crosses a neighbour's centre, [onMove] is called with the two indices and the caller
 * reorders its list; the item under the finger follows the pointer and stays there across the
 * swap. Keep the item composables keyed by a stable id (`key(item.id) { ... }`) so they survive
 * the reorder, and apply each move to a list held in local state that the rows render from:
 * a fast drag reports several moves within one frame, before any recomposition, so a list
 * captured by the lambda would be stale by the second move.
 */
@Composable
fun rememberAppReorderState(onMove: (fromIndex: Int, toIndex: Int) -> Unit): AppReorderState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember { AppReorderState { from, to -> currentOnMove(from, to) } }
}

@Stable
class AppReorderState internal constructor(
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var draggingIndex: Int? by mutableStateOf(null)
        private set
    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    private val boundsByIndex = mutableMapOf<Int, ReorderItemBounds>()

    internal fun updateBounds(index: Int, top: Float, height: Float) {
        // The dragged item is translated on screen; its resting bounds are predicted in
        // [onDrag] instead of read back from layout.
        if (index == draggingIndex) return
        boundsByIndex[index] = ReorderItemBounds(top = top, height = height)
    }

    internal fun onDragStart(index: Int) {
        draggingIndex = index
        dragOffset = 0f
    }

    internal fun onDrag(deltaY: Float) {
        val index = draggingIndex ?: return
        dragOffset += deltaY
        val bounds = boundsByIndex[index] ?: return
        val target = resolveReorderTarget(
            index = index,
            draggedCenter = bounds.center + dragOffset,
            boundsByIndex = boundsByIndex,
        )
        if (target == index) return
        val neighbour = boundsByIndex[target] ?: return
        // After the swap the dragged item rests where the neighbour's edge was; shift the
        // offset by the same amount so it does not jump under the finger.
        val newTop = if (target > index) bounds.top + neighbour.height else bounds.top - neighbour.height
        val newBounds = ReorderItemBounds(top = newTop, height = bounds.height)
        dragOffset -= newBounds.center - bounds.center
        boundsByIndex[target] = newBounds
        boundsByIndex[index] = if (target > index) {
            ReorderItemBounds(top = bounds.top, height = neighbour.height)
        } else {
            ReorderItemBounds(top = newTop + bounds.height, height = neighbour.height)
        }
        draggingIndex = target
        onMove(index, target)
    }

    internal fun onDragEnd() {
        draggingIndex = null
        dragOffset = 0f
    }
}

internal data class ReorderItemBounds(val top: Float, val height: Float) {
    val center: Float get() = top + height / 2f
}

/**
 * Which index the item at [index] should move to given its dragged centre: one step up when
 * it has crossed the centre of the item above, one step down when it has crossed the centre of
 * the item below, otherwise [index] itself. Only adjacent moves are reported, so a fast drag
 * walks the list one swap per call.
 */
internal fun resolveReorderTarget(
    index: Int,
    draggedCenter: Float,
    boundsByIndex: Map<Int, ReorderItemBounds>,
): Int {
    val above = boundsByIndex[index - 1]
    if (above != null && draggedCenter < above.center) return index - 1
    val below = boundsByIndex[index + 1]
    if (below != null && draggedCenter > below.center) return index + 1
    return index
}

/** Marks a `Column` child as item [index] of [state]: it reports its bounds and follows the drag. */
@Composable
fun Modifier.appReorderableItem(state: AppReorderState, index: Int): Modifier {
    val dragging = state.draggingIndex == index
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationY = if (dragging) state.dragOffset else 0f }
        .onGloballyPositioned { coordinates ->
            state.updateBounds(
                index = index,
                top = coordinates.positionInParent().y,
                height = coordinates.size.height.toFloat(),
            )
        }
}

/** The grab area of item [index]: vertical drags here reorder the list. */
@Composable
fun Modifier.appReorderHandle(state: AppReorderState, index: Int): Modifier {
    val currentIndex by rememberUpdatedState(index)
    return this.pointerInput(state) {
        detectDragGestures(
            onDragStart = { state.onDragStart(currentIndex) },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
        ) { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount.y)
        }
    }
}

/** The list with the item at [fromIndex] moved to [toIndex]; unchanged when either is out of range. */
fun <T> List<T>.movedItem(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
