package com.example.unifiedreader.feature.reader.core

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.unifiedreader.core.model.ReadableItem

enum class ReaderChrome {
    Immersive,
    Standard,
}

interface ReaderScreenFactory {
    fun supports(item: ReadableItem): Boolean

    val chrome: ReaderChrome

    @Composable
    fun Content(
        item: ReadableItem,
        onBack: () -> Unit,
    )
}

@Composable
fun ReaderHost(
    item: ReadableItem,
    factories: List<ReaderScreenFactory>,
    onBack: () -> Unit,
) {
    val factory = factories.firstOrNull { it.supports(item) }
    val chrome = factory?.chrome ?: ReaderChrome.Standard
    ReaderWindowEffect(isImmersive = chrome == ReaderChrome.Immersive)

    when {
        factory == null -> UnsupportedReader(item = item, onBack = onBack)
        chrome == ReaderChrome.Immersive -> factory.Content(item = item, onBack = onBack)
        else -> Column(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = item.title,
                onBack = onBack,
            )
            factory.Content(
                item = item,
                onBack = onBack,
            )
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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.systemBars())
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
    ) {
        Text(
            text = "Back",
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .clickable(onClick = onBack),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
}
