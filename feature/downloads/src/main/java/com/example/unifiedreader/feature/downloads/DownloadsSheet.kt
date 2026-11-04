package com.example.unifiedreader.feature.downloads

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.unit.IntOffset
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton

/**
 * The Downloads sheet: what is downloading now (a raised block with the file it is on and a bar),
 * what waits, what couldn't download and why, and what finished, by day. A finished row opens the
 * book's page; Read on a single book opens it straight away.
 */
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
    onReadEntry: (String) -> Unit,
    onOpenBookPage: (itemId: String, source: SourceType) -> Unit,
) {
    // A finished row's book page opens once the sheet has slid away, instead of the sheet vanishing under it.
    val afterClose = remember { arrayOfNulls<() -> Unit>(1) }
    AppSideSheet(
        onDismissRequest = {
            val action = afterClose[0]
            afterClose[0] = null
            if (action != null) action() else onDismiss()
        },
    ) {
        // Plays the sheet's exit before it closes.
        val closeSheet = { dismiss() }
        val openBookPage: (String, SourceType) -> Unit = { itemId, source ->
            afterClose[0] = { onOpenBookPage(itemId, source) }
            dismiss()
        }
        AppPanelHeader(
            title = "Downloads",
            subtitle = downloadsSheetSummary(snapshot),
            onClose = closeSheet,
        )
        if (snapshot.entries.isEmpty()) {
            AppEmptyState(
                title = "Nothing downloaded",
                body = "Downloaded books open without the server. Use Download on a book's page, or long-press covers to pick several.",
            )
            return@AppSideSheet
        }
        // The clock for "Today" and the times, read again whenever the entries change.
        val now = remember(snapshot) { System.currentTimeMillis() }
        val bySection = remember(snapshot) { snapshot.entries.groupBy(DownloadEntry::sheetSection) }
        val finishedDays = remember(snapshot, now) { finishedDownloadsByDay(snapshot.entries, now) }
        val rowContext = DownloadRowContext(itemResolver, coverImageUrlProvider, onCoverVisible)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            bySection[DownloadsSheetSection.Downloading]?.let { entries ->
                sectionHeading(DownloadsSheetSection.Downloading)
                items(entries, key = DownloadEntry::id) { entry ->
                    DownloadRunningBlock(entry = entry, context = rowContext, onCancel = onCancel, modifier = Modifier.downloadRowMotion(this))
                }
            }
            bySection[DownloadsSheetSection.Waiting]?.let { entries ->
                sectionHeading(DownloadsSheetSection.Waiting)
                items(entries, key = DownloadEntry::id) { entry ->
                    val item = rowContext.displayItem(entry)
                    DownloadRow(entry = entry, item = item, line = entry.waitingLabel(item), context = rowContext, modifier = Modifier.downloadRowMotion(this)) {
                        AppIconButton(onClick = { onCancel(entry.id) }) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "Cancel download",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            bySection[DownloadsSheetSection.Failed]?.let { entries ->
                sectionHeading(DownloadsSheetSection.Failed)
                items(entries, key = DownloadEntry::id) { entry ->
                    DownloadRow(
                        entry = entry,
                        item = rowContext.displayItem(entry),
                        line = entry.failureLabel(),
                        lineIsError = true,
                        context = rowContext,
                        modifier = Modifier.downloadRowMotion(this),
                    ) {
                        RetryAndRemove(entry = entry, onRetry = onRetry, onRemove = onRemove)
                    }
                }
            }
            if (finishedDays.isNotEmpty()) {
                sectionHeading(DownloadsSheetSection.Finished) {
                    // Clears the list; the files stay on the device.
                    AppTextButton(onClick = onClearCompleted) {
                        Text(text = "Clear list", fontWeight = FontWeight.SemiBold)
                    }
                }
                finishedDays.forEach { day ->
                    item(key = "day-${day.label}") {
                        AppSheetLabel(text = day.label)
                    }
                    items(day.entries, key = DownloadEntry::id) { entry ->
                        val item = rowContext.displayItem(entry)
                        DownloadRow(
                            entry = entry,
                            item = item,
                            line = entry.finishedLabel(item, now),
                            context = rowContext,
                            onClick = { openBookPage(entry.selectedItemId, entry.sourceType) },
                            modifier = Modifier.downloadRowMotion(this),
                        ) {
                            when {
                                entry.status == DownloadEntryStatus.Canceled ->
                                    RetryAndRemove(entry = entry, onRetry = onRetry, onRemove = onRemove)
                                entry.opensDirectly ->
                                    DownloadRowButton(label = "Read", onClick = { onReadEntry(entry.id) })
                                else -> Icon(
                                    imageVector = AppIcons.NavigateNext,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A download moves between sections (running, finished, failed) and leaves on Clear list instead of cutting. */
private fun Modifier.downloadRowMotion(scope: LazyItemScope): Modifier = with(scope) {
    this@downloadRowMotion.animateItem(
        fadeInSpec = AppMotion.effects(),
        placementSpec = AppMotion.spatial(IntOffset.VisibilityThreshold),
        fadeOutSpec = AppMotion.effects(),
    )
}

private val RunningPanelMargin = 12.dp

/** What every row needs to draw its cover and title. */
private class DownloadRowContext(
    private val itemResolver: (String) -> ReadableItem?,
    val coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    val onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    fun displayItem(entry: DownloadEntry): ReadableItem? = entry.displayItemForDownloads(itemResolver)
}

private fun LazyListScope.sectionHeading(
    section: DownloadsSheetSection,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    item(key = "section-${section.name}") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = AppLayout.inset, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Two levels in this sheet: a section over its days, so the section takes the page's
            // group label and a day the sheet's label.
            AppGroupLabel(text = section.title, modifier = Modifier.weight(1f))
            action?.invoke(this)
        }
    }
}

@Composable
private fun DownloadRunningBlock(
    entry: DownloadEntry,
    context: DownloadRowContext,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = context.displayItem(entry)
    AppPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RunningPanelMargin, vertical = 4.dp),
    ) {
        Row(
            // The cover lines up with the covers of the rows under it, on the sheet's inset.
            modifier = Modifier.padding(AppLayout.inset - RunningPanelMargin),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DownloadCover(item = item, entry = entry, context = context, width = 64.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item?.libraryPrimaryText() ?: entry.selectedItemTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        (downloadItemDetail(item) ?: entry.fileCountLabel())?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (entry.canCancel) {
                        DownloadRowButton(label = "Stop", onClick = { onCancel(entry.id) })
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = entry.activityLabel(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // One file: the app counts files, not bytes, so the bar moves on its own.
                    AppProgressBar(fraction = entry.fileProgressFraction(), modifier = Modifier.weight(1f))
                    entry.fileProgressLabel()?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    item: ReadableItem?,
    line: String,
    context: DownloadRowContext,
    lineIsError: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    // A flat row on the sheet: its text on the sheet's inset, the ripple a little wider.
    AppBookRow(
        title = item?.libraryPrimaryText() ?: entry.selectedItemTitle,
        modifier = modifier.padding(horizontal = 8.dp),
        onClick = onClick,
        contentPadding = PaddingValues(start = AppLayout.inset - 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        cover = { DownloadCover(item = item, entry = entry, context = context, width = AppBookRowDefaults.coverWidth) },
        supporting = {
            AppBookRowLine(
                text = line,
                color = if (lineIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                // An error, or a stopped download beside its two buttons, wraps instead of losing its end.
                maxLines = when {
                    lineIsError -> 3
                    entry.status == DownloadEntryStatus.Canceled -> 2
                    else -> 1
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
private fun DownloadCover(
    item: ReadableItem?,
    entry: DownloadEntry,
    context: DownloadRowContext,
    width: Dp,
) {
    Box(modifier = Modifier.size(width = width, height = width * 1.5f)) {
        if (item != null) {
            LaunchedEffect(item.id) {
                context.onCoverVisible(item, LibraryCoverImageVariant.ShelfRow)
            }
            ResolvedLibraryCover(
                item = item,
                coverImageUrlProvider = context.coverImageUrlProvider,
                imageVariant = LibraryCoverImageVariant.ShelfRow,
                showReadBadge = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (entry.status == DownloadEntryStatus.Failed) AppIcons.ErrorOutline else AppIcons.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun RetryAndRemove(
    entry: DownloadEntry,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (entry.canRetry) {
        DownloadRowButton(label = "Try again", onClick = { onRetry(entry.id) })
    }
    if (entry.canRemove) {
        // Deletes the files this download got.
        AppIconButton(onClick = { onRemove(entry.id) }) {
            Icon(
                imageVector = AppIcons.Delete,
                contentDescription = "Remove download and its files",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A 36 dp tonal pill: Stop, Try again, Read. */
@Composable
private fun DownloadRowButton(label: String, onClick: () -> Unit) {
    AppTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
