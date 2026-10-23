package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/**
 * When the reading happens: the average time per hour of the day (per month in Year), as bars.
 * Folds away with no reading sessions.
 */
@Composable
internal fun JournalWhenYouRead(
    period: JournalPeriod,
    buckets: List<JournalTimeBucket>,
) {
    val peak = buckets.maxByOrNull(JournalTimeBucket::averageMillis)?.takeIf { it.averageMillis > 0L } ?: return
    val maxValue = peak.averageMillis
    val barColor = MaterialTheme.colorScheme.tertiary
    // Twenty-four hour labels only fit a tablet; a phone labels every third hour.
    val labelEvery = if (buckets.size > 12 && currentAppWindowWidth() == AppWindowWidth.Compact) 3 else 1
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        JournalSectionHeader(
            title = "When you read",
            summary = when (period) {
                JournalPeriod.MONTH -> "Busiest around ${peak.label}:00"
                JournalPeriod.YEAR -> "Busiest in ${longMonthName(peak.key)}"
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val fraction = (bucket.averageMillis.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (bucket.averageMillis > 0L) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height((122f * fraction).coerceAtLeast(6f).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(barColor),
                            )
                        }
                    }
                    Text(
                        text = if (bucket.key % labelEvery == 0) bucket.label else "",
                        // A label may be wider than its bar's column; it spills over the empty neighbours.
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = MaterialTheme.typography.labelSmall,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The period's ratings: books and comics side by side as five rows of bars, five stars at the
 * top. Not shown at all while nothing is rated.
 */
@Composable
internal fun JournalRatings(
    overview: JournalPeriodOverview,
    periodName: String,
) {
    val counts = overview.stats.ratingCountsByMediaType
    val books = journalRatingSummary(counts[JournalMediaType.BOOK].orEmpty())
    val comics = journalRatingSummary(counts[JournalMediaType.COMIC].orEmpty())
    val all = journalRatingSummary(
        (counts[JournalMediaType.BOOK].orEmpty().keys + counts[JournalMediaType.COMIC].orEmpty().keys).associateWith { rating ->
            (counts[JournalMediaType.BOOK]?.get(rating) ?: 0) + (counts[JournalMediaType.COMIC]?.get(rating) ?: 0)
        },
    ) ?: return
    val columns = listOfNotNull(books?.let { "Books" to it }, comics?.let { "Comics" to it })
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        JournalSectionHeader(
            title = "Ratings",
            summary = "${all.count} rated in $periodName · ${formatJournalAverage(all.average)} average",
        )
        val stacked = currentAppWindowWidth() == AppWindowWidth.Compact
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                columns.forEach { (title, summary) -> RatingColumn(title = title, summary = summary) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                columns.forEach { (title, summary) -> RatingColumn(title = title, summary = summary, modifier = Modifier.weight(1f)) }
                if (columns.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RatingColumn(
    title: String,
    summary: JournalRatingSummary,
    modifier: Modifier = Modifier,
) {
    val maxCount = summary.starCounts.max().coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold)
            Text(
                text = "${summary.count} rated · ${formatJournalAverage(summary.average)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary.starCounts.forEachIndexed { index, count ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.width(30.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (5 - index).toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = AppIcons.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    text = count.toString(),
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * Most read: each kind (authors, genres, writers, artists, tags) with its top three, side by
 * side; See all lists the rest. Kinds with nothing are left out, and so is the section.
 */
@Composable
internal fun JournalMostRead(
    groups: List<JournalMostReadGroup>,
    periodName: String,
    onRowSelected: (JournalMostReadGroup, JournalFacetRow) -> Unit,
    onSeeAll: (JournalMostReadGroup) -> Unit,
) {
    if (groups.isEmpty()) return
    val columns = if (currentAppWindowWidth() == AppWindowWidth.Compact) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        JournalSectionHeader(title = "Most read", summary = "From what you finished in $periodName")
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            groups.chunked(columns).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    pair.forEach { group ->
                        MostReadGroup(
                            group = group,
                            onRowSelected = { row -> onRowSelected(group, row) },
                            onSeeAll = { onSeeAll(group) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - pair.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MostReadGroup(
    group: JournalMostReadGroup,
    onRowSelected: (JournalFacetRow) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxCount = group.rows.maxOf { it.row.count }.coerceAtLeast(1)
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
            )
            if (group.rows.size > MostReadVisibleRows) {
                AppTextButton(onClick = onSeeAll) { Text(text = "See all") }
            }
        }
        group.rows.take(MostReadVisibleRows).forEach { row ->
            JournalMostReadRowItem(row = row, maxCount = maxCount, onClick = { onRowSelected(row.row) })
        }
    }
}

/** A name, what it counts, and a 4 dp bar against the kind's top name. */
@Composable
internal fun JournalMostReadRowItem(
    row: JournalMostReadRow,
    maxCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = row.row.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.countLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.row.count.toFloat() / maxCount)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private const val MostReadVisibleRows = 3
