package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun JournalActivityDashboard(
    overview: JournalPeriodOverview,
    activityContent: @Composable () -> Unit,
) {
    val points = remember(overview) { overview.activityChartPoints() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatsSectionLabel("Activity dashboard")
            Text(
                text = overview.activityInsight(points),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            ActivityAreaChart(points = points)
            CompactMetrics(overview = overview)
            ReadingMix(overview = overview)
            CompactPace(overview = overview)
            activityContent()
        }
    }
}

@Composable
private fun ActivityAreaChart(
    points: List<ActivityChartPoint>,
) {
    val usesPages = points.any { point -> point.pages > 0 }
    val maxValue = points.maxOfOrNull { point -> point.chartValue(usesPages) }?.coerceAtLeast(1) ?: 1
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueLabel = if (usesPages) "pages" else "sessions"
    var selectedPoint by remember(points, usesPages) {
        mutableStateOf(points.maxByOrNull { point -> point.chartValue(usesPages) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        selectedPoint?.let { point ->
            Text(
                text = "${point.label}: ${formatDashboardInt(point.chartValue(usesPages))} $valueLabel" +
                    if (point.finishedCount > 0) ", ${point.finishedCount} finished" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(points, usesPages) {
                    detectTapGestures { offset ->
                        if (points.isNotEmpty()) {
                            val plotStart = ACTIVITY_AXIS_WIDTH.toPx() + ACTIVITY_CHART_GAP.toPx()
                            val plotWidth = (size.width - plotStart).coerceAtLeast(1f)
                            val step = if (points.size > 1) plotWidth / (points.size - 1).toFloat() else plotWidth
                            val index = if (step > 0f) {
                                ((offset.x - plotStart) / step).roundToInt().coerceIn(points.indices)
                            } else {
                                0
                            }
                            selectedPoint = points[index]
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(ACTIVITY_CHART_GAP),
        ) {
            ChartValueAxis(maxValue = maxValue, modifier = Modifier.height(ACTIVITY_CHART_HEIGHT))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ACTIVITY_CHART_HEIGHT)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val chartTop = ACTIVITY_CHART_TOP_PADDING.toPx()
                    val chartBottom = size.height - ACTIVITY_CHART_BOTTOM_PADDING.toPx()
                    val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
                    val step = if (points.size > 1) size.width / (points.size - 1).toFloat() else size.width
                    repeat(3) { index ->
                        val y = chartTop + chartHeight * index / 2f
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    if (points.any { point -> point.chartValue(usesPages) > 0 }) {
                        val chartPoints = points.mapIndexed { index, point ->
                            val x = if (points.size > 1) step * index else size.width / 2f
                            val y = chartBottom - chartHeight * point.chartValue(usesPages).toFloat() / maxValue.toFloat()
                            Offset(x, y)
                        }
                        val linePath = smoothActivityPath(chartPoints)
                        val fillPath = smoothActivityPath(chartPoints)
                        fillPath.lineTo(chartPoints.last().x, chartBottom)
                        fillPath.lineTo(chartPoints.first().x, chartBottom)
                        fillPath.close()
                        drawPath(path = fillPath, color = fillColor)
                        drawPath(
                            path = linePath,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(ACTIVITY_AXIS_WIDTH))
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                points.forEach { point ->
                    Text(
                        text = point.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = if (points.size > 12) 8.sp else MaterialTheme.typography.labelSmall.fontSize,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartValueAxis(
    maxValue: Int,
    modifier: Modifier = Modifier,
) {
    val plotHeight = ACTIVITY_CHART_HEIGHT - ACTIVITY_CHART_TOP_PADDING - ACTIVITY_CHART_BOTTOM_PADDING
    Box(modifier = modifier.width(ACTIVITY_AXIS_WIDTH)) {
        AxisTickLabel(
            value = maxValue,
            y = ACTIVITY_CHART_TOP_PADDING,
        )
        AxisTickLabel(
            value = maxValue / 2,
            y = ACTIVITY_CHART_TOP_PADDING + plotHeight / 2,
        )
        AxisTickLabel(
            value = 0,
            y = ACTIVITY_CHART_TOP_PADDING + plotHeight,
        )
    }
}

@Composable
private fun AxisTickLabel(
    value: Int,
    y: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = formatDashboardInt(value),
        modifier = Modifier
            .width(ACTIVITY_AXIS_WIDTH)
            .height(ACTIVITY_AXIS_LABEL_HEIGHT)
            .offset(y = y - ACTIVITY_AXIS_LABEL_HEIGHT / 2),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun CompactMetrics(
    overview: JournalPeriodOverview,
) {
    val metrics = listOf(
        CompactMetric("Hours", formatDashboardHours(overview.stats.readingTimeMillis)),
        CompactMetric("Pages", formatDashboardInt(overview.stats.pagesRead)),
        CompactMetric("Finished", (overview.stats.booksFinished + overview.stats.comicsFinished).toString()),
        CompactMetric("Active days", overview.stats.activeDays.toString()),
    )
    BoxWithConstraints {
        val columns = if (maxWidth >= 640.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { metric ->
                        CompactMetricCell(metric = metric, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricCell(
    metric: CompactMetric,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = metric.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = metric.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadingMix(
    overview: JournalPeriodOverview,
) {
    val scheme = MaterialTheme.colorScheme
    val bookColor = scheme.primary
    val comicColor = Color(0xFFFFB84D)
    val bookPages = overview.stats.bookPagesRead
    val comicPages = overview.stats.comicPagesRead
    val totalPages = (bookPages + comicPages).coerceAtLeast(1)
    val bookFraction = bookPages.toFloat() / totalPages.toFloat()
    val comicFraction = comicPages.toFloat() / totalPages.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Reading mix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${formatDashboardInt(bookPages)} book pages / ${formatDashboardInt(comicPages)} comic pages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)),
        ) {
            if (bookPages > 0) {
                Box(
                    modifier = Modifier
                        .weight(bookFraction)
                        .height(18.dp)
                        .background(bookColor),
                )
            }
            if (bookPages > 0 && comicPages > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(18.dp)
                        .background(scheme.surface),
                )
            }
            if (comicPages > 0) {
                Box(
                    modifier = Modifier
                        .weight(comicFraction)
                        .height(18.dp)
                        .background(comicColor),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MixLegendItem(label = "Books", color = bookColor)
            MixLegendItem(label = "Comics", color = comicColor)
        }
    }
}

@Composable
private fun MixLegendItem(
    label: String,
    color: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactPace(
    overview: JournalPeriodOverview,
) {
    BoxWithConstraints {
        if (maxWidth >= 680.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaceSummaryLine("Books", overview.stats.booksFinished, overview.stats.bookActiveDays, modifier = Modifier.weight(1f))
                PaceSummaryLine("Comics", overview.stats.comicsFinished, overview.stats.comicActiveDays, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaceSummaryLine("Books", overview.stats.booksFinished, overview.stats.bookActiveDays)
                PaceSummaryLine("Comics", overview.stats.comicsFinished, overview.stats.comicActiveDays)
            }
        }
    }
}

@Composable
private fun PaceSummaryLine(
    label: String,
    finished: Int,
    activeDays: Int,
    modifier: Modifier = Modifier,
) {
    val value = if (finished > 0 && activeDays > 0) {
        "1 every ${formatDashboardDecimal(activeDays.toDouble() / finished.toDouble())} days"
    } else {
        "No finished ${label.lowercase(Locale.US)}"
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class CompactMetric(
    val label: String,
    val value: String,
)

private data class ActivityChartPoint(
    val label: String,
    val pages: Int,
    val finishedCount: Int,
    val eventCount: Int,
)

private fun JournalPeriodOverview.activityChartPoints(): List<ActivityChartPoint> {
    return when (period) {
        JournalPeriod.MONTH -> {
            val calendar = Calendar.getInstance(Locale.US).apply { timeInMillis = periodStartEpochMillis }
            val dayCount = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val days = activityDays.associateBy(JournalActivityDay::dayOfMonth)
            (1..dayCount).map { day ->
                val activityDay = days[day]
                ActivityChartPoint(
                    label = day.toString(),
                    pages = activityDay?.pagesRead ?: 0,
                    finishedCount = (activityDay?.finishedBooks ?: 0) + (activityDay?.finishedComics ?: 0),
                    eventCount = activityDay?.eventCount ?: 0,
                )
            }
        }

        JournalPeriod.YEAR -> {
            val byMonth = activityDays.groupBy(JournalActivityDay::month)
            val names = DateFormatSymbols.getInstance(Locale.US).shortMonths
            (0..11).map { month ->
                val monthDays = byMonth[month].orEmpty()
                ActivityChartPoint(
                    label = names[month].take(3),
                    pages = monthDays.sumOf(JournalActivityDay::pagesRead),
                    finishedCount = monthDays.sumOf { day -> day.finishedBooks + day.finishedComics },
                    eventCount = monthDays.sumOf(JournalActivityDay::eventCount),
                )
            }
        }
    }
}

private fun JournalPeriodOverview.activityInsight(
    points: List<ActivityChartPoint>,
): String {
    val usesPages = points.any { point -> point.pages > 0 }
    val best = points.maxWithOrNull(
        compareBy<ActivityChartPoint> { it.chartValue(usesPages) }.thenBy { it.finishedCount },
    )
    if (best == null || (best.chartValue(usesPages) <= 0 && best.finishedCount <= 0)) {
        return "No reading activity in this period yet."
    }
    val unit = if (period == JournalPeriod.MONTH) best.label else best.label
    val finished = if (best.finishedCount > 0) {
        ", ${best.finishedCount} finished"
    } else {
        ""
    }
    val value = if (usesPages) {
        "${formatDashboardInt(best.pages)} pages"
    } else {
        "${best.eventCount} ${if (best.eventCount == 1) "session" else "sessions"}"
    }
    return "Peak activity was $unit with $value$finished."
}

private fun ActivityChartPoint.chartValue(
    usesPages: Boolean,
): Int = if (usesPages) pages else eventCount

private fun smoothActivityPath(
    points: List<Offset>,
): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    for (index in 0 until points.lastIndex) {
        val current = points[index]
        val next = points[index + 1]
        val mid = Offset(
            x = (current.x + next.x) / 2f,
            y = (current.y + next.y) / 2f,
        )
        path.quadraticTo(
            current.x,
            current.y,
            mid.x,
            mid.y,
        )
    }
    val last = points.last()
    path.quadraticTo(last.x, last.y, last.x, last.y)
    return path
}

private fun formatDashboardHours(
    millis: Long,
): String = formatDashboardDecimal(millis.toDouble() / 3_600_000.0)

private fun formatDashboardInt(
    value: Int,
): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

private fun formatDashboardDecimal(
    value: Double,
): String = if (value >= 10.0) {
    value.roundToInt().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}

private val ACTIVITY_AXIS_WIDTH = 40.dp
private val ACTIVITY_CHART_GAP = 8.dp
private val ACTIVITY_CHART_HEIGHT = 188.dp
private val ACTIVITY_CHART_TOP_PADDING = 12.dp
private val ACTIVITY_CHART_BOTTOM_PADDING = 22.dp
private val ACTIVITY_AXIS_LABEL_HEIGHT = 16.dp
