package com.example.unifiedreader.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A server's ISO-8601 time as epoch milliseconds, or null when it is blank or not a time.
 *
 * Every source reads its times through this one parser, so a string that parses for one server parses for all.
 * It takes the shapes Kavita, Grimmory and Stump send:
 * - an instant: `2026-11-05T10:42:00Z`, `2026-11-05T10:42:00.1234567Z`
 * - a time with an offset: `2026-11-05T13:42:00+03:00` (Android 9's `Instant.parse` refuses these)
 * - a local time without a zone: `2026-11-05T10:42:00`, read as UTC
 */
fun String?.toServerEpochMillisOrNull(): Long? {
    val text = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return runCatching { Instant.parse(text).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrNull()
}
