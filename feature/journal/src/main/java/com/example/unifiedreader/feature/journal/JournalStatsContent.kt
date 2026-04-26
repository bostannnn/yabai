package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.FormatType
import java.util.Calendar
import java.util.Locale

@Composable
fun JournalStatsContent(
    snapshot: JournalSnapshot,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            StatsPanel(
                title = "This month",
                period = ReadingGoalPeriod.MONTH,
                stats = snapshot.monthStats,
                events = snapshot.events.currentPeriodEvents(ReadingGoalPeriod.MONTH),
            )
        }
        item {
            StatsPanel(
                title = "This year",
                period = ReadingGoalPeriod.YEAR,
                stats = snapshot.yearStats,
                events = snapshot.events.currentPeriodEvents(ReadingGoalPeriod.YEAR),
            )
        }
    }
}

@Composable
private fun StatsPanel(
    title: String,
    period: ReadingGoalPeriod,
    stats: JournalStats,
    events: List<JournalReadingEvent>,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            MetricGrid(stats = stats)
            ActivityPeriodStrip(period = period, events = events)
            BreakdownGroup(
                title = "Sources",
                values = stats.sourceCounts.mapKeys { (source, _) -> source.displayName() },
            )
            BreakdownGroup(
                title = "Formats",
                values = stats.formatCounts.mapKeys { (format, _) -> format.displayName() },
            )
            BreakdownGroup(
                title = "Ratings",
                values = stats.ratingCounts.mapKeys { (rating, _) -> "$rating/5" },
            )
        }
    }
}

@Composable
private fun MetricGrid(
    stats: JournalStats,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(label = "Books", value = stats.booksFinished.toString(), modifier = Modifier.weight(1f))
            MetricTile(label = "Comics", value = stats.comicsFinished.toString(), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(label = "Pages", value = stats.pagesRead.toString(), modifier = Modifier.weight(1f))
            MetricTile(label = "Active days", value = stats.activeDays.toString(), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityDays(
    events: List<JournalReadingEvent>,
) {
    val activeDays = remember(events) {
        events.map { event -> event.dayOfMonth() }.toSet()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Active days",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..31).forEach { day ->
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 20.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (day in activeDays) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ActivityPeriodStrip(
    period: ReadingGoalPeriod,
    events: List<JournalReadingEvent>,
) {
    when (period) {
        ReadingGoalPeriod.MONTH -> ActivityDays(events = events)
        ReadingGoalPeriod.YEAR -> ActivityMonths(events = events)
    }
}

@Composable
private fun ActivityMonths(
    events: List<JournalReadingEvent>,
) {
    val activeMonths = remember(events) {
        events.map { event -> event.monthOfYear() }.toSet()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Active months",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..11).forEach { month ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (month in activeMonths) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            ),
                    )
                    Text(
                        text = month.shortMonthLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownGroup(
    title: String,
    values: Map<String, Int>,
) {
    if (values.isEmpty()) return
    val maxValue = values.values.maxOrNull()?.coerceAtLeast(1) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        values.forEach { (label, count) ->
            BreakdownRow(
                label = label,
                count = count,
                fraction = count.toFloat() / maxValue.toFloat(),
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    count: Int,
    fraction: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun List<JournalReadingEvent>.currentPeriodEvents(
    period: ReadingGoalPeriod,
): List<JournalReadingEvent> {
    val (start, end) = periodBounds(period, System.currentTimeMillis())
    return inPeriod(start, end)
}

private fun JournalReadingEvent.dayOfMonth(): Int = Calendar.getInstance(Locale.US).run {
    timeInMillis = occurredAtEpochMillis
    get(Calendar.DAY_OF_MONTH)
}

private fun JournalReadingEvent.monthOfYear(): Int = Calendar.getInstance(Locale.US).run {
    timeInMillis = occurredAtEpochMillis
    get(Calendar.MONTH)
}

private fun Int.shortMonthLabel(): String = when (this) {
    Calendar.JANUARY -> "J"
    Calendar.FEBRUARY -> "F"
    Calendar.MARCH -> "M"
    Calendar.APRIL -> "A"
    Calendar.MAY -> "M"
    Calendar.JUNE -> "J"
    Calendar.JULY -> "J"
    Calendar.AUGUST -> "A"
    Calendar.SEPTEMBER -> "S"
    Calendar.OCTOBER -> "O"
    Calendar.NOVEMBER -> "N"
    Calendar.DECEMBER -> "D"
    else -> ""
}

private fun FormatType.displayName(): String = name
