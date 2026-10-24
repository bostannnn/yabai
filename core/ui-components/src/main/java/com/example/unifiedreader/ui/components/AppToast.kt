package com.example.unifiedreader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.ui.components.R
import com.example.unifiedreader.ui.theme.AppMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The mark at the start of a toast. [Error] also colours it. */
enum class AppToastIcon { Done, Unread, Download, Removed, Added, Error }

/**
 * What a toast says: [text], an optional [supportingText] line, and an optional action
 * ([actionLabel], [onAction]). [onDismissed] runs once when it leaves without its action: timed
 * out, swiped away, or replaced by the next toast.
 */
@Immutable
class AppToastMessage(
    val text: String,
    val icon: AppToastIcon = AppToastIcon.Done,
    val supportingText: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onDismissed: (() -> Unit)? = null,
) {
    val isError: Boolean get() = icon == AppToastIcon.Error

    /** 4 seconds; 6 with an action or for an error, which take longer to act on. */
    val durationMillis: Long get() = if (actionLabel != null || isError) LongToastMillis else ShortToastMillis
}

/**
 * The app's one toast at a time. [show] replaces the toast on screen (its [AppToastMessage.onDismissed]
 * runs). Every [AppToastHost] in the tree (the shell, a book, a sheet in its own window) registers
 * here and only the latest one shown draws the toast, so it is never hidden under a sheet.
 */
@Stable
class AppToastState {
    internal var current by mutableStateOf<AppShownToast?>(null)
        private set
    private var nextId = 0L
    internal val hosts = mutableStateListOf<Any>()

    /** How far the toast on screen reaches up from the bottom of its host, for floating controls to clear. */
    var bottomObstruction by mutableStateOf(0.dp)
        internal set

    fun show(message: AppToastMessage) {
        val previous = current
        current = AppShownToast(id = ++nextId, message = message)
        previous?.message?.onDismissed?.invoke()
    }

    internal fun dismiss(id: Long, byAction: Boolean) {
        val shown = current?.takeIf { it.id == id } ?: return
        current = null
        if (!byAction) shown.message.onDismissed?.invoke()
    }
}

@Immutable
internal class AppShownToast(val id: Long, val message: AppToastMessage)

val LocalAppToastState = staticCompositionLocalOf<AppToastState?> { null }

/**
 * Where toasts appear in this part of the tree: bottom centre, [bottomPadding] above the bottom
 * (and the navigation bar, unless [aboveNavigationBar] is off because the caller measured from it,
 * and the keyboard),
 * the window's width less 16 dp on phones, at most 480 dp elsewhere. Put it last in a Box that
 * fills the area. It shows only while it is the latest host to appear.
 */
@Composable
fun AppToastHost(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = AppToastDefaults.bottomPadding,
    aboveNavigationBar: Boolean = true,
) {
    val state = LocalAppToastState.current ?: return
    val token = remember { Any() }
    DisposableEffect(state) {
        state.hosts.add(token)
        onDispose { state.hosts.remove(token) }
    }
    val shown = state.current.takeIf { state.hosts.lastOrNull() === token }
    val density = LocalDensity.current
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    var height by remember { mutableStateOf(0.dp) }
    DisposableEffect(shown?.id, height, bottomPadding) {
        if (shown != null) state.bottomObstruction = height + bottomPadding + AppToastDefaults.clearance
        onDispose { if (shown != null) state.bottomObstruction = 0.dp }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // Above the keyboard too; when the caller already cleared the navigation bar, only the
            // part of the keyboard above it.
            .windowInsetsPadding(
                if (aboveNavigationBar) {
                    WindowInsets.navigationBars.union(WindowInsets.ime)
                } else {
                    WindowInsets.ime.exclude(WindowInsets.navigationBars)
                },
            )
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = shown,
            contentKey = { it?.id },
            transitionSpec = {
                val rise = with(density) { AppToastDefaults.enterRise.roundToPx() }
                val drop = with(density) { AppToastDefaults.exitDrop.roundToPx() }
                (slideInVertically(AppMotion.spatial()) { rise } + fadeIn(AppMotion.effects()))
                    .togetherWith(slideOutVertically(AppMotion.spatial()) { drop } + fadeOut(AppMotion.effects()))
            },
            contentAlignment = Alignment.BottomCenter,
            label = "toast",
        ) { toast ->
            if (toast == null) return@AnimatedContent
            AppToastTimer(state = state, toast = toast) { held ->
                AppToast(
                    message = toast.message,
                    onAction = {
                        state.dismiss(toast.id, byAction = true)
                        toast.message.onAction?.invoke()
                    },
                    onSwipedAway = { state.dismiss(toast.id, byAction = false) },
                    onHeldChanged = held,
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(max = AppToastDefaults.maxWidth))
                        .onSizeChanged { size -> height = with(density) { size.height.toDp() } },
                )
            }
        }
    }
}

