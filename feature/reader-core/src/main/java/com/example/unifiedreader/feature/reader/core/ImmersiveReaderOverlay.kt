package com.example.unifiedreader.feature.reader.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.unifiedreader.ui.components.LocalAppGlassState
import com.example.unifiedreader.ui.components.appGlassSource
import com.example.unifiedreader.ui.components.AppToastHost
import com.example.unifiedreader.ui.components.rememberAppGlassState
import kotlinx.coroutines.delay

@Stable
class ReaderOverlayState internal constructor(
    initialVisibility: Boolean,
    initialInteractionTick: Long,
) {
    var isVisible by mutableStateOf(initialVisibility)
        private set

    var interactionTick by mutableLongStateOf(initialInteractionTick)
        private set

    fun show() {
        isVisible = true
        interactionTick++
    }

    fun hide() {
        isVisible = false
    }

    fun toggle() {
        isVisible = !isVisible
        interactionTick++
    }
}

@Composable
fun rememberReaderOverlayState(
    key: String,
    initiallyVisible: Boolean = true,
): ReaderOverlayState = rememberSaveable(
    key,
    saver = ReaderOverlayStateSaver,
) {
    ReaderOverlayState(
        initialVisibility = initiallyVisible,
        initialInteractionTick = 0L,
    )
}

private val ReaderOverlayStateSaver: Saver<ReaderOverlayState, List<Any>> = Saver(
    save = { state ->
        listOf(state.isVisible, state.interactionTick)
    },
    restore = { saved ->
        ReaderOverlayState(
            initialVisibility = saved[0] as Boolean,
            initialInteractionTick = saved[1] as Long,
        )
    },
)

/**
 * Hosts the page [content] under the reader chrome [overlay].
 *
 * With [glass] on, the pages are the glass source and the chrome blurs them. Turn it off when
 * the pages are not drawn by Compose: a WebView on Android 15+ renders through its own
 * SurfaceControl overlay, so the glass layer captures nothing and its per-frame re-record
 * fights the overlay (visible as flicker). The chrome then sits on the plain fallback surface.
 *
 * [pageIndicator] shows over the pages while the chrome is hidden and fades out as it appears,
 * so the page number is on screen exactly when the bars that carry it are not.
 */
@Composable
fun ImmersiveReaderOverlay(
    overlayState: ReaderOverlayState,
    autoHideDelayMillis: Long,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    pageIndicator: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    LaunchedEffect(
        overlayState.isVisible,
        overlayState.interactionTick,
        autoHideDelayMillis,
    ) {
        if (!overlayState.isVisible || autoHideDelayMillis <= 0L) return@LaunchedEffect
        delay(autoHideDelayMillis)
        overlayState.hide()
    }

    val glassState = if (glass) rememberAppGlassState() else null
    val dockReach = remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalAppGlassState provides glassState, LocalReaderDockReach provides dockReach) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appGlassSource(),
                content = content,
            )

            if (pageIndicator != null) {
                AnimatedVisibility(
                    visible = !overlayState.isVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), content = pageIndicator)
                }
            }

            AnimatedVisibility(
                visible = overlayState.isVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            ) {
                overlay()
            }

            // Toasts sit 12 dp above the dock while the bars show, above the page number otherwise.
            val aboveDock = overlayState.isVisible && dockReach.value > 0.dp
            AppToastHost(
                modifier = Modifier.zIndex(2f),
                bottomPadding = if (aboveDock) dockReach.value + ReaderToastGap else ReaderToastPageClearance,
                aboveNavigationBar = !aboveDock,
            )
        }
    }
}

/**
 * How far the reader's dock reaches up from the bottom of the frame while the bars show, as
 * [ReaderChromeFrame] measures it; 0 without a dock on screen.
 */
internal val LocalReaderDockReach = staticCompositionLocalOf<MutableState<Dp>?> { null }

private val ReaderToastGap = 12.dp

/** Clear of the page number, which sits in the bottom strip while the bars are hidden. */
private val ReaderToastPageClearance = 44.dp
