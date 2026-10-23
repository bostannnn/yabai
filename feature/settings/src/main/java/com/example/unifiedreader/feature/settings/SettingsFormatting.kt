package com.example.unifiedreader.feature.settings

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "today at 10:42", "yesterday at 21:10", "20 Oct at 09:14", or "20 Oct 2025" in another year. */
fun formatSettingsMoment(
    epochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    val then = Calendar.getInstance(Locale.US).apply { timeInMillis = epochMillis }
    val now = Calendar.getInstance(Locale.US).apply { timeInMillis = nowEpochMillis }
    val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMillis))
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val dayDifference = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDifference == 0 -> "today at $time"
        sameYear && dayDifference == 1 -> "yesterday at $time"
        sameYear -> "${SimpleDateFormat("d MMM", Locale.US).format(Date(epochMillis))} at $time"
        else -> SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(epochMillis))
    }
}

/** "20 Oct", or "20 Oct 2025" in another year. */
fun formatSettingsDay(
    epochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    val then = Calendar.getInstance(Locale.US).apply { timeInMillis = epochMillis }
    val now = Calendar.getInstance(Locale.US).apply { timeInMillis = nowEpochMillis }
    val pattern = if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "d MMM" else "d MMM yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))
}

/** A server address without its scheme or trailing slash: "192.168.50.60:5000". */
internal fun settingsHostLabel(baseUrl: String): String =
    baseUrl.trim().substringAfter("://").trimEnd('/')
