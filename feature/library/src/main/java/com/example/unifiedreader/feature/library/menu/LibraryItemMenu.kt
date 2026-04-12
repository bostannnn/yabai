package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatTextdirectionLToR
import androidx.compose.material.icons.automirrored.filled.FormatTextdirectionRToL
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

internal data class ItemMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

internal data class ReadingDirectionMenuState(
    val selectedDirection: ComicReadingDirection,
    val onDirectionSelected: (ComicReadingDirection) -> Unit,
)

internal fun buildReadingDirectionMenuState(
    seriesReadingDirection: ComicReadingDirection?,
    onSeriesReadingDirectionSelected: ((ComicReadingDirection) -> Unit)?,
): ReadingDirectionMenuState? {
    if (seriesReadingDirection == null || onSeriesReadingDirectionSelected == null) return null
    return ReadingDirectionMenuState(
        selectedDirection = seriesReadingDirection,
        onDirectionSelected = onSeriesReadingDirectionSelected,
    )
}

internal fun buildLibraryItemMenuActions(
    isDownloading: Boolean,
    onDeleteRequested: (() -> Unit)?,
    onDownloadRequested: (() -> Unit)?,
    onDeleteDownloadedRequested: (() -> Unit)?,
): List<ItemMenuAction> = buildList {
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
    readingDirectionState: ReadingDirectionMenuState? = null,
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
            readingDirectionState?.let { state ->
                ReadingDirectionMenuRow(
                    state = state,
                    onDirectionSelected = { direction ->
                        expanded = false
                        state.onDirectionSelected(direction)
                    },
                )
                if (actions.isNotEmpty()) {
                    HorizontalDivider()
                }
            }
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(text = action.label) },
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

@Composable
private fun ReadingDirectionMenuRow(
    state: ReadingDirectionMenuState,
    onDirectionSelected: (ComicReadingDirection) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComicReadingDirection.entries.forEach { direction ->
            FilledTonalIconButton(
                onClick = { onDirectionSelected(direction) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (direction == state.selectedDirection) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (direction == state.selectedDirection) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Icon(
                    imageVector = direction.icon(),
                    contentDescription = "Reading ${direction.label}",
                )
            }
        }
    }
}

private fun ComicReadingDirection.icon(): ImageVector = when (this) {
    ComicReadingDirection.LTR -> Icons.AutoMirrored.Filled.FormatTextdirectionLToR
    ComicReadingDirection.RTL -> Icons.AutoMirrored.Filled.FormatTextdirectionRToL
}
