package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode

internal data class ReaderModeMenuState(
    val selectedMode: ComicReaderMode,
    val onModeSelected: (ComicReaderMode) -> Unit,
)

internal fun buildReaderModeMenuState(
    seriesReaderMode: ComicReaderMode?,
    onSeriesReaderModeSelected: ((ComicReaderMode) -> Unit)?,
): ReaderModeMenuState? {
    if (seriesReaderMode == null || onSeriesReaderModeSelected == null) return null
    return ReaderModeMenuState(
        selectedMode = seriesReaderMode,
        onModeSelected = onSeriesReaderModeSelected,
    )
}

@Composable
internal fun ReaderModeMenuRow(
    state: ReaderModeMenuState,
    onModeSelected: (ComicReaderMode) -> Unit,
) {
    val buttonShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ComicReaderMode.entries.forEach { mode ->
            ReadingDirectionButton(
                onClick = { onModeSelected(mode) },
                selected = mode == state.selectedMode,
                shape = buttonShape,
            ) {
                Icon(
                    imageVector = mode.icon(),
                    contentDescription = "Reader mode ${mode.label}",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun ComicReaderMode.icon(): ImageVector = when (this) {
    ComicReaderMode.Paged -> Icons.Default.ViewStream
    ComicReaderMode.VerticalScroll -> Icons.Default.ViewStream
}
