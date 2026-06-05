package com.example.unifiedreader.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    snapshot: DownloadEntriesSnapshot,
    itemResolver: (String) -> ReadableItem?,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val sheetRecipe = UnifiedReaderThemeTokens.components.sheet
    val sheetShape = RoundedCornerShape(
        topStart = presentation.cardCornerRadius,
        topEnd = presentation.cardCornerRadius,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.unifiedReaderComponentTreatment(
            recipe = sheetRecipe,
            accentColor = semanticColors.readerAccent,
            secondaryColor = semanticColors.syncAccent,
            overlayColor = MaterialTheme.colorScheme.onSurface,
            shape = sheetShape,
        ),
        containerColor = semanticColors.surfaceBase,
        shape = sheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .unifiedReaderSurfacePattern(
                    treatment = sheetRecipe.surfaceTreatment,
                    accentColor = semanticColors.readerAccent,
                    overlayColor = MaterialTheme.colorScheme.onSurface,
                    intensity = sheetRecipe.patternIntensity,
                )
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = when {
                            snapshot.entries.isEmpty() -> "No downloads yet"
                            snapshot.activeEntries.isNotEmpty() -> "${snapshot.activeEntries.size} active"
                            snapshot.failedEntries.isNotEmpty() -> "${snapshot.failedEntries.size} need attention"
                            else -> "${snapshot.completedEntries.size} finished"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (snapshot.completedEntries.isNotEmpty()) {
                        IconButton(onClick = onClearCompleted) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear completed downloads",
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close downloads",
                        )
                    }
                }
            }
            if (snapshot.entries.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = "Queued, failed, and completed downloads will appear here.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    downloadSection(
                        title = "Active",
                        entries = snapshot.activeEntries,
                        onCancel = onCancel,
                        onRemove = onRemove,
                        onRetry = onRetry,
                        onOpenEntry = onOpenEntry,
                        itemResolver = itemResolver,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                    )
                    downloadSection(
                        title = "Failed",
                        entries = snapshot.failedEntries,
                        onCancel = onCancel,
                        onRemove = onRemove,
                        onRetry = onRetry,
                        onOpenEntry = onOpenEntry,
                        itemResolver = itemResolver,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                    )
                    downloadSection(
                        title = "History",
                        entries = snapshot.completedEntries,
                        onCancel = onCancel,
                        onRemove = onRemove,
                        onRetry = onRetry,
                        onOpenEntry = onOpenEntry,
                        itemResolver = itemResolver,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadSection(
    title: String,
    entries: List<DownloadEntry>,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    itemResolver: (String) -> ReadableItem?,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    if (entries.isEmpty()) return
    item(key = "${title}_header") {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(
        items = entries,
        key = DownloadEntry::id,
    ) { entry ->
        DownloadEntryCard(
            entry = entry,
            onCancel = onCancel,
            onRemove = onRemove,
            onRetry = onRetry,
            onOpenEntry = onOpenEntry,
            itemResolver = itemResolver,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
        )
    }
}

@Composable
private fun DownloadEntryCard(
    entry: DownloadEntry,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    itemResolver: (String) -> ReadableItem?,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    val displayItem = entry.displayItemForDownloads(itemResolver)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(enabled = entry.canOpen) {
                onOpenEntry(entry.id)
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(width = 42.dp, height = 58.dp),
                ) {
                    if (displayItem != null) {
                        LaunchedEffect(displayItem.id) {
                            onCoverVisible(displayItem, LibraryCoverImageVariant.ShelfRow)
                        }
                        ResolvedLibraryCover(
                            item = displayItem,
                            coverImageUrlProvider = coverImageUrlProvider,
                            imageVariant = LibraryCoverImageVariant.ShelfRow,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        Icon(
                            imageVector = entry.status.icon(),
                            contentDescription = null,
                            tint = entry.status.tint(),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = displayItem?.libraryPrimaryText() ?: entry.selectedItemTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.detailLabel(displayItem),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.currentItemTitle?.takeIf { entry.status == DownloadEntryStatus.Running }?.let { currentTitle ->
                        Text(
                            text = "Current: $currentTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    entry.errorMessage?.takeIf(String::isNotBlank)?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.canRetry) {
                        IconButton(onClick = { onRetry(entry.id) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry download",
                            )
                        }
                    }
                    if (entry.canRemove) {
                        IconButton(onClick = { onRemove(entry.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove download",
                            )
                        }
                    }
                    if (entry.canCancel) {
                        IconButton(onClick = { onCancel(entry.id) }) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Cancel download",
                            )
                        }
                    }
                }
            }
            when {
                entry.status == DownloadEntryStatus.Running && entry.totalItemCount > 1 -> {
                    LinearProgressIndicator(
                        progress = { entry.completedItemCount.toFloat() / entry.totalItemCount.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                entry.status == DownloadEntryStatus.Running -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                entry.status == DownloadEntryStatus.Completed && entry.totalItemCount > 1 -> {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadEntryStatus.tint() = when (this) {
    DownloadEntryStatus.Queued -> MaterialTheme.colorScheme.secondary
    DownloadEntryStatus.PausedConnectivity -> MaterialTheme.colorScheme.secondary
    DownloadEntryStatus.Running -> MaterialTheme.colorScheme.primary
    DownloadEntryStatus.Canceling -> MaterialTheme.colorScheme.secondary
    DownloadEntryStatus.Completed -> MaterialTheme.colorScheme.tertiary
    DownloadEntryStatus.Failed -> MaterialTheme.colorScheme.error
    DownloadEntryStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun DownloadEntryStatus.icon() = when (this) {
    DownloadEntryStatus.Queued -> Icons.Default.HourglassTop
    DownloadEntryStatus.PausedConnectivity -> Icons.Default.HourglassTop
    DownloadEntryStatus.Running -> Icons.Default.HourglassTop
    DownloadEntryStatus.Canceling -> Icons.Default.Stop
    DownloadEntryStatus.Completed -> Icons.Default.DownloadDone
    DownloadEntryStatus.Failed -> Icons.Default.ErrorOutline
    DownloadEntryStatus.Canceled -> Icons.Default.Stop
}

private fun DownloadEntryStatus.label(
    entry: DownloadEntry,
): String = when (this) {
    DownloadEntryStatus.Queued -> "Queued"
    DownloadEntryStatus.PausedConnectivity -> "Waiting for server"
    DownloadEntryStatus.Running -> entry.progressLabel
    DownloadEntryStatus.Canceling -> if (entry.completedItemCount > 0) {
        "Canceling after ${entry.progressLabel.lowercase()}"
    } else {
        "Canceling"
    }
    DownloadEntryStatus.Completed -> if (entry.totalItemCount == 1) "Ready to open" else "Downloaded ${entry.completedItemCount} files"
    DownloadEntryStatus.Failed -> "Failed after ${entry.progressLabel.lowercase()}"
    DownloadEntryStatus.Canceled -> if (entry.completedItemCount > 0) {
        "Canceled after ${entry.progressLabel.lowercase()}"
    } else {
        "Canceled"
    }
}

private fun DownloadEntry.detailLabel(
    item: ReadableItem?,
): String {
    val itemDetail = item?.librarySecondaryText()
        ?.takeIf { detail -> detail != item.libraryPrimaryText() && detail != item.title }
    val statusLabel = status.label(this)
    return when {
        itemDetail != null && status == DownloadEntryStatus.Completed -> itemDetail
        itemDetail != null -> "$statusLabel - $itemDetail"
        else -> statusLabel
    }
}