/** Times [toast] out, longer when accessibility services ask for it, and not while it is held. */
@Composable
private fun AppToastTimer(
    state: AppToastState,
    toast: AppShownToast,
    content: @Composable (onHeldChanged: (Boolean) -> Unit) -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(toast.id, held) {
        if (held) return@LaunchedEffect
        val message = toast.message
        val millis = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = message.durationMillis,
            containsIcons = true,
            containsText = true,
            containsControls = message.actionLabel != null,
        ) ?: message.durationMillis
        if (millis == Long.MAX_VALUE) return@LaunchedEffect
        delay(millis)
        state.dismiss(toast.id, byAction = false)
    }
    content { held = it }
}

/**
 * The toast itself: the bars' glass (an opaque raised surface where nothing is there to blur),
 * the icon, the text, and the action as an accent pill. Swiping it sideways or down dismisses it.
 */
@Composable
private fun AppToast(
    message: AppToastMessage,
    onAction: () -> Unit,
    onSwipedAway: () -> Unit,
    onHeldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dismissDistance = with(density) { AppToastDefaults.swipeDistance.toPx() }
    val shape = RoundedCornerShape(AppToastDefaults.cornerRadius)
    // Glass blurs what is under the toast; in a sheet's own window there is nothing to blur, so
    // the kit's opaque fallback needs a surface that stands off the sheet.
    val container = if (LocalAppGlassState.current != null) colors.surface else colors.surfaceContainerHighest
    val dismissLabel = stringResource(R.string.app_toast_dismiss)
    Row(
        modifier = modifier
            .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.coerceAtLeast(0f).roundToInt()) }
            .graphicsLayer {
                val travelled = maxOf(abs(drag.value.x), drag.value.y.coerceAtLeast(0f))
                alpha = (1f - travelled / (dismissDistance * 2f)).coerceIn(0f, 1f)
            }
            .shadow(
                elevation = AppToastDefaults.shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .appGlass(shape = shape, containerColor = container)
            .heightIn(min = AppToastDefaults.minHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHeldChanged(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    onHeldChanged(false)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val value = drag.value
                        if (abs(value.x) > dismissDistance || value.y > dismissDistance / 2f) {
                            onSwipedAway()
                        } else {
                            scope.launch { drag.animateTo(Offset.Zero, AppMotion.spatial()) }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, AppMotion.spatial()) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount) }
                }
            }
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                customActions = listOf(
                    CustomAccessibilityAction(dismissLabel) {
                        onSwipedAway()
                        true
                    },
                )
            }
            .padding(
                PaddingValues(
                    start = 16.dp,
                    end = if (message.actionLabel != null) 6.dp else 20.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = message.icon.vector,
            contentDescription = null,
            tint = if (message.isError) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            message.supportingText?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        message.actionLabel?.let { label ->
            AppButton(
                onClick = onAction,
                colors = AppButtonDefaults.filledColors(
                    containerColor = colors.primary.copy(alpha = 0.14f),
                    contentColor = colors.primary,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(AppToastDefaults.actionHeight),
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1)
            }
        }
    }
}

private val AppToastIcon.vector: ImageVector
    @Composable get() = when (this) {
        AppToastIcon.Done -> AppIcons.Check
        AppToastIcon.Unread -> AppIcons.History
        AppToastIcon.Download -> AppIcons.Download
        AppToastIcon.Removed -> AppIcons.Delete
        AppToastIcon.Added -> AppIcons.Add
        AppToastIcon.Error -> AppIcons.ErrorOutline
    }

/**
 * Moves a floating control at the bottom of the screen (the grid's back-to-top button) up and out
 * of the way of the toast on screen, on the spatial spring.
 */
@Composable
fun Modifier.appToastAvoiding(): Modifier {
    val obstruction = LocalAppToastState.current?.bottomObstruction ?: 0.dp
    val lift by animateDpAsState(targetValue = obstruction, animationSpec = AppMotion.spatial(), label = "toastAvoiding")
    val density = LocalDensity.current
    return offset { IntOffset(0, -with(density) { lift.roundToPx() }) }
}

private const val ShortToastMillis = 4_000L
private const val LongToastMillis = 6_000L

object AppToastDefaults {
    val minHeight: Dp = 48.dp
    val cornerRadius: Dp = 24.dp
    val maxWidth: Dp = 480.dp
    val actionHeight: Dp = 36.dp
    val shadowElevation: Dp = 8.dp

    /** Above the bottom of the host (and the navigation bar). */
    val bottomPadding: Dp = 16.dp

    /** Between the toast and a control that steps out of its way. */
    val clearance: Dp = 8.dp
    val enterRise: Dp = 16.dp
    val exitDrop: Dp = 8.dp
    val swipeDistance: Dp = 96.dp
}
