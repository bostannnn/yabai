package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.RatingBadge
import com.example.unifiedreader.feature.library.ratingBadgeState

internal fun finishedItemsNeedingRating(
    overview: JournalPeriodOverview,
): List<JournalItemSummary> = overview.finishedItems.filter { summary -> summary.entry?.rating == null }

@Composable
internal fun FinishedItemsRow(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
) {
    JournalFinishedShelf(
        title = "Finished",
        summary = "${overview.finishedItems.size} in ${overview.periodName()}",
        items = overview.finishedItems,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onOpenItem = onOpenItem,
    )
}

@Composable
internal fun NeedsRatingItemsRow(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
) {
    val unrated = finishedItemsNeedingRating(overview)
    JournalFinishedShelf(
        title = "Needs rating",
        summary = "${unrated.size} to rate",
        items = unrated,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onOpenItem = onOpenItem,
    )
}

@Composable
private fun JournalFinishedShelf(
    title: String,
    summary: String,
    items: List<JournalItemSummary>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
) {
    // An empty row folds away rather than saying so.
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JournalSectionHeader(title = title, summary = summary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { summary -> summary.item.itemId }) { summary ->
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
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onOpen)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            JournalCoverArt(
                item = summary.item,
                catalogItem = summary.catalogItem,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(0.68f),
            )
            summary.entry?.rating?.let { rating ->
                RatingBadge(
                    state = ratingBadgeState(rating),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
            }
        }
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

/** "October", "2026": how the sections name the period. */
internal fun JournalPeriodOverview.periodName(): String = when (period) {
    JournalPeriod.MONTH -> monthLabel().substringBefore(" ")
    JournalPeriod.YEAR -> yearLabel()
}
