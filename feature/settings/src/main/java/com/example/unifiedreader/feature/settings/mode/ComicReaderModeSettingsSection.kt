package com.example.unifiedreader.feature.settings.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.settings.resolveSettingsAdaptiveLayout

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

    BoxWithConstraints {
        val layout = resolveSettingsAdaptiveLayout(maxWidth.value.toInt())

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
                    useInlineChoiceControls = layout.useInlineChoiceControls,
                    onLibraryModeSelected = onLibraryModeSelected,
                )
            }
        }
    }
}

@Composable
private fun ReaderModeLibraryRow(
    library: ReaderModeLibraryUiState,
    useInlineChoiceControls: Boolean,
    onLibraryModeSelected: (String, ComicReaderMode) -> Unit,
) {
    if (useInlineChoiceControls) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = library.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
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
    } else {
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
}
