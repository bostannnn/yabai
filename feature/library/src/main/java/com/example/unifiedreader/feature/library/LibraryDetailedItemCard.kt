package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.menu.LibraryItemOverflowMenu
import com.example.unifiedreader.feature.library.menu.ReadStatusMenuActionState
import com.example.unifiedreader.feature.library.menu.buildLibraryItemMenuActions
import com.example.unifiedreader.feature.library.menu.buildReaderModeMenuState
import com.example.unifiedreader.feature.library.menu.buildReadingDirectionMenuState
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

@Composable
internal fun DetailedItemCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    seriesReadingDirection: ComicReadingDirection? = null,
    seriesReaderMode: ComicReaderMode? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onClick: () -> Unit,
    onSeriesReadingDirectionSelected: ((ComicReadingDirection) -> Unit)? = null,
    onSeriesReaderModeSelected: ((ComicReaderMode) -> Unit)? = null,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            val readingDirectionMenuState = buildReadingDirectionMenuState(
                seriesReadingDirection = seriesReadingDirection,
                onSeriesReadingDirectionSelected = onSeriesReadingDirectionSelected,
            )
            val readerModeMenuState = buildReaderModeMenuState(
                seriesReaderMode = seriesReaderMode,
                onSeriesReaderModeSelected = onSeriesReaderModeSelected,
            )
            val menuActions = buildLibraryItemMenuActions(
                isDownloading = isDownloading,
                readStatusActionState = readStatusActionState,
                onDeleteRequested = onDeleteRequested,
                onDownloadRequested = onDownloadRequested,
                onDeleteDownloadedRequested = onDeleteDownloadedRequested,
            )
            val hasMenuContent = readingDirectionMenuState != null ||
                readerModeMenuState != null ||
                menuActions.isNotEmpty()
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
                        coverUrl = coverImageUrlProvider(item, LibraryCoverImageVariant.Detailed),
                        isDownloaded = isDownloaded,
                        isRead = progress?.isComplete == true,
                        imageVariant = LibraryCoverImageVariant.Detailed,
                        modifier = Modifier
                            .width(96.dp)
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
                        readerModeState = readerModeMenuState,
                    )
                }
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
