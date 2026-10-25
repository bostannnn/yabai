package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.appSkeletonPulse
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The whole description of a book for its page: the library keeps only the start of each, and
 * some servers leave it out of their catalog (Grimmory) or keep it on the series (Kavita).
 * Null when there is none. Never throws; a server that can't be reached gives what is stored.
 */
fun interface LibraryBookDescriptionLoader {
    suspend fun description(item: ReadableItem): String?
}

/** Set by the app; without it the book page shows the description the library already has. */
val LocalLibraryBookDescriptionLoader = staticCompositionLocalOf<LibraryBookDescriptionLoader?> { null }

/** The description to show now, and whether to hold its place with faint lines while it loads. */
internal data class LibraryBookDescriptionState(
    val text: String?,
    val loading: Boolean,
)

/**
 * The page's description: the library's own at once, then the whole one when it arrives. The
 * placeholder lines show only for a book with none yet and only once the wait is noticeable, so a
 * stored description never flickers.
 */
@Composable
internal fun rememberLibraryBookDescription(item: ReadableItem): LibraryBookDescriptionState {
    val loader = LocalLibraryBookDescriptionLoader.current
    var loaded by remember(item.id) { mutableStateOf<LoadedDescription?>(null) }
    var slow by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id, loader) {
        if (loader == null) return@LaunchedEffect
        val placeholder = launch {
            delay(BookDescriptionPlaceholderDelayMillis)
            slow = true
        }
        loaded = LoadedDescription(loader.description(item))
        placeholder.cancel()
    }
    return LibraryBookDescriptionState(
        text = loaded?.text ?: item.description,
        loading = loader != null && loaded == null && slow && item.description.isNullOrBlank(),
    )
}

private class LoadedDescription(val text: String?)

/** Three faint lines where the description will be. */
@Composable
internal fun LibraryBookDescriptionPlaceholder(modifier: Modifier = Modifier) {
    val color = UnifiedReaderThemeTokens.colors.surfaceRaised
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp)
            .appSkeletonPulse(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1f, 0.96f, 0.58f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
            )
        }
    }
}

private const val BookDescriptionPlaceholderDelayMillis = 150L
