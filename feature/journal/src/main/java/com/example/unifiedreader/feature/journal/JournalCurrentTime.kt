package com.example.unifiedreader.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@Composable
internal fun rememberJournalNowEpochMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            now = current
            delay(millisUntilNextLocalDay(current).coerceAtLeast(MinClockRefreshDelayMillis))
        }
    }
    return now
}

internal fun millisUntilNextLocalDay(
    epochMillis: Long,
): Long {
    val nextDay = Calendar.getInstance(Locale.US).apply {
        timeInMillis = epochMillis
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return nextDay.timeInMillis - epochMillis
}

private const val MinClockRefreshDelayMillis = 1_000L
