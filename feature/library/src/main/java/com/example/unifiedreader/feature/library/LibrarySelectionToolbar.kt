package com.example.unifiedreader.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LibrarySelectionToolbar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    selectableCount: Int,
    onSelectionModeChanged: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = isSelectionMode,
            enabled = selectableCount > 0,
            onClick = { onSelectionModeChanged(!isSelectionMode) },
            leadingIcon = {
                Icon(
                    imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            label = {
                Text(
                    text = if (isSelectionMode) "Cancel" else "Select",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        if (isSelectionMode) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                enabled = selectedCount < selectableCount,
                onClick = onSelectAll,
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "All")
            }
            TextButton(
                enabled = selectedCount > 0,
                onClick = onClearSelection,
            ) {
                Text(text = "Clear")
            }
            TextButton(
                enabled = selectedCount > 0,
                onClick = onDownloadSelected,
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Download")
            }
        }
    }
}
