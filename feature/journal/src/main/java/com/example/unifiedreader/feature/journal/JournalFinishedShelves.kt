package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

internal const val JournalFinishedShelfPreviewLimit = 50

internal fun finishedItemsNeedingRating(
    overview: JournalPeriodOverview,
): List<JournalItemSummary> = overview.finishedItems.filter { summary -> summary.entry?.rating == null }

internal fun journalFinishedShelfPreviewItems(
    items: List<JournalItemSummary>,
): List<JournalItemSummary> = items.take(JournalFinishedShelfPreviewLimit)

@Composable
internal fun FinishedItemsRow(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onShowAll: (List<JournalItemSummary>, String) -> Unit,
) {
    JournalFinishedShelf(
        title = "Finished",
        emptyText = "No finished items yet",
        items = overview.finishedItems,
        seeAllLabel = "See all ${overview.periodLabel()} finished",
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onOpenItem = onOpenItem,
        onShowAll = onShowAll,
    )
}

@Composable
internal fun NeedsRatingItemsRow(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onShowAll: (List<JournalItemSummary>, String) -> Unit,
) {
    JournalFinishedShelf(
        title = "Needs rating",
        emptyText = "No unrated finished items",
        items = finishedItemsNeedingRating(overview),
        seeAllLabel = "See all ${overview.periodLabel()} unrated",
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onOpenItem = onOpenItem,
        onShowAll = onShowAll,
    )
}

@Composable
private fun JournalFinishedShelf(
    title: String,
    emptyText: String,
    items: List<JournalItemSummary>,
    seeAllLabel: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onShowAll: (List<JournalItemSummary>, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatsSectionLabel(title)
            if (items.size > JournalFinishedShelfPreviewLimit) {
                TextButton(onClick = { onShowAll(items, seeAllLabel) }) {
                    Text(text = seeAllLabel)
                }
            }
        }
        if (items.isEmpty()) {
            EmptyStatsMessage(emptyText)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(journalFinishedShelfPreviewItems(items), key = { summary -> summary.item.itemId }) { summary ->
                    FinishedItemCard(
                        summary = summary,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onOpen = { onOpenItem(summary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishedItemCard(
    summary: JournalItemSummary,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JournalCoverArt(
            item = summary.item,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(0.68f),
        )
        Text(
            text = summary.item.displayTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        summary.item.displaySubtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun JournalPeriodOverview.periodLabel(): String = when (period) {
    JournalPeriod.MONTH -> monthLabel().substringBefore(" ")
    JournalPeriod.YEAR -> yearLabel()
}
