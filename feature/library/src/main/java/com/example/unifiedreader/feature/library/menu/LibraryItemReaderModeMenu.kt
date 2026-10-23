package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow

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
    val modes = ComicReaderMode.entries
    AppSegmentedRow(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        modes.forEach { mode ->
            AppSegment(
                selected = mode == state.selectedMode,
                onClick = { onModeSelected(mode) },
                label = { Text(text = mode.label) },
            )
        }
    }
}
