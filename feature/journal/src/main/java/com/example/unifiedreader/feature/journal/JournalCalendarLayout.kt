package com.example.unifiedreader.feature.journal

import kotlin.math.floor

internal data class MonthCalendarLayout(
    val isSingleRow: Boolean,
    val cellSizeDp: Float,
    val cellSpacingDp: Float,
)

internal fun monthCalendarLayout(
    maxWidthDp: Float,
    daysInMonth: Int,
    compact: Boolean,
): MonthCalendarLayout {
    val spacing = if (compact) 3f else 5f
    val preferredCell = if (compact) 13f else 28f
    val maxCell = if (compact) 18f else 36f
    val canUseSingleRow = !compact &&
        daysInMonth > 0 &&
        maxWidthDp >= daysInMonth * preferredCell + (daysInMonth - 1) * spacing
    val columns = if (canUseSingleRow) daysInMonth else 7
    val availableForCells = maxWidthDp - (columns - 1) * spacing
    val cellSize = (availableForCells / columns)
        .coerceAtLeast(if (compact) 12f else 24f)
        .coerceAtMost(maxCell)
    return MonthCalendarLayout(
        isSingleRow = canUseSingleRow,
        cellSizeDp = cellSize,
        cellSpacingDp = spacing,
    )
}

internal fun yearCalendarMonthColumns(
    maxWidthDp: Float,
): Int {
    val spacing = 14f
    val preferredMonthWidth = 7f * 18f + 6f * 3f
    return floor((maxWidthDp + spacing) / (preferredMonthWidth + spacing))
        .toInt()
        .coerceIn(1, 6)
}
