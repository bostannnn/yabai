package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

internal data class ItemMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

internal fun buildLibraryItemMenuActions(
    isDownloading: Boolean,
    seriesReadingDirection: ComicReadingDirection?,
    onSeriesReadingDirectionSelected: ((ComicReadingDirection) -> Unit)?,
    onDeleteRequested: (() -> Unit)?,
    onDownloadRequested: (() -> Unit)?,
    onDeleteDownloadedRequested: (() -> Unit)?,
): List<ItemMenuAction> = buildList {
    if (seriesReadingDirection != null && onSeriesReadingDirectionSelected != null) {
        ComicReadingDirection.entries.forEach { direction ->
            add(
                ItemMenuAction(
                    label = "Reading: ${direction.label}",
                    selected = direction == seriesReadingDirection,
                    onClick = { onSeriesReadingDirectionSelected(direction) },
                ),
            )
        }
    }
    if (isDownloading) {
        add(ItemMenuAction(label = "Downloading...", enabled = false, onClick = {}))
    }
    onDownloadRequested?.let { add(ItemMenuAction(label = "Download file", onClick = it)) }
    onDeleteDownloadedRequested?.let { add(ItemMenuAction(label = "Delete local file", onClick = it)) }
    onDeleteRequested?.let { add(ItemMenuAction(label = "Remove local file", onClick = it)) }
}

@Composable
internal fun LibraryItemOverflowMenu(
    modifier: Modifier = Modifier,
    actions: List<ItemMenuAction>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val menuButtonShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = "More actions"
                    role = Role.Button
                }
                .clickable(onClick = { expanded = true }),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = menuButtonShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.22f),
                    )
                    .clip(menuButtonShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                        shape = menuButtonShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (action.selected) "• ${action.label}" else action.label,
                        )
                    },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
