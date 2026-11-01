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
    // Moves on at each local midnight. The first value is already current, so it isn't written again on
    // the next frame: that would lay the whole journal out a second time as it opens.
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextLocalDay(System.currentTimeMillis()).coerceAtLeast(MinClockRefreshDelayMillis))
            now = System.currentTimeMillis()
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
