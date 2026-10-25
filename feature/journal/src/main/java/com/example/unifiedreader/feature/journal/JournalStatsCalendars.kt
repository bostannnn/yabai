package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.unifiedreader.ui.components.AppPanelDefaults
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import java.util.Calendar
import java.util.Locale

/**
 * Reading days in Month: each day a square shaded in the accent by its minutes of reading (four
 * steps, scaled to the month's longest day) with the minutes written in it where it is big
 * enough, today ringed, a dot on a day something was finished. A day without reading is only an
 * outline, and a day still to come is fainter still.
 */
@Composable
internal fun JournalMonthReadingDaysPanel(
    overview: JournalPeriodOverview,
    nowEpochMillis: Long,
    onDaySelected: (JournalActivityDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysByKey = remember(overview) { overview.activityDays.associateBy(JournalActivityDay::localDayKey) }
    val maxValue = overview.activityDays.maxOfOrNull { day -> day.heatValue } ?: 0
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(
            title = "Reading days",
            summary = if (compact) "Tap a day for what you read" else "Minutes each day · tap a day for what you read",
        )
        BoxWithConstraints(modifier = Modifier.padding(top = 14.dp)) {
            val cell = ((maxWidth - MonthCellGap * 6) / 7).coerceAtMost(MonthCellMax)
            MonthGrid(
                monthStartEpochMillis = overview.periodStartEpochMillis,
                daysByKey = daysByKey,
                maxValue = maxValue,
                todayKey = localDateKey(nowEpochMillis),
                cell = cell,
                gap = MonthCellGap,
                corner = if (cell >= 48.dp) 12.dp else 10.dp,
                showNumbers = true,
                showMinutes = cell >= MinutesCellMin,
                showFinished = true,
                onDaySelected = onDaySelected,
            )
        }
        HeatLegend(showFinished = true, modifier = Modifier.padding(top = 14.dp))
    }
}

/** Reading days in Year: the twelve months, four across, as squares without day numbers. */
@Composable
internal fun JournalYearReadingDaysPanel(
    overview: JournalPeriodOverview,
    nowEpochMillis: Long,
    onDaySelected: (JournalActivityDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysByKey = remember(overview) { overview.activityDays.associateBy(JournalActivityDay::localDayKey) }
    val maxValue = overview.activityDays.maxOfOrNull { day -> day.heatValue } ?: 0
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(title = "Reading days", summary = "Tap a day for what you read")
        BoxWithConstraints(modifier = Modifier.padding(top = 16.dp)) {
            val columns = journalYearMonthColumns(maxWidth.value)
            val monthWidth = (maxWidth - YearMonthGap * (columns - 1)) / columns
            val cell = ((monthWidth - YearCellGap * 6) / 7).coerceAtMost(YearCellMax)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                (Calendar.JANUARY..Calendar.DECEMBER).chunked(columns).forEach { months ->
                    Row(horizontalArrangement = Arrangement.spacedBy(YearMonthGap)) {
                        months.forEach { month ->
                            Column(modifier = Modifier.width(monthWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = longMonthName(month),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
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
                                    corner = 4.dp,
                                    showNumbers = false,
                                    showMinutes = false,
                                    showFinished = false,
                                    onDaySelected = onDaySelected,
                                )
                            }
                        }
                    }
                }
            }
        }
        HeatLegend(showFinished = false, modifier = Modifier.padding(top = 18.dp))
    }
}

/** Months across in Year: four on a tablet, fewer as the space narrows. */
internal fun journalYearMonthColumns(maxWidthDp: Float): Int = when {
    maxWidthDp >= 520f -> 4
    maxWidthDp >= 380f -> 3
    else -> 2
}

