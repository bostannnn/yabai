package com.example.unifiedreader.feature.settings.direction

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
import com.example.unifiedreader.feature.settings.resolveSettingsAdaptiveLayout
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

data class ReadingDirectionLibraryUiState(
    val libraryId: String,
    val title: String,
    val direction: ComicReadingDirection,
)

@Composable
fun ComicReadingDirectionSettingsSection(
    libraries: List<ReadingDirectionLibraryUiState>,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
) {
    if (libraries.isEmpty()) return

    BoxWithConstraints {
        val layout = resolveSettingsAdaptiveLayout(maxWidth.value.toInt())

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reading direction",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            libraries.forEach { library ->
                ReadingDirectionLibraryRow(
                    library = library,
                    useInlineChoiceControls = layout.useInlineChoiceControls,
                    onLibraryDirectionSelected = onLibraryDirectionSelected,
                )
            }
        }
    }
}

@Composable
private fun ReadingDirectionLibraryRow(
    library: ReadingDirectionLibraryUiState,
    useInlineChoiceControls: Boolean,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
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
                    ComicReadingDirection.entries.forEachIndexed { index, direction ->
                        SegmentedButton(
                            selected = direction == library.direction,
                            onClick = { onLibraryDirectionSelected(library.libraryId, direction) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ComicReadingDirection.entries.size,
                            ),
                            label = {
                                Text(text = direction.label)
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
                ComicReadingDirection.entries.forEachIndexed { index, direction ->
                    SegmentedButton(
                        selected = direction == library.direction,
                        onClick = { onLibraryDirectionSelected(library.libraryId, direction) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ComicReadingDirection.entries.size,
                        ),
                        label = {
                            Text(text = direction.label)
                        },
                    )
                }
            }
        }
    }
}
