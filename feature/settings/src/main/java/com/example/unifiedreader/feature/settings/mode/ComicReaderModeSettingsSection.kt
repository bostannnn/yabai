package com.example.unifiedreader.feature.settings.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode

data class ReaderModeLibraryUiState(
    val libraryId: String,
    val title: String,
    val mode: ComicReaderMode,
)

@Composable
fun ComicReaderModeSettingsSection(
    libraries: List<ReaderModeLibraryUiState>,
    onLibraryModeSelected: (String, ComicReaderMode) -> Unit,
) {
    if (libraries.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Reader mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        libraries.forEach { library ->
            ReaderModeLibraryRow(
                library = library,
                onLibraryModeSelected = onLibraryModeSelected,
            )
        }
    }
}

@Composable
private fun ReaderModeLibraryRow(
    library: ReaderModeLibraryUiState,
    onLibraryModeSelected: (String, ComicReaderMode) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = library.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ComicReaderMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == library.mode,
                    onClick = { onLibraryModeSelected(library.libraryId, mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ComicReaderMode.entries.size,
                    ),
                    label = {
                        Text(text = mode.label)
                    },
                )
            }
        }
    }
}
