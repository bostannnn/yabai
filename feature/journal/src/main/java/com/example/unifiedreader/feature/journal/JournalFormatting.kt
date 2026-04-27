package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.SourceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val JournalReadingEventType.label: String
    get() = when (this) {
        JournalReadingEventType.STARTED -> "Started"
        JournalReadingEventType.CONTINUED -> "Continued"
        JournalReadingEventType.FINISHED -> "Finished"
    }

internal fun JournalReadingEvent.progressLabel(): String {
    val pagePart = if (toPage != null && pageCount != null) {
        fromPage?.let { "$it -> $toPage / $pageCount" } ?: "$toPage / $pageCount"
    } else {
        null
    }
    val percentPart = toPercent?.let { percent ->
        val to = (percent * 100).toInt()
        fromPercent?.let { from -> "${(from * 100).toInt()}% -> $to%" } ?: "$to%"
    }
    return listOfNotNull(pagePart, percentPart).firstOrNull() ?: formatDateTime(occurredAtEpochMillis)
}

internal fun Int.stars(): String = buildString {
    repeat(this@stars.coerceIn(1, 5)) { append("*") }
    repeat(5 - this@stars.coerceIn(1, 5)) { append("·") }
}

internal fun SourceType.displayName(): String = name.displayName()

internal fun String.displayName(): String = lowercase().replaceFirstChar(Char::uppercase)

internal fun formatDate(
    epochMillis: Long,
): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))

internal fun formatShortDate(
    epochMillis: Long,
): String = SimpleDateFormat("MMM d", Locale.US).format(Date(epochMillis))

internal fun formatDateTime(
    epochMillis: Long,
): String = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(epochMillis))

internal fun localDateKey(
    epochMillis: Long,
): Int {
    val calendar = Calendar.getInstance(Locale.US).apply {
        timeInMillis = epochMillis
    }
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
}
