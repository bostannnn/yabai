package com.example.unifiedreader.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.AppMotion
import kotlin.math.roundToInt

/**
 * A small card that opens at something on screen rather than at an edge: a word you selected
 * in a book. It sits under [anchor] with a 12 gap, or over it when there isn't room below, and
 * stays inside the window's gutter. A tap anywhere else or Back calls [onDismissRequest]; the
 * card has no scrim, so what it points at stays readable. Without an anchor it sits in the
 * middle of the slot.
 *
 * The caller places it in a full-size slot whose coordinates [anchor] is in (pixels).
 */
@Composable
fun AppAnchoredCard(
    anchor: Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = AppAnchoredCardDefaults.maxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismissRequest)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val gutter = appGutter()
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
    ) {
        Layout(
            content = {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(AppMotion.effects()) + scaleIn(AppMotion.spatial(), initialScale = 0.96f),
                    exit = fadeOut(AppMotion.effects()) + scaleOut(AppMotion.spatial(), targetScale = 0.96f),
                ) {
                    AppPanel(
                        // Taps on the card stay on the card.
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = AppAnchoredCardDefaults.elevation,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(AppLayout.inset),
                            content = content,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val gutterPx = gutter.roundToPx()
            val width = minOf(maxWidth.roundToPx(), constraints.maxWidth - 2 * gutterPx).coerceAtLeast(0)
            val placeable = measurables.single().measure(
                Constraints(maxWidth = width, maxHeight = (constraints.maxHeight - 2 * gutterPx).coerceAtLeast(0)),
            )
            val position = placeAnchoredCard(
                anchor = anchor,
                card = IntSize(placeable.width, placeable.height),
                window = IntSize(constraints.maxWidth, constraints.maxHeight),
                gutter = gutterPx,
                gap = AppAnchoredCardDefaults.gap.roundToPx(),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(position)
            }
        }
    }
}

object AppAnchoredCardDefaults {
    val maxWidth: Dp = 380.dp
    val gap: Dp = 12.dp
    val elevation: Dp = 8.dp
}

/**
 * Where the card goes, kept pure for tests: centred on the anchor, under it if it fits, else
 * over it, else wherever has more room; always inside the gutter.
 */
fun placeAnchoredCard(
    anchor: Rect?,
    card: IntSize,
    window: IntSize,
    gutter: Int,
    gap: Int,
): IntOffset {
    val maxX = (window.width - gutter - card.width).coerceAtLeast(gutter)
    val maxY = (window.height - gutter - card.height).coerceAtLeast(gutter)
    if (anchor == null) {
        return IntOffset((window.width - card.width) / 2, (window.height - card.height) / 2)
    }
    val x = (anchor.center.x - card.width / 2f).roundToInt().coerceIn(gutter, maxX)
    val below = anchor.bottom.roundToInt() + gap
    val above = anchor.top.roundToInt() - gap - card.height
    val y = when {
        below + card.height <= window.height - gutter -> below
        above >= gutter -> above
        window.height - anchor.bottom > anchor.top -> below
        else -> above
    }.coerceIn(gutter, maxY)
    return IntOffset(x, y)
}
