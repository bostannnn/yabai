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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.example.unifiedreader.ui.components.LocalAppGlassState
import com.example.unifiedreader.ui.components.appGlassSource
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
    key = key,
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
 */
@Composable
fun ImmersiveReaderOverlay(
    overlayState: ReaderOverlayState,
    autoHideDelayMillis: Long,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
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
    CompositionLocalProvider(LocalAppGlassState provides glassState) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appGlassSource(),
                content = content,
            )

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
        }
    }
}
