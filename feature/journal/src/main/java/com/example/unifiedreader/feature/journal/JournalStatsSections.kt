package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun JournalStatsMetricGrid(
    overview: JournalPeriodOverview,
) {
    val periodDayCount = overview.periodDayCount()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsSectionLabel("Reading stats")
        BoxWithConstraints {
            if (maxWidth >= 760.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingStatsSummary(
                        title = "All reading",
                        hoursMillis = overview.stats.readingTimeMillis,
                        pages = overview.stats.pagesRead,
                        completedLabel = "finished",
                        completed = overview.stats.booksFinished + overview.stats.comicsFinished,
                        period = overview.period,
                        periodDayCount = periodDayCount,
                        modifier = Modifier.weight(1f),
                    )
                    ReadingStatsSummary(
                        title = "Books",
                        hoursMillis = overview.stats.bookReadingTimeMillis,
                        pages = overview.stats.bookPagesRead,
                        completedLabel = "books",
                        completed = overview.stats.booksFinished,
                        period = overview.period,
                        periodDayCount = periodDayCount,
                        modifier = Modifier.weight(1f),
                    )
                    ReadingStatsSummary(
                        title = "Comics",
                        hoursMillis = overview.stats.comicReadingTimeMillis,
                        pages = overview.stats.comicPagesRead,
                        completedLabel = "comics",
                        completed = overview.stats.comicsFinished,
                        period = overview.period,
                        periodDayCount = periodDayCount,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReadingStatsSummary(
                        title = "All reading",
                        hoursMillis = overview.stats.readingTimeMillis,
                        pages = overview.stats.pagesRead,
                        completedLabel = "finished",
                        completed = overview.stats.booksFinished + overview.stats.comicsFinished,
                        period = overview.period,
                        periodDayCount = periodDayCount,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReadingStatsSummary(
                            title = "Books",
                            hoursMillis = overview.stats.bookReadingTimeMillis,
                            pages = overview.stats.bookPagesRead,
                            completedLabel = "books",
                            completed = overview.stats.booksFinished,
                            period = overview.period,
                            periodDayCount = periodDayCount,
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                        ReadingStatsSummary(
                            title = "Comics",
                            hoursMillis = overview.stats.comicReadingTimeMillis,
                            pages = overview.stats.comicPagesRead,
                            completedLabel = "comics",
                            completed = overview.stats.comicsFinished,
                            period = overview.period,
                            periodDayCount = periodDayCount,
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingStatsSummary(
    title: String,
    hoursMillis: Long,
    pages: Int,
    completedLabel: String,
    completed: Int,
    period: JournalPeriod,
    periodDayCount: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatLine(
                value = formatHours(hoursMillis),
                label = "hours read",
                perPeriod = averageLabel(hoursMillis.toDouble() / 3_600_000.0, period, periodDayCount),
            )
            StatLine(
                value = formatCompactInt(pages),
                label = "pages read",
                perPeriod = averageLabel(pages.toDouble(), period, periodDayCount),
            )
            StatLine(
                value = completed.toString(),
                label = completedLabel,
                perPeriod = averageLabel(completed.toDouble(), period, periodDayCount),
            )
        }
    }
}

@Composable
private fun StatLine(
    value: String,
    label: String,
    perPeriod: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = perPeriod,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ReadingPacePanels(
    overview: JournalPeriodOverview,
) {
    BoxWithConstraints {
        if (maxWidth >= 760.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadingPacePanel(
                    title = "Reading pace: books",
                    mediaLabel = "book",
                    completed = overview.stats.booksFinished,
                    hoursMillis = overview.stats.bookReadingTimeMillis,
                    pages = overview.stats.bookPagesRead,
                    activeDays = overview.stats.bookActiveDays,
                    modifier = Modifier.weight(1f),
                )
                ReadingPacePanel(
                    title = "Reading pace: comics",
                    mediaLabel = "comic",
                    completed = overview.stats.comicsFinished,
                    hoursMillis = overview.stats.comicReadingTimeMillis,
                    pages = overview.stats.comicPagesRead,
                    activeDays = overview.stats.comicActiveDays,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadingPacePanel(
                    title = "Reading pace: books",
                    mediaLabel = "book",
                    completed = overview.stats.booksFinished,
                    hoursMillis = overview.stats.bookReadingTimeMillis,
                    pages = overview.stats.bookPagesRead,
                    activeDays = overview.stats.bookActiveDays,
                )
                ReadingPacePanel(
                    title = "Reading pace: comics",
                    mediaLabel = "comic",
                    completed = overview.stats.comicsFinished,
                    hoursMillis = overview.stats.comicReadingTimeMillis,
                    pages = overview.stats.comicPagesRead,
                    activeDays = overview.stats.comicActiveDays,
                )
            }
        }
    }
}

@Composable
private fun ReadingPacePanel(
    title: String,
    mediaLabel: String,
    completed: Int,
    hoursMillis: Long,
    pages: Int,
    activeDays: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatsSectionLabel(title)
            if (completed <= 0 && hoursMillis <= 0L && pages <= 0) {
                EmptyStatsMessage("No reading sessions yet")
            } else {
                Text(
                    text = paceHeadline(mediaLabel, completed, activeDays),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                PaceRow(
                    value = formatHours(hoursMillis),
                    label = "hours",
                    average = paceValue(hoursMillis.toDouble() / 3_600_000.0, activeDays),
                )
                PaceRow(
                    value = formatCompactInt(pages),
                    label = "pages",
                    average = paceValue(pages.toDouble(), activeDays),
                )
                PaceRow(
                    value = completed.toString(),
                    label = mediaLabel.pluralize(),
                    average = paceValue(completed.toDouble(), activeDays),
                )
            }
        }
    }
}

@Composable
private fun PaceRow(
    value: String,
    label: String,
    average: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(72.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        PaceColumn(value = average, label = "per active day", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PaceColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun FinishedItemsRow(
    overview: JournalPeriodOverview,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
) {
    if (overview.finishedItems.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsSectionLabel("Finished")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(overview.finishedItems, key = { summary -> summary.item.itemId }) { summary ->
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

@Composable
internal fun RatingBreakdownSection(
    bookRatings: Map<Int, Int>,
    comicRatings: Map<Int, Int>,
) {
    BoxWithConstraints {
        if (maxWidth >= 840.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RatingBreakdown(
                    title = "Book ratings",
                    values = bookRatings,
                    modifier = Modifier.weight(1f),
                )
                RatingBreakdown(
                    title = "Comic ratings",
                    values = comicRatings,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RatingBreakdown(
                    title = "Book ratings",
                    values = bookRatings,
                )
                RatingBreakdown(
                    title = "Comic ratings",
                    values = comicRatings,
                )
            }
        }
    }
}

@Composable
private fun RatingBreakdown(
    title: String,
    values: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsSectionLabel(title)
            val maxValue = values.values.maxOrNull()?.coerceAtLeast(1)
            if (maxValue == null) {
                EmptyStatsMessage("No ratings")
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                (1..10).forEach { rating ->
                    val count = values[rating] ?: 0
                    RatingBar(
                        rating = rating,
                        count = count,
                        maxValue = maxValue,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AverageReadingTimeChart(
    title: String,
    buckets: List<JournalTimeBucket>,
) {
    if (buckets.isEmpty() || buckets.all { bucket -> bucket.averageMillis <= 0L }) return
    val maxBucket = buckets.maxBy { bucket -> bucket.averageMillis }
    val maxValue = maxBucket.averageMillis.coerceAtLeast(1L)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    val barColor = MaterialTheme.colorScheme.tertiary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatsSectionLabel(title)
            Text(
                text = "Most reading happens around ${maxBucket.label}, averaging ${formatDuration(maxBucket.averageMillis)}.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val rowHeight = size.height / 4f
                    repeat(4) { index ->
                        val y = rowHeight * index
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 8.dp),
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
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                if (bucket.averageMillis > 0L) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.72f)
                                            .height((132f * fraction).coerceAtLeast(6f).dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor),
                                    )
                                }
                            }
                            Text(
                                text = bucket.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopFacetSections(
    sections: List<JournalFacetSection>,
    onFacetSelected: (JournalFacetSection, JournalFacetRow) -> Unit,
    onSeeAllSelected: (JournalFacetSection) -> Unit,
) {
    if (sections.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsSectionLabel("Most read metadata")
        BoxWithConstraints {
            val visibleSections = sections.filter { section -> section.rows.isNotEmpty() }
            if (maxWidth >= 840.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleSections.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { section ->
                                FacetChartCard(
                                    section = section,
                                    onFacetSelected = onFacetSelected,
                                    onSeeAllSelected = onSeeAllSelected,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleSections.forEach { section ->
                        FacetChartCard(
                            section = section,
                            onFacetSelected = onFacetSelected,
                            onSeeAllSelected = onSeeAllSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FacetChartCard(
    section: JournalFacetSection,
    onFacetSelected: (JournalFacetSection, JournalFacetRow) -> Unit,
    onSeeAllSelected: (JournalFacetSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxValue = section.rows.maxOfOrNull(JournalFacetRow::count)?.coerceAtLeast(1) ?: 1
    val visibleRows = section.rows.take(FACET_CHART_VISIBLE_ROWS)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatsSectionLabel(section.title)
                if (section.rows.size > visibleRows.size) {
                    TextButton(onClick = { onSeeAllSelected(section) }) {
                        Text(text = "See all")
                    }
                }
            }
            section.rows.firstOrNull()?.let { top ->
                Text(
                    text = "${top.label} leads with ${top.count} ${if (top.count == 1) "item" else "items"}.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            visibleRows.forEachIndexed { index, row ->
                FacetBar(
                    row = row,
                    maxValue = maxValue,
                    color = facetColor(index),
                    onClick = { onFacetSelected(section, row) },
                )
            }
        }
    }
}

@Composable
internal fun JournalFacetRowsDialog(
    section: JournalFacetSection,
    onRowSelected: (JournalFacetRow) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxValue = section.rows.maxOfOrNull(JournalFacetRow::count)?.coerceAtLeast(1) ?: 1
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(section.rows, key = { row -> row.label }) { row ->
                        FacetBar(
                            row = row,
                            maxValue = maxValue,
                            color = facetColor(section.rows.indexOf(row)),
                            onClick = { onRowSelected(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FacetBar(
    row: JournalFacetRow,
    maxValue: Int,
    color: Color,
    onClick: () -> Unit,
) {
    val fraction = (row.count.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            modifier = Modifier.weight(0.34f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        Box(
            modifier = Modifier
                .weight(0.52f)
                .height(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                    .height(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
        Text(
            text = row.count.toString(),
            modifier = Modifier.weight(0.14f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RatingBar(
    rating: Int,
    count: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = (count.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    val barHeight = (88f * fraction).coerceAtLeast(if (count > 0) 12f else 0f).dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .height(94.dp)
                .width(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rating.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsSectionLabel(
    text: String,
) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyStatsMessage(
    text: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun facetColor(
    index: Int,
): Color {
    val scheme = MaterialTheme.colorScheme
    return when (index % 7) {
        0 -> scheme.primary
        1 -> scheme.tertiary
        2 -> scheme.secondary
        3 -> scheme.error
        4 -> scheme.primaryContainer
        5 -> scheme.tertiaryContainer
        else -> scheme.secondaryContainer
    }
}

private fun averageLabel(
    total: Double,
    period: JournalPeriod,
    periodDayCount: Int,
): String = when (period) {
    JournalPeriod.MONTH -> "${formatDecimal(total / periodDayCount.coerceAtLeast(1).toDouble())} per day"
    JournalPeriod.YEAR -> "${formatDecimal(total / 12.0)} per month"
}

private fun JournalPeriodOverview.periodDayCount(): Int {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = periodStartEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    var days = 0
    while (calendar.timeInMillis < periodEndEpochMillis) {
        days += 1
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days.coerceAtLeast(1)
}

private fun paceHeadline(
    mediaLabel: String,
    completed: Int,
    activeDays: Int,
): String {
    if (completed <= 0 || activeDays <= 0) return "No completed ${mediaLabel.pluralize()} yet"
    val daysPerItem = activeDays.toDouble() / completed.toDouble()
    return "One $mediaLabel every ${formatDecimal(daysPerItem)} days"
}

private fun paceValue(
    total: Double,
    activeDays: Int,
): String {
    if (activeDays <= 0 || total <= 0.0) return "0"
    return formatDecimal(total / activeDays.toDouble())
}

private fun formatHours(
    millis: Long,
): String {
    val hours = millis.toDouble() / 3_600_000.0
    return formatDecimal(hours)
}

private fun formatDuration(
    millis: Long,
): String {
    val minutes = (millis / 60_000L).coerceAtLeast(1L)
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return if (hours > 0L) {
        "${hours}h ${remainingMinutes}m"
    } else {
        "${minutes}m"
    }
}

private fun formatCompactInt(
    value: Int,
): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun formatDecimal(
    value: Double,
): String = if (value >= 10.0) {
    value.roundToInt().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}

private fun String.pluralize(): String = when (this) {
    "book" -> "books"
    "comic" -> "comics"
    else -> "${this}s"
}

private const val FACET_CHART_VISIBLE_ROWS = 6
