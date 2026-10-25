package com.example.unifiedreader.feature.reader.core

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.unifiedreader.core.model.ReadableItem

enum class ReaderChrome {
    Immersive,
    Standard,
}

data class ReaderNavigationActions(
    val onRequestNextInSeries: () -> Unit = {},
    val onRequestCompletion: () -> Unit = {},
    val currentOpenMode: ReaderOpenMode = ReaderOpenMode.Default,
    val readerWorkCoordinator: ReaderWorkCoordinator? = null,
)

interface ReaderScreenFactory {
    fun supports(item: ReadableItem): Boolean

    val chrome: ReaderChrome

    @Composable
    fun Content(
        item: ReadableItem,
        onBack: () -> Unit,
        navigationActions: ReaderNavigationActions,
    )
}

/**
 * Shows [item] in the reader that takes its format. Until the book's first page is there the
 * opening state covers it (the [cover], title and author), and when it can't open, or no reader
 * takes the format, the error screen does; [downloadAgain] offers a fresh download for a server
 * book whose download is broken.
 */
@Composable
fun ReaderHost(
    item: ReadableItem,
    factories: List<ReaderScreenFactory>,
    onBack: () -> Unit,
    navigationActions: ReaderNavigationActions = ReaderNavigationActions(),
    readerWorkCoordinator: ReaderWorkCoordinator? = null,
    cover: (@Composable (ReadableItem, Modifier) -> Unit)? = null,
    downloadAgain: ReaderDownloadAgain? = null,
) {
    val factory = factories.firstOrNull { it.supports(item) }
    val chrome = factory?.chrome ?: ReaderChrome.Immersive
    ReaderWindowEffect(isImmersive = chrome == ReaderChrome.Immersive)
    ReaderWorkEffect(
        item = item,
        readerWorkCoordinator = readerWorkCoordinator,
    )
    val coordinatedNavigationActions = navigationActions.copy(
        readerWorkCoordinator = readerWorkCoordinator,
    )
    val opening = remember(item.id, factory) {
        ReaderOpeningState(
            initialStatus = if (factory == null) {
                ReaderOpenStatus.Failed(ReaderOpenFailure.Unsupported(item.format))
            } else {
                ReaderOpenStatus.Opening(ReaderOpeningStep.Opening)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalReaderOpening provides opening) {
            when {
                factory == null -> Unit
                chrome == ReaderChrome.Immersive -> factory.Content(
                    item = item,
                    onBack = onBack,
                    navigationActions = coordinatedNavigationActions,
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    ReaderTopBar(
                        title = item.title,
                        subtitle = null,
                        onBack = onBack,
                    )
                    factory.Content(
                        item = item,
                        onBack = onBack,
                        navigationActions = coordinatedNavigationActions,
                    )
                }
            }
        }
        ReaderOpeningLayer(
            state = opening,
            item = item,
            cover = cover,
            downloadAgain = downloadAgain,
            onBack = onBack,
        )
    }
}

@Composable
private fun ReaderWorkEffect(
    item: ReadableItem,
    readerWorkCoordinator: ReaderWorkCoordinator?,
) {
    DisposableEffect(readerWorkCoordinator, item.id) {
        val lease = readerWorkCoordinator?.beginReaderSession(item.id)
        onDispose {
            lease?.close()
        }
    }
}

@Composable
private fun ReaderWindowEffect(
    isImmersive: Boolean,
) {
    val view = LocalView.current
    val activity = view.context.findContextAncestor<Activity>() ?: return

    DisposableEffect(activity, isImmersive) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, !isImmersive)
        val controller = WindowInsetsControllerCompat(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isImmersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
