package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth

internal fun finishedItemsNeedingRating(
    overview: JournalPeriodOverview,
): List<JournalItemSummary> = overview.finishedItems.filter { summary -> summary.entry?.rating == null }

/**
 * Finished (Year): one shelf of what was finished, newest first, a rated cover with its badge and
 * an unrated one with Rate under it. Rate the ones waiting, and See all, open lists in the sheet.
 */
@Composable
internal fun JournalFinishedPanel(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    onSeeAll: () -> Unit,
    onRateWaiting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = overview.finishedItems
    if (finished.isEmpty()) return
    val waiting = finishedItemsNeedingRating(overview).size
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    val actions = @Composable {
        if (waiting > 0) {
            AppTonalButton(onClick = onRateWaiting, modifier = Modifier.height(38.dp)) {
                Text(text = "Rate the $waiting waiting")
            }
        }
        if (finished.size > FinishedShelfCount || waiting > 0) {
            AppTextButton(onClick = onSeeAll) { Text(text = "See all") }
        }
    }
    // Beside the heading on a tablet, under it on a phone.
    val trailing: (@Composable RowScope.() -> Unit)? = if (compact) {
        null
    } else {
        { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() } }
    }
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(
            title = "Finished",
            summary = "${finished.size} in ${overview.periodName()}, newest first",
            trailing = trailing,
        )
        if (compact) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
        LazyRow(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(finished.take(FinishedShelfCount), key = { summary -> summary.item.itemId }) { summary ->
                FinishedItemCard(
                    summary = summary,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpen = { onOpenItem(summary) },
                )
            }
            if (finished.size > FinishedShelfCount) {
                item(key = "more") {
                    Box(
                        modifier = Modifier
                            .width(FinishedCoverWidth)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable(onClick = onSeeAll),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppBadge(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                text = "${finished.size - FinishedShelfCount} more",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
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
            .width(FinishedCoverWidth)
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
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary.entry?.rating == null) {
            AppBadge(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            ) {
                Text(
                    text = "Rate",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            summary.item.displaySubtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "October", "2026": how the panels name the period. */
internal fun JournalPeriodOverview.periodName(): String = when (period) {
    JournalPeriod.MONTH -> monthLabel().substringBefore(" ")
    JournalPeriod.YEAR -> yearLabel()
}

private val FinishedCoverWidth = 92.dp
private const val FinishedShelfCount = 12
