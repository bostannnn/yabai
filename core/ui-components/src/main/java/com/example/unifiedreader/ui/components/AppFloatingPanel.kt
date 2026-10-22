package com.example.unifiedreader.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Where an [AppFloatingPanel] sits on a wide screen. Under the compact width every placement is a bottom sheet. */
enum class AppFloatingPanelPlacement {
    /** Full-height panel docked to the start edge (a table of contents). */
    Start,

    /** Full-height panel docked to the end edge (settings with many controls). */
    End,

    /** Content-sized card under a button at the end of the top bar (quick settings). */
    TopEnd,
}

/** How an [AppFloatingPanel] is laid out once the screen width is known. */
enum class AppFloatingPanelMode {
    EdgePanel,
    AnchoredCard,
    BottomSheet,
}

data class AppFloatingPanelLayout(
    val mode: AppFloatingPanelMode,
    val alignment: Alignment,
    /** Null means the panel fills the width (the bottom sheet). */
    val width: Dp?,
    val fillHeight: Boolean,
)

/**
 * The placement rule, kept pure so it can be tested: wide screens get the requested
 * placement, compact screens (under [compactWidthDp]) get a bottom sheet whatever was asked.
 */
fun resolveAppFloatingPanelLayout(
    placement: AppFloatingPanelPlacement,
    screenWidthDp: Int,
    width: Dp,
    compactWidthDp: Int = AppFloatingPanelDefaults.compactWidthDp,
): AppFloatingPanelLayout {
    if (screenWidthDp < compactWidthDp) {
        return AppFloatingPanelLayout(
            mode = AppFloatingPanelMode.BottomSheet,
            alignment = Alignment.BottomCenter,
            width = null,
            fillHeight = false,
        )
    }
    return when (placement) {
        AppFloatingPanelPlacement.Start -> AppFloatingPanelLayout(
            mode = AppFloatingPanelMode.EdgePanel,
            alignment = Alignment.CenterStart,
            width = width,
            fillHeight = true,
        )

        AppFloatingPanelPlacement.End -> AppFloatingPanelLayout(
            mode = AppFloatingPanelMode.EdgePanel,
            alignment = Alignment.CenterEnd,
            width = width,
            fillHeight = true,
        )

        AppFloatingPanelPlacement.TopEnd -> AppFloatingPanelLayout(
            mode = AppFloatingPanelMode.AnchoredCard,
            alignment = Alignment.TopEnd,
            width = width,
            fillHeight = false,
        )
    }
}

/**
 * A panel that floats over a screen's own content without opening a window: the reader's
 * table of contents and settings, or any menu that should animate with the chrome around it
 * and sit inside the host's insets. Because it is in the tree it can be glass; where no glass
 * state is provided it is the opaque raised surface.
 *
 * The caller owns [visible] and places the panel in a full-size slot that is already inset
 * from the system bars and from any bar the panel must not cover ([contentPadding] adds the
 * panel's own margin inside that slot; the bottom sheet ignores the bottom and side margins).
 * While visible, a scrim covers the slot, tapping it or pressing back calls
 * [onDismissRequest], and the panel slides in from its edge. A predictive back gesture moves
 * and shrinks the panel toward its edge as it goes; letting go closes it from there.
 */
@Composable
fun AppFloatingPanel(
    visible: Boolean,
    placement: AppFloatingPanelPlacement,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = AppFloatingPanelDefaults.width(placement),
    maxHeight: Dp = Dp.Unspecified,
    contentPadding: PaddingValues = PaddingValues(AppFloatingPanelDefaults.margin),
    containerColor: Color = AppFloatingPanelDefaults.containerColor,
    glass: Boolean = true,
    scrimColor: Color = AppFloatingPanelDefaults.scrimColor,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { event -> backProgress.snapTo(event.progress) }
            onDismissRequest()
        } catch (cancelled: CancellationException) {
            scope.launch { backProgress.animateTo(0f, AppMotion.spatial()) }
            throw cancelled
        }
    }
    // The panel leaves from wherever the gesture let go of it; a fresh opening starts square.
    LaunchedEffect(visible) {
        if (visible) backProgress.snapTo(0f)
    }

    val layout = resolveAppFloatingPanelLayout(
        placement = placement,
        screenWidthDp = LocalConfiguration.current.screenWidthDp,
        width = width,
    )
    val layoutDirection = LocalLayoutDirection.current
    val shape = AppFloatingPanelDefaults.shape(layout.mode)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = AppMotion.effects()),
            exit = fadeOut(animationSpec = AppMotion.effects()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - backProgress.value }
                    .background(scrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(layout.padding(contentPadding, layoutDirection)),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = layout.enter(layoutDirection),
                exit = layout.exit(layoutDirection),
                modifier = Modifier.align(layout.alignment),
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            when {
                                layout.width == null -> Modifier.fillMaxWidth()
                                else -> Modifier.widthIn(max = layout.width).width(layout.width)
                            },
                        )
                        .then(if (layout.fillHeight) Modifier.fillMaxHeight() else Modifier)
                        .then(if (maxHeight.isSpecified) Modifier.heightIn(max = maxHeight) else Modifier)
                        .graphicsLayer {
                            val progress = backProgress.value
                            val shift = AppMotion.predictiveBackShift.toPx() * progress
                            val toward = layout.dockedEdge(layoutDirection)
                            translationX = toward.x * shift
                            translationY = toward.y * shift
                            scaleX = 1f - AppMotion.predictiveBackShrink * progress
                            scaleY = scaleX
                            transformOrigin = toward.origin
                        }
                        .panelSurface(shape = shape, containerColor = containerColor, glass = glass)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                ) {
                    if (layout.mode == AppFloatingPanelMode.BottomSheet) {
                        AppFloatingPanelHandle()
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun Modifier.panelSurface(
    shape: Shape,
    containerColor: Color,
    glass: Boolean,
): Modifier {
    if (glass) {
        return appGlass(
            shape = shape,
            containerColor = containerColor,
            tintAlpha = AppGlassDefaults.paneTintAlpha,
        )
    }
    val edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AppGlassDefaults.edgeAlpha)
    return clip(shape)
        .background(containerColor)
        .border(width = 1.dp, color = edge, shape = shape)
}

@Composable
private fun ColumnScope.AppFloatingPanelHandle() {
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 10.dp, bottom = 2.dp)
            .width(32.dp)
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(2.dp),
            ),
    )
}

