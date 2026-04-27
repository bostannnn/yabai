package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun MonthActivityCalendar(
    overview: JournalPeriodOverview,
    nowEpochMillis: Long,
    onActivityDaySelected: (JournalActivityDay) -> Unit,
) {
    CalendarSectionTitle(title = "Reading calendar")
    MonthGrid(
        monthStartEpochMillis = overview.periodStartEpochMillis,
        daysByKey = overview.activityDays.associateBy(JournalActivityDay::localDayKey),
        nowEpochMillis = nowEpochMillis,
        showMonthLabel = false,
        compact = false,
        modifier = Modifier.fillMaxWidth(),
        onActivityDaySelected = onActivityDaySelected,
    )
}

@Composable
internal fun YearActivityCalendar(
    overview: JournalPeriodOverview,
    nowEpochMillis: Long,
    onActivityDaySelected: (JournalActivityDay) -> Unit,
) {
    CalendarSectionTitle(title = "Year activity")
    val daysByKey = overview.activityDays.associateBy(JournalActivityDay::localDayKey)
    BoxWithConstraints {
        val monthsPerRow = yearCalendarMonthColumns(maxWidth.value)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (Calendar.JANUARY..Calendar.DECEMBER).chunked(monthsPerRow).forEach { months ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    months.forEach { month ->
                        MonthGrid(
                            monthStartEpochMillis = monthStartInYear(overview.periodStartEpochMillis, month),
                            daysByKey = daysByKey,
                            nowEpochMillis = nowEpochMillis,
                            showMonthLabel = true,
                            compact = true,
                            onActivityDaySelected = onActivityDaySelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSectionTitle(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun MonthGrid(
    monthStartEpochMillis: Long,
    daysByKey: Map<Int, JournalActivityDay>,
    nowEpochMillis: Long,
    showMonthLabel: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onActivityDaySelected: (JournalActivityDay) -> Unit,
) {
    val cells = remember(monthStartEpochMillis) { monthCells(monthStartEpochMillis) }
    BoxWithConstraints(modifier = modifier) {
        val layout = monthCalendarLayout(
            maxWidthDp = maxWidth.value,
            daysInMonth = cells.count { cell -> cell != null },
            compact = compact,
        )
        val cellSize = layout.cellSizeDp.dp
        val cellSpacing = layout.cellSpacingDp.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (showMonthLabel) {
                Text(
                    text = shortMonthName(monthStartEpochMillis),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (layout.isSingleRow) {
                Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                    cells.filterNotNull().forEach { cell ->
                        CalendarDayCell(
                            cell = cell,
                            activity = daysByKey[cell.localDayKey],
                            isToday = cell.localDayKey == localDateKey(nowEpochMillis),
                            compact = compact,
                            modifier = Modifier.size(cellSize),
                            onActivityDaySelected = onActivityDaySelected,
                        )
                    }
                }
            } else {
                WeekdayHeader(compact = compact, cellSize = cellSize, cellSpacing = cellSpacing)
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
                        week.forEach { cell ->
                            CalendarDayCell(
                                cell = cell,
                                activity = cell?.let { daysByKey[it.localDayKey] },
                                isToday = cell?.localDayKey == localDateKey(nowEpochMillis),
                                compact = compact,
                                modifier = Modifier.size(cellSize),
                                onActivityDaySelected = onActivityDaySelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(
    compact: Boolean,
    cellSize: androidx.compose.ui.unit.Dp,
    cellSpacing: androidx.compose.ui.unit.Dp,
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.size(width = cellSize, height = 14.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarCell?,
    activity: JournalActivityDay?,
    isToday: Boolean,
    compact: Boolean,
    onActivityDaySelected: (JournalActivityDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cell == null) {
        Box(modifier = modifier)
        return
    }
    val color = activityColor(activity)
    val shape = RoundedCornerShape(if (compact) 4.dp else 6.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(color)
            .then(
                if (activity != null) {
                    Modifier.clickable { onActivityDaySelected(activity) }
                } else {
                    Modifier
                },
            )
            .then(
                if (isToday) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (activity != null) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun activityColor(
    activity: JournalActivityDay?,
) = when {
    activity == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    activity.pagesRead >= 100 || activity.finishedBooks + activity.finishedComics > 0 ->
        MaterialTheme.colorScheme.primary
    activity.pagesRead >= 25 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
}

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
): Long {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = yearStartEpochMillis
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.timeInMillis
}

private fun shortMonthName(
    epochMillis: Long,
): String = SimpleDateFormat("MMM", Locale.US).format(Date(epochMillis))
