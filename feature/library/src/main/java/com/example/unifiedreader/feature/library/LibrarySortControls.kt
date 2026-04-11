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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.unifiedreader.core.model.LibrarySortMode

@Composable
internal fun LibraryBrowseOptionsSelector(
    sortMode: LibrarySortMode,
    viewMode: LibraryViewMode,
    showSortOptions: Boolean,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onViewModeChanged: (LibraryViewMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = viewMode != LibraryViewMode.Grid ||
                (showSortOptions && sortMode != defaultLibrarySortMode),
            onClick = { expanded = true },
            label = {
                Text(
                    text = if (showSortOptions) {
                        "Browse: ${sortMode.label} · ${viewMode.label}"
                    } else {
                        "View: ${viewMode.label}"
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
