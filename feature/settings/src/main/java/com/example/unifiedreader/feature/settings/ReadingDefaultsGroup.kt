package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRowDivider

internal data class ReadingDefaultsRow(
    val libraryId: String,
    val title: String,
    val direction: ComicReadingDirection?,
    val mode: ComicReaderMode?,
)

/** One row per library, joining the direction and layout lists on the library id. */
internal fun buildReadingDefaultsRows(
    directionLibraries: List<ReadingDirectionLibraryUiState>,
    modeLibraries: List<ReaderModeLibraryUiState>,
): List<ReadingDefaultsRow> {
    val modesById = modeLibraries.associateBy { it.libraryId }
    val rows = directionLibraries.map { library ->
        ReadingDefaultsRow(
            libraryId = library.libraryId,
            title = library.title,
            direction = library.direction,
            mode = modesById[library.libraryId]?.mode,
        )
    }
    val seen = rows.mapTo(mutableSetOf()) { it.libraryId }
    return rows + modeLibraries
        .filterNot { it.libraryId in seen }
        .map { library ->
            ReadingDefaultsRow(
                libraryId = library.libraryId,
                title = library.title,
                direction = null,
                mode = library.mode,
            )
        }
}

/** What the defaults reach: what you set for a book or a series in the reader wins (`ComicReadingDirectionResolver`). */
internal const val ReadingDefaultsNote =
    "Comics in each library open this way, unless you picked something else for a book or a series in the reader."

/** The "Reading defaults" group of a source page: direction and layout per library. */
@Composable
internal fun ReadingDefaultsGroup(
    rows: List<ReadingDefaultsRow>,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryModeSelected: (String, ComicReaderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSettingsGroup(label = "Reading defaults") {
            BoxWithConstraints {
                val layout = resolveSettingsAdaptiveLayout(maxWidth.value.toInt())
                Column {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) AppSettingsRowDivider(inset = AppSettingsDefaults.dividerInsetNoIcon)
                        ReadingDefaultsLibraryRow(
                            row = row,
                            inline = layout.useInlineChoiceControls,
                            onLibraryDirectionSelected = onLibraryDirectionSelected,
                            onLibraryModeSelected = onLibraryModeSelected,
                        )
                    }
                }
            }
        }
        // Flush with the panel's edge, like the group label: a line of the page, not of a row.
        SettingsPageLine(ReadingDefaultsNote)
    }
}

@Composable
private fun ReadingDefaultsLibraryRow(
    row: ReadingDefaultsRow,
    inline: Boolean,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryModeSelected: (String, ComicReaderMode) -> Unit,
) {
    // The direction in words needs more room than Paged / Scroll.
    val controls: @Composable (directionModifier: Modifier, modeModifier: Modifier) -> Unit = { directionModifier, modeModifier ->
        row.direction?.let { direction ->
            AppSegmentedRow(modifier = directionModifier) {
                ComicReadingDirection.entries.forEach { option ->
                    AppSegment(
                        selected = option == direction,
                        onClick = { onLibraryDirectionSelected(row.libraryId, option) },
                        label = { Text(text = option.wordsLabel) },
                    )
                }
            }
        }
        row.mode?.let { mode ->
            AppSegmentedRow(modifier = modeModifier) {
                ComicReaderMode.entries.forEach { option ->
                    AppSegment(
                        selected = option == mode,
                        onClick = { onLibraryModeSelected(row.libraryId, option) },
                        label = { Text(text = option.label) },
                    )
                }
            }
        }
    }
    if (inline) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSettingsDefaults.rowMinHeight)
                .padding(horizontal = AppSettingsDefaults.horizontalPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            controls(Modifier.widthIn(min = 240.dp, max = 280.dp), Modifier.widthIn(min = 150.dp, max = 200.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSettingsDefaults.horizontalPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
            )
            // Stacked: side by side, "Right to left" has no room on a phone.
            controls(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
        }
    }
}
