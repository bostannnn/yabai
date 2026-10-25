package com.example.unifiedreader.journal

import kotlin.math.roundToLong

/**
 * How long the rest of a book should take at the pace its sessions went: the time spent over the
 * share of the book those sessions moved through, applied to what is left after [fraction]. Only
 * sessions that moved forward count (a session that went back, or has no positions, says nothing
 * about pace). Null until there is enough to go on ([MinPaceMillis] of reading over at least
 * [MinPaceShare] of the book), and when nothing is left.
 */
fun JournalSnapshot.readingTimeLeftMillis(
    itemId: String,
    fraction: Float,
): Long? {
    val left = 1.0 - fraction.coerceIn(0f, 1f)
    if (left <= 0.0) return null
    var millis = 0L
    var share = 0.0
    sessions.forEach { session ->
        if (session.itemId != itemId || session.durationMillis <= 0L) return@forEach
        val from = session.fromPercent ?: return@forEach
        val to = session.toPercent ?: return@forEach
        if (to <= from) return@forEach
        millis += session.durationMillis
        share += to - from
    }
    if (millis < MinPaceMillis || share < MinPaceShare) return null
    return (millis * left / share).roundToLong()
}

private const val MinPaceMillis = 5 * 60_000L
private const val MinPaceShare = 0.02
