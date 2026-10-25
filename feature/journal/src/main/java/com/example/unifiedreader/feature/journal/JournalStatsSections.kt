package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppTextButton

/**
 * Ratings: what you thought of what you finished in the period, books and comics apart. A kind
 * with a few ratings gets five rows of bars, five stars at the top; one or two get a line. Not
 * shown while none of the period's books is rated.
 */
@Composable
internal fun JournalRatingsPanel(
    overview: JournalPeriodOverview,
    modifier: Modifier = Modifier,
) {
    val counts = overview.stats.ratingCountsByMediaType
    val books = journalRatingSummary(counts[JournalMediaType.BOOK].orEmpty())
    val comics = journalRatingSummary(counts[JournalMediaType.COMIC].orEmpty())
    val all = journalRatingSummary(
        (counts[JournalMediaType.BOOK].orEmpty().keys + counts[JournalMediaType.COMIC].orEmpty().keys).associateWith { rating ->
            (counts[JournalMediaType.BOOK]?.get(rating) ?: 0) + (counts[JournalMediaType.COMIC]?.get(rating) ?: 0)
        },
    ) ?: return
    // The kind with more ratings first: its bars carry the panel.
    val kinds = listOfNotNull(books?.let { "Books" to it }, comics?.let { "Comics" to it })
        .sortedByDescending { (_, summary) -> summary.count }
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(
            title = "Ratings",
            summary = "${all.count} of ${overview.finishedItems.size} rated · ${formatJournalAverage(all.average)} average",
        )
        // A lone kind's line would only repeat the heading's.
        if (kinds.size == 1 && kinds.single().second.count < RatingBarsMinCount) return@JournalPanel
        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            kinds.forEach { (title, summary) ->
                if (summary.count >= RatingBarsMinCount) {
                    RatingBars(title = title, summary = summary)
                } else {
                    RatingLine(title = title, summary = summary)
                }
            }
        }
    }
}

@Composable
private fun RatingBars(
    title: String,
    summary: JournalRatingSummary,
) {
    val maxCount = summary.starCounts.max().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RatingLine(title = title, summary = summary)
        summary.starCounts.forEachIndexed { index, count ->
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        contentDescription = "stars",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    text = count.toString(),
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** "Comics · 142 rated · 3.9 ★". */
@Composable
private fun RatingLine(
    title: String,
    summary: JournalRatingSummary,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
        Text(
            text = "· ${summary.count} rated · ${formatJournalAverage(summary.average)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = AppIcons.Star,
            contentDescription = "average",
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Most read: each kind (writers, artists, genres, tags, authors) with its top three in columns,
 * as many across as fit; See all lists the rest. Bars only where the counts differ.
 */
@Composable
internal fun JournalMostReadPanel(
    groups: List<JournalMostReadGroup>,
    finishedCount: Int,
    onRowSelected: (JournalMostReadGroup, JournalFacetRow) -> Unit,
    onSeeAll: (JournalMostReadGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(title = "Most read", summary = "From the ${finishedCount} you finished")
        BoxWithConstraints(modifier = Modifier.padding(top = 10.dp)) {
            // At least three across on a tablet, so one or two kinds don't stretch over the page.
            val columns = when {
                maxWidth >= 860.dp -> groups.size.coerceIn(3, 5)
                maxWidth >= 480.dp -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                groups.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        row.forEach { group ->
                            MostReadGroup(
                                group = group,
                                onRowSelected = { facet -> onRowSelected(group, facet) },
                                onSeeAll = { onSeeAll(group) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
                    }
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
    val bars = group.barsCompare
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
            )
            if (group.rows.size > MostReadVisibleRows) {
                AppTextButton(onClick = onSeeAll) { Text(text = "See all") }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
        group.rows.take(MostReadVisibleRows).forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRowSelected(row.row) }
                    .padding(vertical = 9.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = row.row.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.row.count.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (bars) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(row.row.count.toFloat() / maxCount)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        )
                    }
                }
            }
        }
    }
}

/** A name, what it counts, and a 4 dp bar against the kind's top name: a row of a kind's See all. */
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
private const val RatingBarsMinCount = 3
