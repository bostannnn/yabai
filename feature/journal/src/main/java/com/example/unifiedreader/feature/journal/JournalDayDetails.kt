package com.example.unifiedreader.feature.journal

data class JournalDayDetails(
    val activityDay: JournalActivityDay,
    val finishedItems: List<JournalItemSummary>,
)

fun JournalPeriodOverview.dayDetails(
    activityDay: JournalActivityDay,
): JournalDayDetails = JournalDayDetails(
    activityDay = activityDay,
    finishedItems = finishedItems.filter { summary ->
        summary.finishedAtEpochMillis?.let(::localDateKey) == activityDay.localDayKey
    },
)
