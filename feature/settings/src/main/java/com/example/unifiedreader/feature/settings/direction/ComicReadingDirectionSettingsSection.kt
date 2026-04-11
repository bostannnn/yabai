package com.example.unifiedreader.feature.settings.direction

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
                onLibraryDirectionSelected = onLibraryDirectionSelected,
            )
        }
    }
}

@Composable
private fun ReadingDirectionLibraryRow(
    library: ReadingDirectionLibraryUiState,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
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
