package com.example.unifiedreader.feature.reader.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import com.example.unifiedreader.ui.components.AppScreenBackHandler
import kotlinx.coroutines.CoroutineScope

/** The journal's tracker for [item]'s reading time, or null when there is no journal to record to. */
@Composable
fun rememberReaderActiveSessionTracker(
    item: ReadableItem,
    recorder: JournalReadingSessionRecorder?,
    scope: CoroutineScope,
): ReaderActiveSessionTracker? = remember(item.id, recorder, scope) {
    recorder?.let { ReaderActiveSessionTracker(item = item, recorder = it, scope = scope) }
}

/**
 * A reader's session on screen. [onStart] runs when the reader appears and each time the app comes back to the
 * front; [onLeave] when it goes to the background, leaves the screen, or is closed, to record the reading time and
 * save the position. Back closes the reader: [onLeave], then [onBack]. Returns that close action for the reader's
 * own back button.
 */
@Composable
fun rememberReaderSessionLifecycle(
    key: Any?,
    onStart: () -> Unit,
    onLeave: (ReaderLeave) -> Unit,
    onBack: () -> Unit,
): () -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val start by rememberUpdatedState(onStart)
    val leave by rememberUpdatedState(onLeave)
    val back by rememberUpdatedState(onBack)
    DisposableEffect(lifecycleOwner, key) {
        start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> leave(ReaderLeave.Stop)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            leave(ReaderLeave.Dispose)
        }
    }
    val close = remember {
        {
            leave(ReaderLeave.Close)
            back()
        }
    }
    AppScreenBackHandler(onBack = close)
    return close
}
