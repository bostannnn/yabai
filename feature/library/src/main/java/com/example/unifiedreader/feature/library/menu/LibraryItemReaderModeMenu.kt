package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                Text(text = mode.label)
            }
        }
    }
}