private fun AppFloatingPanelLayout.padding(
    requested: PaddingValues,
    layoutDirection: LayoutDirection,
): PaddingValues = when (mode) {
    // A bottom sheet runs edge to edge; the host's insets already keep it clear of the bars.
    AppFloatingPanelMode.BottomSheet -> PaddingValues(
        top = requested.calculateTopPadding(),
    )

    AppFloatingPanelMode.EdgePanel,
    AppFloatingPanelMode.AnchoredCard -> PaddingValues(
        start = requested.calculateStartPadding(layoutDirection),
        top = requested.calculateTopPadding(),
        end = requested.calculateEndPadding(layoutDirection),
        bottom = requested.calculateBottomPadding(),
    )
}

private fun AppFloatingPanelLayout.enter(layoutDirection: LayoutDirection): EnterTransition {
    val spec = AppMotion.spatial(IntOffset.VisibilityThreshold)
    val fade = fadeIn(animationSpec = AppMotion.effects())
    return when (mode) {
        AppFloatingPanelMode.BottomSheet -> slideInVertically(spec) { height -> height / 3 } + fade
        AppFloatingPanelMode.AnchoredCard -> slideInVertically(spec) { height -> -height / 4 } + fade
        AppFloatingPanelMode.EdgePanel -> slideInHorizontally(spec) { width ->
            edgeOffset(width, layoutDirection)
        } + fade
    }
}

private fun AppFloatingPanelLayout.exit(layoutDirection: LayoutDirection): ExitTransition {
    val spec = AppMotion.spatial(IntOffset.VisibilityThreshold)
    val fade = fadeOut(animationSpec = AppMotion.effects())
    return when (mode) {
        AppFloatingPanelMode.BottomSheet -> slideOutVertically(spec) { height -> height / 3 } + fade
        AppFloatingPanelMode.AnchoredCard -> slideOutVertically(spec) { height -> -height / 4 } + fade
        AppFloatingPanelMode.EdgePanel -> slideOutHorizontally(spec) { width ->
            edgeOffset(width, layoutDirection)
        } + fade
    }
}

/** A third of the panel's width, toward the edge it is docked to, in layout-direction terms. */
private fun AppFloatingPanelLayout.edgeOffset(width: Int, layoutDirection: LayoutDirection): Int {
    val dockedAtStart = alignment == Alignment.CenterStart
    val towardLeft = dockedAtStart == (layoutDirection == LayoutDirection.Ltr)
    return if (towardLeft) -width / 3 else width / 3
}

/** The edge a panel leaves through: a unit direction and the matching transform origin. */
private class PanelEdge(val x: Float, val y: Float, val origin: TransformOrigin)

private fun AppFloatingPanelLayout.dockedEdge(layoutDirection: LayoutDirection): PanelEdge = when (mode) {
    AppFloatingPanelMode.BottomSheet -> PanelEdge(0f, 1f, TransformOrigin(0.5f, 1f))
    AppFloatingPanelMode.AnchoredCard -> {
        val right = layoutDirection == LayoutDirection.Ltr
        PanelEdge(if (right) 1f else -1f, -1f, TransformOrigin(if (right) 1f else 0f, 0f))
    }
    AppFloatingPanelMode.EdgePanel -> {
        val left = (alignment == Alignment.CenterStart) == (layoutDirection == LayoutDirection.Ltr)
        PanelEdge(if (left) -1f else 1f, 0f, TransformOrigin(if (left) 0f else 1f, 0.5f))
    }
}

private val Dp.isSpecified: Boolean
    get() = this != Dp.Unspecified

object AppFloatingPanelDefaults {
    /** The drawer's and side sheet's distance from the screen edges. */
    val margin: Dp = 12.dp

    /** Below this width every placement is a bottom sheet, matching [AppSideSheetDefaults]. */
    const val compactWidthDp: Int = AppSideSheetDefaults.compactWidthDp

    const val scrimAlpha: Float = 0.2f

    fun width(placement: AppFloatingPanelPlacement): Dp = when (placement) {
        AppFloatingPanelPlacement.Start -> 360.dp
        AppFloatingPanelPlacement.End,
        AppFloatingPanelPlacement.TopEnd -> AppSideSheetDefaults.width
    }

    /** The raised surface; glass tints it, the fallback paints it. */
    val containerColor: Color
        @Composable get() = UnifiedReaderThemeTokens.colors.surfaceRaised

    val scrimColor: Color
        @Composable get() = MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)

    /** Container radius; a bottom sheet rounds only its top corners. */
    @Composable
    fun shape(mode: AppFloatingPanelMode): Shape {
        val radius = UnifiedReaderThemeTokens.presentation.cardCornerRadius
        return when (mode) {
            AppFloatingPanelMode.BottomSheet -> RoundedCornerShape(topStart = radius, topEnd = radius)
            AppFloatingPanelMode.EdgePanel,
            AppFloatingPanelMode.AnchoredCard -> RoundedCornerShape(radius)
        }
    }
}
