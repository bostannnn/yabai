package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText

@Composable
internal fun ContinueReadingShelfSection(
    items: List<ContinueReadingShelfItem>,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShelfSectionCard(
        title = "Continue Reading",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        modifier = modifier,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = items, key = { it.item.id }) { shelfItem ->
                ContinueReadingCarouselCard(
                    shelfItem = shelfItem,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                )
            }
        }
    }
}

@Composable
internal fun NextInSeriesShelfSection(
    items: List<NextInSeriesShelfItem>,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShelfSectionCard(
        title = "Up Next",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        modifier = modifier,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = items, key = { it.item.id }) { shelfItem ->
                ShelfPreviewCard(
                    item = shelfItem.item,
                    title = shelfItem.item.libraryPrimaryText(),
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
internal fun RecentlyFinishedShelfSection(
    items: List<RecentlyFinishedShelfItem>,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShelfSectionCard(
        title = "Recently Finished",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        modifier = modifier,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = items, key = { it.item.id }) { shelfItem ->
                ShelfPreviewCard(
                    item = shelfItem.item,
                    title = shelfItem.item.libraryPrimaryText(),
                    supportingText = shelfItem.item.librarySecondaryText()
                        ?: shelfItem.progress.label,
                    trailingText = DateUtils.getRelativeTimeSpanString(
                        shelfItem.progress.lastReadAtEpochMillis,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                )
            }
        }
    }
}

@Composable
private fun ShelfSectionCard(
    title: String,
    count: Int,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
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
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = shelfCountLabel(count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSeeAll) {
                    TextButton(onClick = onSeeAll) {
                        Text(text = "See all")
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun ContinueReadingCarouselCard(
    shelfItem: ContinueReadingShelfItem,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onClick: () -> Unit,
) {
    ShelfPreviewCard(
        item = shelfItem.item,
        title = shelfItem.item.libraryPrimaryText(),
        supportingText = shelfItem.item.librarySecondaryText()
            ?: shelfItem.progress.label,
        trailingText = shelfItem.progress.detailLabel,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onClick = onClick,
        progressFraction = shelfItem.progress.fraction,
    )
}

private fun shelfCountLabel(
    count: Int,
): String = when (count) {
    1 -> "1 item"
    else -> "$count items"
}

private const val SeeAllThreshold = 4
