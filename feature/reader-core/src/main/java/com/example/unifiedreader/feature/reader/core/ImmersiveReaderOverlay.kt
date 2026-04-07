package com.example.unifiedreader.feature.reader.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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

@Composable
fun ImmersiveReaderOverlay(
    overlayState: ReaderOverlayState,
    autoHideDelayMillis: Long,
    modifier: Modifier = Modifier,
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

    Box(modifier = modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = overlayState.isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f),
        ) {
            overlay()
        }
    }
}
