package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** A section's 17 sp heading and the one line under it, with an optional action at the end. */
@Composable
internal fun JournalSectionHeader(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.SemiBold,
            )
            summary?.takeIf(String::isNotBlank)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Hours read, pages, finished and active days at 46 sp: four across, two by two on phones. */
@Composable
internal fun JournalFigures(
    overview: JournalPeriodOverview,
) {
    val stats = overview.stats
    val figures = listOf(
        formatJournalDecimal(stats.readingTimeMillis / 3_600_000.0) to "hours read",
        formatJournalCount(stats.pagesRead) to "pages",
        (stats.booksFinished + stats.comicsFinished).toString() to "finished",
        stats.activeDays.toString() to "active days",
    )
    val rows = if (currentAppWindowWidth() == AppWindowWidth.Compact) figures.chunked(2) else listOf(figures)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (value, label) ->
                    JournalFigure(value = value, label = label, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun JournalFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueSize: Int = 46,
    labelLines: Int = 1,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = valueSize.sp,
                lineHeight = (valueSize + 6).sp,
                fontFeatureSettings = "tnum",
                letterSpacing = (-0.01).sp * valueSize,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = labelLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pages per day (per month in Year) as an area: a 2.5 dp accent line over a 16% fill, days
 * labelled every seven. The line under the heading names the busiest day, or the tapped one.
 */
@Composable
internal fun JournalPagesChart(
    overview: JournalPeriodOverview,
) {
    val points = remember(overview) { overview.activityChartPoints() }
    val usesPages = points.any { point -> point.pages > 0 }
    if (points.none { point -> point.chartValue(usesPages) > 0 }) return
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val busiest = points.indices.maxByOrNull { index -> points[index].chartValue(usesPages) }
    val month = overview.period == JournalPeriod.MONTH
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        JournalSectionHeader(
            title = if (month) "Pages per day" else "Pages per month",
            summary = selected?.let { index -> points[index].describe(usesPages) }
                ?: busiest?.let { index -> points[index].describeBusiest(usesPages, month) },
        )
        val maxValue = points.maxOf { point -> point.chartValue(usesPages) }.coerceAtLeast(1)
        val lineColor = MaterialTheme.colorScheme.primary
        val fillColor = lineColor.copy(alpha = 0.16f)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PagesChartHeight)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val step = if (points.size > 1) size.width / (points.size - 1).toFloat() else size.width.toFloat()
                            val index = (offset.x / step).roundToInt().coerceIn(points.indices)
                            selected = index.takeUnless { it == selected }
                        }
                    },
            ) {
                val stroke = 2.5.dp.toPx()
                val top = stroke
                val bottom = size.height - stroke / 2f
                val step = if (points.size > 1) size.width / (points.size - 1).toFloat() else size.width
                val offsets = points.mapIndexed { index, point ->
                    Offset(
                        x = if (points.size > 1) step * index else size.width / 2f,
                        y = bottom - (bottom - top) * point.chartValue(usesPages) / maxValue.toFloat(),
                    )
                }
                val fill = smoothActivityPath(offsets).apply {
                    lineTo(offsets.last().x, size.height)
                    lineTo(offsets.first().x, size.height)
                    close()
                }
                drawPath(path = fill, color = fillColor)
                drawPath(path = smoothActivityPath(offsets), color = lineColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
                selected?.let { index ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = offsets[index])
                }
            }
            ChartAxisLabels(
                labels = points.mapIndexed { index, point ->
                    point.label.takeIf { !month || index % 7 == 0 }
                },
            )
        }
    }
}

