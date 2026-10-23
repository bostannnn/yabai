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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLinearProgress
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

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
    // A side sheet like the filters and the book page; a bottom sheet on phones.
    val wide = currentAppWindowWidth() >= AppWindowWidth.Medium
    AppSideSheet(
        onDismissRequest = onDismiss,
    ) {
        // Plays the sheet's exit before it closes.
        val closeSheet = { dismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (wide) 20.dp else 0.dp, bottom = 24.dp),
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
                        AppIconButton(onClick = onClearCompleted) {
                            Icon(
                                imageVector = AppIcons.DeleteSweep,
                                contentDescription = "Clear completed downloads",
                            )
                        }
                    }
                    AppIconButton(onClick = closeSheet) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Close downloads",
                        )
                    }
                }
            }
            if (snapshot.entries.isEmpty()) {
                AppPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
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
    // A flat row on the sheet, not a boxed card; the ripple is clipped to the control radius.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = entry.canOpen) {
                onOpenEntry(entry.id)
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
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
                            modifier = Modifier.fillMaxSize(),
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
                        AppIconButton(onClick = { onRetry(entry.id) }) {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = "Retry download",
                            )
                        }
                    }
                    if (entry.canRemove) {
                        AppIconButton(onClick = { onRemove(entry.id) }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = "Remove download",
                            )
                        }
                    }
                    if (entry.canCancel) {
                        AppIconButton(onClick = { onCancel(entry.id) }) {
                            Icon(
                                imageVector = AppIcons.Stop,
                                contentDescription = "Cancel download",
                            )
                        }
                    }
                }
            }
            when {
                entry.status == DownloadEntryStatus.Running && entry.totalItemCount > 1 -> {
                    AppLinearProgress(
                        progress = { entry.completedItemCount.toFloat() / entry.totalItemCount.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                entry.status == DownloadEntryStatus.Running -> {
                    AppLinearProgress(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // A finished download shows its done icon and file count, not a full bar.
            }
        }
    }
}

@Composable
private fun DownloadEntryStatus.tint() = when (this) {
    DownloadEntryStatus.Queued,
    DownloadEntryStatus.PausedConnectivity,
    DownloadEntryStatus.Canceling,
    DownloadEntryStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
    DownloadEntryStatus.Running -> UnifiedReaderThemeTokens.colors.readerAccent
    DownloadEntryStatus.Completed -> UnifiedReaderThemeTokens.colors.syncAccent
    DownloadEntryStatus.Failed -> MaterialTheme.colorScheme.error
}


@Composable
private fun DownloadEntryStatus.icon() = when (this) {
    DownloadEntryStatus.Queued -> AppIcons.HourglassTop
    DownloadEntryStatus.PausedConnectivity -> AppIcons.HourglassTop
    DownloadEntryStatus.Running -> AppIcons.HourglassTop
    DownloadEntryStatus.Canceling -> AppIcons.Stop
    DownloadEntryStatus.Completed -> AppIcons.DownloadDone
    DownloadEntryStatus.Failed -> AppIcons.ErrorOutline
    DownloadEntryStatus.Canceled -> AppIcons.Stop
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
