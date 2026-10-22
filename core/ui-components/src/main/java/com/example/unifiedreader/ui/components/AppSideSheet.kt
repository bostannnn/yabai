package com.example.unifiedreader.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** The content of an [AppSideSheet]: a column that can also close its sheet. */
interface AppSideSheetScope : ColumnScope {
    /** Plays the sheet's exit animation, then calls its `onDismissRequest`. */
    fun dismiss()
}

/**
 * A modal panel docked to the end edge for tasks with several controls (filters, options).
 * On compact widths it becomes the bottom sheet. Like every sheet it is its own window, so it
 * is an opaque surface rather than glass. It slides in from the end edge like the drawer and
 * keeps the drawer's margin from the screen edges and the system bars.
 *
 * Tapping outside, back, and [AppSideSheetScope.dismiss] slide it out before
 * [onDismissRequest] is called; a predictive back gesture moves it toward its edge as it goes.
 */
@Composable
fun AppSideSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = AppSideSheetDefaults.width,
    content: @Composable AppSideSheetScope.() -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < AppSideSheetDefaults.compactWidthDp
    if (compact) {
        AppSheetLayout(onDismissRequest = onDismissRequest) { hide ->
            AppSideSheetScopeImpl(column = this, onDismiss = hide).content()
        }
        return
    }
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = { visibleState.targetState = false }
    // The caller removes the sheet once its exit animation has played.
    if (!visibleState.targetState && !visibleState.currentState && visibleState.isIdle) {
        LaunchedEffect(Unit) { latestOnDismissRequest() }
    }
    AppDialogHost(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val backProgress = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        PredictiveBackHandler(enabled = visibleState.targetState) { events ->
            try {
                events.collect { event -> backProgress.snapTo(event.progress) }
                dismiss()
            } catch (cancelled: CancellationException) {
                scope.launch { backProgress.animateTo(0f, AppMotion.spatial()) }
                throw cancelled
            }
        }
        // The end edge: right to left flips which way the sheet travels.
        val towardEnd = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
        // On Android 15+ the dialog's content is laid out at the full screen height while the
        // window manager fits the window between the system bars, so the bottom of a
        // fill-height panel ends up under the gesture bar. Clamp the content to the window's
        // visible frame; where the two already agree this is a no-op.
        val view = LocalView.current
        val density = LocalDensity.current
        var overflow by remember { mutableStateOf(0.dp) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val frame = android.graphics.Rect()
                    view.getWindowVisibleDisplayFrame(frame)
                    val extra = coordinates.size.height - frame.height()
                    overflow = if (extra > 0) with(density) { extra.toDp() } else 0.dp
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                )
                .padding(bottom = overflow)
                .padding(AppSideSheetDefaults.margin),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInHorizontally(
                    animationSpec = AppMotion.spatial(IntOffset.VisibilityThreshold),
                    initialOffsetX = { fullWidth -> towardEnd * fullWidth / 3 },
                ) + fadeIn(animationSpec = AppMotion.effects()),
                exit = slideOutHorizontally(
                    animationSpec = AppMotion.spatial(IntOffset.VisibilityThreshold),
                    targetOffsetX = { fullWidth -> towardEnd * fullWidth / 3 },
                ) + fadeOut(animationSpec = AppMotion.effects()),
            ) {
                AppPanel(
                    modifier = modifier
                        .width(width)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val progress = backProgress.value
                            translationX = towardEnd * AppMotion.predictiveBackShift.toPx() * progress
                            scaleX = 1f - AppMotion.predictiveBackShrink * progress
                            scaleY = scaleX
                            transformOrigin = TransformOrigin(if (towardEnd > 0) 1f else 0f, 0.5f)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    color = UnifiedReaderThemeTokens.colors.surfaceBase,
                    shadowElevation = 16.dp,
                ) {
                    AppSideSheetScopeImpl(column = this, onDismiss = dismiss).content()
                }
            }
        }
    }
}

private class AppSideSheetScopeImpl(
    column: ColumnScope,
    private val onDismiss: () -> Unit,
) : AppSideSheetScope, ColumnScope by column {
    override fun dismiss() = onDismiss()
}

object AppSideSheetDefaults {
    val width: Dp = 400.dp
    val margin: Dp = 12.dp
    const val compactWidthDp: Int = 600
}
