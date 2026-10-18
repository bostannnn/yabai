package com.example.unifiedreader.feature.reader.core

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons

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

@Composable
fun ReaderHost(
    item: ReadableItem,
    factories: List<ReaderScreenFactory>,
    onBack: () -> Unit,
    navigationActions: ReaderNavigationActions = ReaderNavigationActions(),
    readerWorkCoordinator: ReaderWorkCoordinator? = null,
) {
    val factory = factories.firstOrNull { it.supports(item) }
    val chrome = factory?.chrome ?: ReaderChrome.Standard
    ReaderWindowEffect(isImmersive = chrome == ReaderChrome.Immersive)
    ReaderWorkEffect(
        item = item,
        readerWorkCoordinator = readerWorkCoordinator,
    )
    val coordinatedNavigationActions = navigationActions.copy(
        readerWorkCoordinator = readerWorkCoordinator,
    )

    when {
        factory == null -> UnsupportedReader(item = item, onBack = onBack)
        chrome == ReaderChrome.Immersive -> factory.Content(
            item = item,
            onBack = onBack,
            navigationActions = coordinatedNavigationActions,
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = item.title,
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
private fun UnsupportedReader(
    item: ReadableItem,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ReaderTopBar(
            title = item.title,
            onBack = onBack,
        )
        Text(
            text = "No reader is registered for ${item.format.name}.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
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

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(onClick = onBack) {
            Icon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = "Back to library",
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

}
