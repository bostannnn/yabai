package com.example.unifiedreader.feature.library

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
internal fun LibrarySortSelector(
    sortMode: LibrarySortMode,
    onSortModeChanged: (LibrarySortMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        FilterChip(
            selected = sortMode != defaultLibrarySortMode,
            onClick = { expanded = true },
            label = {
                Text(
                    text = "Sort: ${sortMode.label}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
        }
    }
}
