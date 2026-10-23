package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import java.util.Calendar
import java.util.Locale

/**
 * Reading days in Month: each day a square shaded in the accent by pages read (four steps,
 * scaled to the month's busiest day), today ringed, a dot on days something was finished. Beside
 * it (under it on phones) the longest run, the busiest day and the most finished in one day.
 */
@Composable
internal fun JournalMonthReadingDays(
    overview: JournalPeriodOverview,
    nowEpochMillis: Long,
    onDaySelected: (JournalActivityDay) -> Unit,
) {
    val daysByKey = remember(overview) { overview.activityDays.associateBy(JournalActivityDay::localDayKey) }
    val maxValue = overview.activityDays.maxOfOrNull { day -> day.heatValue } ?: 0
    val highlights = remember(overview) { journalMonthHighlights(overview.activityDays) }
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        JournalSectionHeader(title = "Reading days", summary = "${overview.monthDaysSummary()} · tap a day to see what you read")
        BoxWithConstraints {
            val gridWidth = if (compact) maxWidth else minOf(maxWidth * 0.6f, MonthCellMax * 7 + MonthCellGap * 6)
            val cell = ((gridWidth - MonthCellGap * 6) / 7).coerceAtMost(MonthCellMax)
            val grid = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MonthGrid(
                        monthStartEpochMillis = overview.periodStartEpochMillis,
                        daysByKey = daysByKey,
                        maxValue = maxValue,
                        todayKey = localDateKey(nowEpochMillis),
                        cell = cell,
                        gap = MonthCellGap,
                        corner = 10.dp,
                        showNumbers = true,
                        showFinished = true,
                        onDaySelected = onDaySelected,
                    )
                    HeatLegend(showFinished = true)
                }
            }
            val figures = monthFigures(highlights)
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    grid()
                    if (figures.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            figures.forEach { (value, label) ->
                                JournalFigure(value = value, label = label, valueSize = 24, labelLines = 2, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    grid()
                    Column(
                        modifier = Modifier.padding(top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        figures.forEach { (value, label) -> JournalFigure(value = value, label = label, valueSize = 30) }
                    }
                }
            }
        }
    }
}

private fun monthFigures(highlights: JournalMonthHighlights): List<Pair<String, String>> = listOfNotNull(
    highlights.longestRun?.let { run -> "${run.length} days" to "longest run, ${run.rangeLabel()}" },
    highlights.busiestDay?.let { day -> day.pagesRead.counted("page") to "busiest day, ${day.shortLabel()}" },
    highlights.mostFinishedDay?.let { day ->
        day.shortLabel().substringBeforeLast(' ') to "${day.finishedCount} finished in one day"
    },
)

/** Reading days in Year: the twelve months, four across, as squares without day numbers. */
@Composable
internal fun JournalYearReadingDays(
    overview: JournalPeriodOverview,
    nowEpochMillis: Long,
    onDaySelected: (JournalActivityDay) -> Unit,
) {
    val daysByKey = remember(overview) { overview.activityDays.associateBy(JournalActivityDay::localDayKey) }
    val maxValue = overview.activityDays.maxOfOrNull { day -> day.heatValue } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        JournalSectionHeader(title = "Reading days", summary = overview.yearDaysSummary())
        BoxWithConstraints {
            val columns = journalYearMonthColumns(maxWidth.value)
            val monthWidth = (maxWidth - YearMonthGap * (columns - 1)) / columns
            val cell = ((monthWidth - YearCellGap * 6) / 7).coerceAtMost(YearCellMax)
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                (Calendar.JANUARY..Calendar.DECEMBER).chunked(columns).forEach { months ->
                    Row(horizontalArrangement = Arrangement.spacedBy(YearMonthGap)) {
                        months.forEach { month ->
                            Column(modifier = Modifier.width(monthWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = shortMonthName(month),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MonthGrid(
                                    monthStartEpochMillis = monthStartInYear(overview.periodStartEpochMillis, month),
                                    daysByKey = daysByKey,
                                    maxValue = maxValue,
                                    todayKey = localDateKey(nowEpochMillis),
                                    cell = cell,
                                    gap = YearCellGap,
                                    corner = 5.dp,
                                    showNumbers = false,
                                    showFinished = false,
                                    onDaySelected = onDaySelected,
                                )
                            }
                        }
                    }
                }
                HeatLegend(showFinished = false)
            }
        }
    }
}

