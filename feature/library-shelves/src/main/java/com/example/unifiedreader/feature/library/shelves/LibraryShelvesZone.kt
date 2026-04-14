package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText

@Composable
fun LibraryShelvesZone(
    state: LibraryShelvesState,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasContent) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useTabletRow = maxWidth >= TabletShelvesBreakpoint && countVisibleShelves(state) > 1
        if (useTabletRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TabletShelfCardHeight),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                state.continueReading?.let { shelfItem ->
                    Box(
                        modifier = Modifier
                            .weight(if (countVisibleShelves(state) >= 3) 2f else 1.7f)
                            .fillMaxHeight(),
                    ) {
                        ContinueReadingCard(
                            shelfItem = shelfItem,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
                if (state.nextInSeries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(if (state.continueReading != null && state.recentlyFinished.isNotEmpty()) 1.2f else 1f)
                            .fillMaxHeight(),
                    ) {
                        NextInSeriesCard(
                            items = state.nextInSeries,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
                if (state.recentlyFinished.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(if (state.continueReading != null) 0.9f else 1f)
                            .fillMaxHeight(),
                    ) {
                        RecentlyFinishedCard(
                            items = state.recentlyFinished,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.continueReading?.let { shelfItem ->
                    ContinueReadingCard(
                        shelfItem = shelfItem,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onItemSelected = onItemSelected,
                    )
                }
                if (state.nextInSeries.isNotEmpty()) {
                    NextInSeriesCard(
                        items = state.nextInSeries,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onItemSelected = onItemSelected,
                    )
                }
                if (state.recentlyFinished.isNotEmpty()) {
                    RecentlyFinishedCard(
                        items = state.recentlyFinished,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onItemSelected = onItemSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    shelfItem: ContinueReadingShelfItem,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
) {
    val item = shelfItem.item
    val progress = shelfItem.progress
    ShelfCard(
        title = "Continue Reading",
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = TabletShelfCardMinHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                modifier = Modifier
                    .widthIn(min = 92.dp, max = 128.dp)
                    .fillMaxWidth(0.26f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = item.libraryPrimaryText(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.librarySecondaryText()?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = progress.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = item.sourceType.displayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = progress.detailLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { onItemSelected(item) },
                ) {
                    Text(text = "Resume")
                }
            }
        }
    }
}

@Composable
private fun NextInSeriesCard(
    items: List<NextInSeriesShelfItem>,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
) {
    ShelfCard(
        title = "Up Next",
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = TabletShelfCardMinHeight),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { shelfItem ->
                CompactShelfRow(
                    item = shelfItem.item,
                    label = shelfItem.item.libraryPrimaryText(),
                    supportingText = shelfItem.item.librarySecondaryText()
                        ?: shelfItem.item.title,
                    trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                )
            }
        }
    }
}

@Composable
private fun RecentlyFinishedCard(
    items: List<RecentlyFinishedShelfItem>,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
) {
    ShelfCard(
        title = "Recently Finished",
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = TabletShelfCardMinHeight),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { shelfItem ->
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    shelfItem.progress.lastReadAtEpochMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                CompactShelfRow(
                    item = shelfItem.item,
                    label = shelfItem.item.libraryPrimaryText(),
                    supportingText = shelfItem.item.librarySecondaryText()
                        ?: shelfItem.progress.label,
                    trailingText = relativeTime,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                )
            }
        }
    }
}

@Composable
private fun CompactShelfRow(
    item: ReadableItem,
    label: String,
    supportingText: String,
    trailingText: String,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .heightIn(min = CompactShelfRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfCover(
            item = item,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            modifier = Modifier.size(width = 42.dp, height = 58.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShelfCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun ShelfCover(
    item: ReadableItem,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.id) {
        onCoverVisible(item)
    }
    val imageUrl = coverImageUrlProvider(item)
    if (imageUrl.isNullOrBlank()) {
        PlaceholderCover(
            title = item.title,
            modifier = modifier,
        )
        return
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = item.title,
        modifier = modifier
            .aspectRatio(CoverAspectRatio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun PlaceholderCover(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .aspectRatio(CoverAspectRatio)
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun countVisibleShelves(
    state: LibraryShelvesState,
): Int = listOf(
    state.continueReading != null,
    state.nextInSeries.isNotEmpty(),
    state.recentlyFinished.isNotEmpty(),
).count { visible -> visible }

private val TabletShelvesBreakpoint = 840.dp
private val TabletShelfCardHeight = 292.dp
private val TabletShelfCardMinHeight = TabletShelfCardHeight
private val CompactShelfRowMinHeight = 64.dp
private const val CoverAspectRatio = 0.72f