/** Labels centred under their points, the first and last kept inside the chart. */
@Composable
private fun ChartAxisLabels(
    labels: List<String?>,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            labels.forEach { label ->
                Text(
                    text = label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                    color = color,
                    maxLines = 1,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val step = if (labels.size > 1) width / (labels.size - 1).toFloat() else 0f
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val x = (step * index - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

/** Books and comics as one 12 dp split bar, with each side's pages and finishing pace. */
@Composable
internal fun JournalBooksAndComics(
    overview: JournalPeriodOverview,
) {
    val stats = overview.stats
    val bookPages = stats.bookPagesRead
    val comicPages = stats.comicPagesRead
    if (bookPages <= 0 && comicPages <= 0) return
    val bookColor = MaterialTheme.colorScheme.primary
    val comicColor = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JournalSectionHeader(title = "Books and comics", summary = null)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (bookPages > 0) {
                Box(modifier = Modifier.weight(bookPages.toFloat()).height(12.dp).clip(CircleShape).background(bookColor))
            }
            if (comicPages > 0) {
                Box(modifier = Modifier.weight(comicPages.toFloat()).height(12.dp).clip(CircleShape).background(comicColor))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (bookPages > 0) {
                MixLegendLine(
                    color = bookColor,
                    text = listOfNotNull("Books", bookPages.counted("page"), journalFinishPace(stats.booksFinished, stats.bookActiveDays))
                        .joinToString(" · "),
                )
            }
            if (comicPages > 0) {
                MixLegendLine(
                    color = comicColor,
                    text = listOfNotNull("Comics", comicPages.counted("page"), journalFinishPace(stats.comicsFinished, stats.comicActiveDays))
                        .joinToString(" · "),
                )
            }
        }
    }
}

@Composable
private fun MixLegendLine(
    color: Color,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ActivityChartPoint(
    val label: String,
    /** "the 17th", "October": how the busiest point is named. */
    val name: String,
    /** "Thu 22 Oct", "March": how a tapped point is named. */
    val shortName: String,
    val pages: Int,
    val finishedCount: Int,
    val eventCount: Int,
)

private fun ActivityChartPoint.chartValue(usesPages: Boolean): Int = if (usesPages) pages else eventCount

private fun ActivityChartPoint.amount(usesPages: Boolean): String = listOfNotNull(
    if (usesPages) pages.counted("page") else eventCount.counted("session"),
    finishedCount.takeIf { it > 0 }?.let { "$it finished" },
).joinToString(", ")

private fun ActivityChartPoint.describe(usesPages: Boolean): String = "$shortName: ${amount(usesPages)}"

private fun ActivityChartPoint.describeBusiest(usesPages: Boolean, month: Boolean): String =
    "Busiest ${if (month) "day" else "month"} was $name: ${amount(usesPages)}"

private fun JournalPeriodOverview.activityChartPoints(): List<ActivityChartPoint> = when (period) {
    JournalPeriod.MONTH -> {
        val calendar = Calendar.getInstance(Locale.US).apply { timeInMillis = periodStartEpochMillis }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val days = activityDays.associateBy(JournalActivityDay::dayOfMonth)
        (1..calendar.getActualMaximum(Calendar.DAY_OF_MONTH)).map { day ->
            val activity = days[day]
            ActivityChartPoint(
                label = day.toString(),
                name = "the ${ordinal(day)}",
                shortName = (activity ?: JournalActivityDay(0, year, month, day, 0, 0, 0, 0)).shortLabel(),
                pages = activity?.pagesRead ?: 0,
                finishedCount = activity?.finishedCount ?: 0,
                eventCount = activity?.eventCount ?: 0,
            )
        }
    }
    JournalPeriod.YEAR -> {
        val byMonth = activityDays.groupBy(JournalActivityDay::month)
        (0..11).map { month ->
            val monthDays = byMonth[month].orEmpty()
            ActivityChartPoint(
                label = shortMonthName(month),
                name = longMonthName(month),
                shortName = longMonthName(month),
                pages = monthDays.sumOf(JournalActivityDay::pagesRead),
                finishedCount = monthDays.sumOf { day -> day.finishedCount },
                eventCount = monthDays.sumOf(JournalActivityDay::eventCount),
            )
        }
    }
}

private fun ordinal(day: Int): String = day.toString() + when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

private fun smoothActivityPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (index in 0 until points.lastIndex) {
        val current = points[index]
        val next = points[index + 1]
        path.quadraticTo(current.x, current.y, (current.x + next.x) / 2f, (current.y + next.y) / 2f)
    }
    val last = points.last()
    path.quadraticTo(last.x, last.y, last.x, last.y)
    return path
}

/** "1,204"; "12.5K" from ten thousand. */
internal fun formatJournalCount(value: Int): String = when {
    value >= 10_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0).replace(".0K", "K")
    else -> NumberFormat.getIntegerInstance(Locale.US).format(value)
}

private val PagesChartHeight = 140.dp
