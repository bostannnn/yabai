package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibrarySortMode

internal const val LibraryGridSizeOptionTagPrefix = "library-grid-size-option-"

@Composable
internal fun LibraryBrowseOptionsSelector(
    sortMode: LibrarySortMode,
    gridCellSizeDp: Int,
    gridSizeOptions: List<LibraryGridSizeOption>,
    fadeReadItems: Boolean,
    showSortOptions: Boolean,
    availableSortModes: List<LibrarySortMode> = LibrarySortMode.entries,
    lockedSortLabel: String? = null,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    Box {
        FilterChip(
            selected = sanitizedGridCellSizeDp != defaultLibraryGridCellSizeDp ||
                fadeReadItems ||
                (showSortOptions && sortMode != defaultLibrarySortMode) ||
                lockedSortLabel != null,
            onClick = { expanded = true },
            modifier = Modifier.libraryAccentChipTreatment(
                selected = sanitizedGridCellSizeDp != defaultLibraryGridCellSizeDp ||
                    fadeReadItems ||
                    (showSortOptions && sortMode != defaultLibrarySortMode) ||
                    lockedSortLabel != null,
            ),
            colors = libraryAccentFilterChipColors(),
            shape = libraryAccentFilterChipShape(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            label = {
                Text(
                    text = if (lockedSortLabel != null) {
                        "Order"
                    } else if (showSortOptions) {
                        sortMode.label
                    } else {
                        "View"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (lockedSortLabel != null) {
                DropdownSectionHeader(text = "Order")
                DropdownMenuItem(
                    text = { Text(text = lockedSortLabel) },
                    enabled = false,
                    onClick = {},
                )
                HorizontalDivider()
            } else if (showSortOptions) {
                DropdownSectionHeader(text = "Sort")
                availableSortModes.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mode.label,
                                fontWeight = if (mode == sortMode) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSortModeChanged(mode)
                        },
                    )
                }
                HorizontalDivider()
            }
            DropdownSectionHeader(text = "View")
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = fadeReadItems,
                            onCheckedChange = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fade read books",
                            fontWeight = if (fadeReadItems) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                },
                onClick = {
                    onFadeReadItemsChanged(!fadeReadItems)
                },
            )
            HorizontalDivider()
            DropdownSectionHeader(text = "Grid size")
            gridSizeOptions.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.testTag(LibraryGridSizeOptionTagPrefix + option.cellSizeDp),
                    text = {
                        Text(
                            text = "${option.cellSizeDp}dp cards · ${option.columnCount} cols",
                            fontWeight = if (option.cellSizeDp == sanitizedGridCellSizeDp) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        if (option.cellSizeDp != sanitizedGridCellSizeDp) {
                            onGridCellSizeDpChanged(option.cellSizeDp)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DropdownSectionHeader(
    text: String,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = {},
        enabled = false,
    )
}