/** A day's minutes in its square: "22m", "1h 27", "2h". */
internal fun formatJournalCellMinutes(millis: Long): String {
    val minutes = ((millis + 59_999L) / 60_000L).coerceAtLeast(1L)
    if (minutes < 60L) return "${minutes}m"
    val rest = minutes % 60L
    return if (rest == 0L) "${minutes / 60L}h" else "${minutes / 60L}h ${rest.toString().padStart(2, '0')}"
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
    showMinutes: Boolean,
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
                        val activity = daysByKey[day.localDayKey]?.takeIf { it.heatValue > 0 }
                        HeatCell(
                            dayOfMonth = day.dayOfMonth.takeIf { showNumbers },
                            minutes = activity?.readingMillis?.takeIf { showMinutes && it > 0L }?.let(::formatJournalCellMinutes),
                            cornerLayout = showMinutes,
                            level = activity?.let { journalHeatLevel(it.heatValue, maxValue) } ?: 0,
                            future = day.localDayKey > todayKey,
                            finished = showFinished && (activity?.finishedCount ?: 0) > 0,
                            today = day.localDayKey == todayKey,
                            corner = corner,
                            onClick = activity?.let { { onDaySelected(it) } },
                            description = activity?.cellDescription(),
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
    minutes: String?,
    cornerLayout: Boolean,
    level: Int,
    future: Boolean,
    finished: Boolean,
    today: Boolean,
    corner: Dp,
    onClick: (() -> Unit)?,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ring = scheme.onSurface
    val dot = scheme.tertiary
    val shape = RoundedCornerShape(corner)
    val outline = scheme.onSurface.copy(alpha = if (future) 0.04f else 0.10f)
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
                    val radius = 3.5.dp.toPx()
                    drawCircle(color = dot, radius = radius, center = Offset(size.width - 6.dp.toPx() - radius, 6.dp.toPx() + radius))
                }
            }
            .clip(shape)
            .then(if (level > 0) Modifier.background(heatColor(level)) else Modifier.border(1.dp, outline, shape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val textColor = when {
            level >= 3 -> scheme.onPrimary
            level > 0 -> scheme.onSurface
            future -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
            else -> scheme.onSurfaceVariant
        }
        when {
            dayOfMonth == null -> Unit
            // The number in the corner and the minutes under it, in every square alike.
            cornerLayout -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CellText(text = dayOfMonth.toString(), color = textColor.copy(alpha = 0.85f), size = 11.5f, weight = FontWeight.SemiBold)
                minutes?.let { CellText(text = it, color = textColor, size = 12f, weight = FontWeight.Bold) }
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CellText(text = dayOfMonth.toString(), color = textColor, size = 13f, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CellText(
    text: String,
    color: Color,
    size: Float,
    weight: FontWeight,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = size.sp, lineHeight = (size + 2.5f).sp, fontFeatureSettings = "tnum"),
        fontWeight = weight,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun heatColor(level: Int): Color {
    val base = AppPanelDefaults.containerColor
    val accent = MaterialTheme.colorScheme.primary
    return when (level) {
        0 -> base
        1 -> lerp(base, accent, 0.2f)
        2 -> lerp(base, accent, 0.4f)
        3 -> lerp(base, accent, 0.66f)
        else -> accent
    }
}

/** "None ▢ ▢▢▢▢ More", and the finished dot where the calendar shows it. */
@Composable
private fun HeatLegend(
    showFinished: Boolean,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val swatch = RoundedCornerShape(4.dp)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), swatch))
        Text(text = "None", style = textStyle, color = color)
        Spacer(modifier = Modifier.width(6.dp))
        (1..4).forEach { level ->
            Box(modifier = Modifier.size(12.dp).clip(swatch).background(heatColor(level)))
        }
        Text(text = "More", style = textStyle, color = color)
        if (showFinished) {
            Spacer(modifier = Modifier.width(14.dp))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            Text(text = "Finished something", style = textStyle, color = color)
        }
    }
}

private fun JournalActivityDay.cellDescription(): String = listOfNotNull(
    longLabel(),
    readingMillis.takeIf { it > 0L }?.let(::formatJournalMinutes),
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

private val MonthCellMax = 56.dp
private val MonthCellGap = 5.dp
private val MinutesCellMin = 48.dp
private val YearCellMax = 18.dp
private val YearCellGap = 3.dp
private val YearMonthGap = 22.dp
