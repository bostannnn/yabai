package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.unifiedreader.core.model.LibrarySortMode

internal const val LibraryGridSizeOptionTagPrefix = "library-grid-size-option-"

@Composable
internal fun LibraryBrowseOptionsSelector(
    sortMode: LibrarySortMode,
    viewMode: LibraryViewMode,
    gridCellSizeDp: Int,
    gridSizeOptions: List<LibraryGridSizeOption>,
    showSortOptions: Boolean,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onViewModeChanged: (LibraryViewMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    Box {
        FilterChip(
            selected = viewMode != LibraryViewMode.Grid ||
                sanitizedGridCellSizeDp != defaultLibraryGridCellSizeDp ||
                (showSortOptions && sortMode != defaultLibrarySortMode),
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (showSortOptions) {
                        "Browse: ${sortMode.label} · ${viewMode.label} · ${sanitizedGridCellSizeDp}dp"
                    } else {
                        "View: ${viewMode.label} · ${sanitizedGridCellSizeDp}dp"
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
            if (showSortOptions) {
                DropdownSectionHeader(text = "Sort")
                LibrarySortMode.entries.forEach { mode ->
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
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = mode.label,
                            fontWeight = if (mode == viewMode) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onViewModeChanged(mode)
                    },
                )
            }
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
