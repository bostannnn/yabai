package com.example.unifiedreader.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A day as the app writes it: "3 Nov", or "3 Nov 2025" when it is not in [today]'s year.
 *
 * Month names are English (`Locale.US`), like the rest of the app's text, so a label never mixes languages
 * ("since 3 нояб.").
 */
fun shortDayLabel(day: LocalDate, today: LocalDate): String =
    day.format(if (day.year == today.year) SameYearFormat else OtherYearFormat)

/** [shortDayLabel] for a moment, read as a day in [zone]. */
fun shortDayLabel(
    epochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = shortDayLabel(
    day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(),
    today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate(),
)

private val SameYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val OtherYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