/** Months across in Year: four on a tablet, fewer as the space narrows. */
internal fun journalYearMonthColumns(maxWidthDp: Float): Int = when {
    maxWidthDp >= 560f -> 4
    maxWidthDp >= 400f -> 3
    else -> 2
}

@Composable
private fun MonthGrid(
    monthStartEpochMillis: Long,
    daysByKey: Map<Int, JournalActivityDay>,
    maxValue: Int,
    todayKey: Int,
    cell: Dp,
    gap: Dp,
    corner: Dp,
    showNumbers: Boolean,
    showFinished: Boolean,
    onDaySelected: (JournalActivityDay) -> Unit,
) {
    val cells = remember(monthStartEpochMillis) { monthCells(monthStartEpochMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        if (showNumbers) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.width(cell),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(modifier = Modifier.size(cell))
                    } else {
                        val activity = daysByKey[day.localDayKey]
                        HeatCell(
                            dayOfMonth = day.dayOfMonth.takeIf { showNumbers },
                            level = activity?.let { journalHeatLevel(it.heatValue, maxValue) } ?: 0,
                            finished = showFinished && (activity?.finishedCount ?: 0) > 0,
                            today = day.localDayKey == todayKey,
                            corner = corner,
                            onClick = activity?.takeIf { it.heatValue > 0 }?.let { { onDaySelected(it) } },
                            description = activity?.let { it.cellDescription() },
                            modifier = Modifier.size(cell),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    dayOfMonth: Int?,
    level: Int,
    finished: Boolean,
    today: Boolean,
    corner: Dp,
    onClick: (() -> Unit)?,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ring = scheme.primary
    val dot = scheme.tertiary
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .drawWithContent {
                drawContent()
                if (today) {
                    // Outside the square, clear of it by 2 dp.
                    val inset = -3.dp.toPx()
                    drawRoundRect(
                        color = ring,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius((corner + 3.dp).toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                if (finished) {
                    val radius = 3.dp.toPx()
                    drawCircle(color = dot, radius = radius, center = Offset(size.width - 5.dp.toPx() - radius, size.height - 5.dp.toPx() - radius))
                }
            }
            .clip(shape)
            .background(heatColor(level))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        dayOfMonth?.let { day ->
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = when (level) {
                    0, 1 -> scheme.onSurfaceVariant
                    2 -> scheme.onSurface
                    else -> scheme.onPrimary
                },
            )
        }
    }
}

@Composable
private fun heatColor(level: Int): Color {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val accent = MaterialTheme.colorScheme.primary
    return when (level) {
        0 -> base
        1 -> lerp(base, accent, 0.24f)
        2 -> lerp(base, accent, 0.46f)
        3 -> lerp(base, accent, 0.72f)
        else -> accent
    }
}

/** "Less ▢▢▢▢▢ More", and the finished dot where the calendar shows it. */
@Composable
private fun HeatLegend(showFinished: Boolean) {
    val textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Less", style = textStyle, color = color)
        (0..4).forEach { level ->
            Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(heatColor(level)))
        }
        Text(text = "More", style = textStyle, color = color)
        if (showFinished) {
            Spacer(modifier = Modifier.width(14.dp))
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            Text(text = "Finished something", style = textStyle, color = color)
        }
    }
}

private fun JournalActivityDay.cellDescription(): String = listOfNotNull(
    longLabel(),
    pagesRead.takeIf { it > 0 }?.counted("page"),
    finishedCount.takeIf { it > 0 }?.let { "$it finished" },
).joinToString(", ")

private data class CalendarCell(
    val localDayKey: Int,
    val dayOfMonth: Int,
)

private fun monthCells(
    monthStartEpochMillis: Long,
): List<CalendarCell?> {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = monthStartEpochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val leadingBlanks = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = MutableList<CalendarCell?>(leadingBlanks) { null }
    repeat(daysInMonth) { index ->
        calendar.set(Calendar.DAY_OF_MONTH, index + 1)
        cells += CalendarCell(
            localDayKey = localDateKey(calendar.timeInMillis),
            dayOfMonth = index + 1,
        )
    }
    while (cells.size % 7 != 0) {
        cells += null
    }
    return cells
}

private fun monthStartInYear(
    yearStartEpochMillis: Long,
    month: Int,
): Long = Calendar.getInstance(Locale.US).apply {
    timeInMillis = yearStartEpochMillis
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private val MonthCellMax = 44.dp
private val MonthCellGap = 6.dp
private val YearCellMax = 20.dp
private val YearCellGap = 4.dp
private val YearMonthGap = 24.dp
