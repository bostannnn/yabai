package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.supportsManualReadStatusAction
import com.example.unifiedreader.feature.library.menu.LibraryItemOverflowMenu
import com.example.unifiedreader.feature.library.menu.ReadStatusMenuActionState
import com.example.unifiedreader.feature.library.menu.buildReadingDirectionMenuState
import com.example.unifiedreader.feature.library.menu.buildLibraryItemMenuActions
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

internal fun itemDeleteRequest(
    item: ReadableItem,
    onDeleteCandidateSelected: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.isLocalImport) {
    { onDeleteCandidateSelected(item) }
} else {
    null
}

internal fun ReadableItem.isDownloaded(
    downloadedItemIds: Set<String>,
): Boolean = id in downloadedItemIds

internal fun itemDownloadRequest(
    item: ReadableItem,
    downloadableItemIds: Set<String>,
    onDownloadItem: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.id in downloadableItemIds) {
    { onDownloadItem(item) }
} else {
    null
}

internal fun itemDownloadedDeleteRequest(
    item: ReadableItem,
    deletableDownloadedItemIds: Set<String>,
    onDeleteDownloadedItem: (ReadableItem) -> Unit,
): (() -> Unit)? = if (item.id in deletableDownloadedItemIds) {
    { onDeleteDownloadedItem(item) }
} else {
    null
}

internal fun itemReadStatusActionState(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    onSetReadStatus: (ReadableItem, Boolean) -> Unit,
): ReadStatusMenuActionState? {
    if (!item.supportsManualReadStatusAction()) return null
    val isRead = progress?.isComplete == true
    return ReadStatusMenuActionState(
        isRead = isRead,
        onToggleRequested = { onSetReadStatus(item, !isRead) },
    )
}

@Composable
internal fun DetailedItemCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    seriesReadingDirection: ComicReadingDirection? = null,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onClick: () -> Unit,
    onSeriesReadingDirectionSelected: ((ComicReadingDirection) -> Unit)? = null,
    readStatusActionState: ReadStatusMenuActionState? = null,
    onDeleteRequested: (() -> Unit)?,
    onDownloadRequested: (() -> Unit)?,
    onDeleteDownloadedRequested: (() -> Unit)?,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-detailed-card-${item.id}"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            val coverWidth = (maxWidth * 0.36f).coerceIn(72.dp, 104.dp)
            val readingDirectionMenuState = buildReadingDirectionMenuState(
                seriesReadingDirection = seriesReadingDirection,
                onSeriesReadingDirectionSelected = onSeriesReadingDirectionSelected,
            )
            val menuActions = buildLibraryItemMenuActions(
                isDownloading = isDownloading,
                readStatusActionState = readStatusActionState,
                onDeleteRequested = onDeleteRequested,
                onDownloadRequested = onDownloadRequested,
                onDeleteDownloadedRequested = onDeleteDownloadedRequested,
            )
            val hasMenuContent = readingDirectionMenuState != null || menuActions.isNotEmpty()
            val menuSpace = if (hasMenuContent) 48.dp else 0.dp
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = menuSpace),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LibraryCover(
                        item = item,
                        coverUrl = coverImageUrlProvider(item),
                        isDownloaded = isDownloaded,
                        isRead = progress?.isComplete == true,
                        onCoverVisible = onCoverVisible,
                        modifier = Modifier
                            .width(coverWidth)
                            .aspectRatio(2f / 3f),
                    )
                    DetailedItemTextBlock(
                        item = item,
                        progress = progress,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (hasMenuContent) {
                    LibraryItemOverflowMenu(
                        modifier = Modifier.align(Alignment.TopEnd),
                        actions = menuActions,
                        readingDirectionState = readingDirectionMenuState,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    seriesReadingDirection: ComicReadingDirection? = null,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onClick: () -> Unit,
    onSeriesReadingDirectionSelected: ((ComicReadingDirection) -> Unit)? = null,
    readStatusActionState: ReadStatusMenuActionState? = null,
    onDeleteRequested: (() -> Unit)?,
    onDownloadRequested: (() -> Unit)?,
    onDeleteDownloadedRequested: (() -> Unit)?,
) {
    val showCoverProgress = progress?.shouldShowCoverProgress == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}")
            .clickable(onClick = onClick),
    ) {
        LibraryCover(
            item = item,
            coverUrl = coverImageUrlProvider(item),
            isDownloaded = isDownloaded,
            isRead = progress?.isComplete == true,
            downloadBadgeBottomInset = if (showCoverProgress) 2.dp else 0.dp,
            onCoverVisible = onCoverVisible,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        progress?.takeIf { showCoverProgress }?.let { summary ->
            LibraryCoverProgressBar(
                progress = summary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
        val readingDirectionMenuState = buildReadingDirectionMenuState(
            seriesReadingDirection = seriesReadingDirection,
            onSeriesReadingDirectionSelected = onSeriesReadingDirectionSelected,
        )
        val menuActions = buildLibraryItemMenuActions(
            isDownloading = isDownloading,
            readStatusActionState = readStatusActionState,
            onDeleteRequested = onDeleteRequested,
            onDownloadRequested = onDownloadRequested,
            onDeleteDownloadedRequested = onDeleteDownloadedRequested,
        )
        if (readingDirectionMenuState != null || menuActions.isNotEmpty()) {
            LibraryItemOverflowMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                actions = menuActions,
                readingDirectionState = readingDirectionMenuState,
            )
        }
    }
}

@Composable
private fun LibraryCover(
    item: ReadableItem,
    coverUrl: String? = item.coverUrl,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    downloadBadgeBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onCoverVisible: (ReadableItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coverShape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    val coverBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)

    LaunchedEffect(item.id, item.sourceType, coverUrl) {
        if (
            item.sourceType in setOf(SourceType.GRIMMORY, SourceType.STUMP) &&
            !coverUrl.orEmpty().startsWith("file:", ignoreCase = true)
        ) {
            onCoverVisible(item)
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = coverShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            )
            .clip(coverShape)
            .background(
                brush = Brush.linearGradient(listOf(Color(0xFF425E6B), Color(0xFF141A20))),
            )
            .border(
                width = 1.dp,
                color = coverBorderColor,
                shape = coverShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = formatBadgeText(item.format),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.22f),
                            0.04f to Color.White.copy(alpha = 0.08f),
                            0.11f to Color.Transparent,
                            0.88f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.12f),
                            0.20f to Color.Transparent,
                            0.82f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.10f),
                        ),
                    ),
                ),
        )
        if (isRead) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
            )
            LibraryReadStatusBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
        if (isDownloaded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp + downloadBadgeBottomInset),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailedItemTextBlock(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val primaryText = itemPrimaryText(item)
        val secondaryText = itemSecondaryText(item)
        val tertiaryText = itemTertiaryText(item)
        val pageCountText = itemPageCountText(item, progress)

        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = itemPrimaryMaxLines(item),
            overflow = TextOverflow.Ellipsis,
        )

        secondaryText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        tertiaryText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        pageCountText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        progress?.let {
            Box(modifier = Modifier.padding(top = 2.dp)) {
                ProgressSummary(progress = it)
            }
        }
    }
}
