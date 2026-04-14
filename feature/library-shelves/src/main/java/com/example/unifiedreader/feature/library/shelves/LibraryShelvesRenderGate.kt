package com.example.unifiedreader.feature.library.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

@Composable
internal fun rememberShelvesRenderReady(
    hasContent: Boolean,
): Boolean {
    var ready by remember(hasContent) { mutableStateOf(!hasContent) }

    LaunchedEffect(hasContent) {
        if (!hasContent) {
            ready = true
            return@LaunchedEffect
        }
        withFrameNanos { }
        delay(SHELVES_RENDER_DELAY_MILLIS)
        ready = true
    }

    return ready
}

private const val SHELVES_RENDER_DELAY_MILLIS = 250L
