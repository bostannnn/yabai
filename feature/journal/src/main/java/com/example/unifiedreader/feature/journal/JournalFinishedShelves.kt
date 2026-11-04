package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.RatingBadge
import com.example.unifiedreader.feature.library.ratingBadgeState
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppTextButton

internal fun finishedItemsNeedingRating(
    overview: JournalPeriodOverview,
): List<JournalItemSummary> = overview.finishedItems.filter { summary -> summary.entry?.rating == null }

/** Finished: the period's finished books as a shelf of covers, newest first, a rated one with its badge. */
@Composable
internal fun JournalFinishedPanel(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JournalCoverShelfPanel(
        title = "Finished",
        summary = "${overview.finishedItems.size} in ${overview.periodName()}, newest first",
        items = overview.finishedItems,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onOpenItem = onOpenItem,
        onSeeAll = onSeeAll,
        modifier = modifier,
    )
}

/** Needs rating: the period's finished books without a rating, as a shelf of covers. */
@Composable
internal fun JournalNeedsRatingPanel(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unrated = finishedItemsNeedingRating(overview)
    JournalCoverShelfPanel(
        title = "Needs rating",
        summary = "${unrated.size} to rate",
        items = unrated,
        coverImageUrlProvider = coverImageUrlProvider,
        onCoverVisible = onCoverVisible,
        onOpenItem = onOpenItem,
        onSeeAll = onSeeAll,
        modifier = modifier,
    )
}

/** A panel with a heading and a row of covers; past a dozen, See all lists them in the sheet. */
@Composable
private fun JournalCoverShelfPanel(
    title: String,
    summary: String,
    items: List<JournalItemSummary>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // An empty shelf folds away rather than saying so.
    if (items.isEmpty()) return
    val seeAll: (@Composable RowScope.() -> Unit)? = if (items.size > CoverShelfCount) {
        { AppTextButton(onClick = onSeeAll) { Text(text = "See all") } }
    } else {
        null
    }
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(title = title, summary = summary, trailing = seeAll)
        LazyRow(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(items.take(CoverShelfCount), key = { summary -> summary.item.itemId }) { summary ->
                CoverShelfItem(
                    summary = summary,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpen = { onOpenItem(summary) },
                )
            }
            if (items.size > CoverShelfCount) {
                item(key = "more") {
                    Box(
                        modifier = Modifier
                            .width(CoverShelfWidth)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(onClick = onSeeAll),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppBadge(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                text = "${items.size - CoverShelfCount} more",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverShelfItem(
    summary: JournalItemSummary,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CoverShelfWidth)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            JournalCoverArt(
                item = summary.item,
                catalogItem = summary.catalogItem,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(7.dp)),
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
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        summary.item.displaySubtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "October", "2026": how the panels name the period. */
internal fun JournalPeriodOverview.periodName(): String = when (period) {
    JournalPeriod.MONTH -> monthLabel().substringBefore(" ")
    JournalPeriod.YEAR -> yearLabel()
}

private val CoverShelfWidth = 92.dp
private const val CoverShelfCount = 12
