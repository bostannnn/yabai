package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.journal.classifyJournalMedia
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Pages read, from the positions the readers save. A position counts as reading only from a
 * start the journal knows: the first time a book is opened, its saved place (from another device,
 * or from before the journal) is not pages read that day. And where a book has reading sessions,
 * its pages are held to what that much time allows, so a jump through the contents or a position
 * synced from elsewhere doesn't count as hundreds of pages read in a minute.
 */

/** Pages one progress event moved on; none when where it started is unknown. */
internal fun JournalReadingEvent.positivePageDelta(): Int =
    knownPageDelta(fromPage, toPage, fromPercent, toPercent, pageCount)

/** Pages one reading session moved on, at most what its length allows. */
internal fun JournalReadingSession.positivePageDelta(): Int =
    knownPageDelta(fromPage, toPage, fromPercent, toPercent, pageCount)
        .coerceAtMost(journalPlausiblePages(mediaType, durationMillis))

/**
 * Pages read of one book in a stretch of time, from its events and sessions there. The two
 * describe the same reading, so the larger counts, not both; the events are held to the time
 * the sessions took, and count as they are only for reading done without a session (elsewhere).
 */
internal fun journalItemPages(
    events: List<JournalReadingEvent>,
    sessions: List<JournalReadingSession>,
    sessionMillis: Long = sessions.sumOf { session -> session.durationMillis.coerceAtLeast(0L) },
): Int {
    val sessionPages = sessions.sumOf(JournalReadingSession::positivePageDelta)
    val eventPages = events.sumOf(JournalReadingEvent::positivePageDelta)
    val mediaType = sessions.firstOrNull()?.mediaType ?: events.firstOrNull()?.mediaType()
    val heldEventPages = if (sessions.isEmpty() || mediaType == null) {
        eventPages
    } else {
        eventPages.coerceAtMost(journalPlausiblePages(mediaType, sessionMillis))
    }
    return maxOf(heldEventPages, sessionPages)
}

/**
 * The most pages anyone reads in [millis]: a fast reader's pace (Readium positions, about 70
 * words each, for books; flipping for comics) plus a few pages for the turns at either end.
 */
internal fun journalPlausiblePages(
    mediaType: JournalMediaType,
    millis: Long,
): Int {
    val perMinute = when (mediaType) {
        JournalMediaType.BOOK -> MaxBookPagesPerMinute
        JournalMediaType.COMIC -> MaxComicPagesPerMinute
    }
    val minutes = millis.coerceAtLeast(0L) / 60_000.0
    return ceil(minutes * perMinute).toInt() + PageTurnSlack
}

internal fun JournalReadingEvent.mediaType(): JournalMediaType = classifyJournalMedia(
    sourceType = sourceType,
    itemKind = itemKind,
    format = format,
)

private fun knownPageDelta(
    fromPage: Int?,
    toPage: Int?,
    fromPercent: Double?,
    toPercent: Double?,
    pageCount: Int?,
): Int {
    if (fromPage != null && toPage != null && toPage > fromPage) return toPage - fromPage
    val count = pageCount ?: return 0
    val from = fromPercent ?: return 0
    val to = toPercent ?: return 0
    return ((to - from).coerceAtLeast(0.0) * count).roundToInt()
}

private const val MaxBookPagesPerMinute = 4
private const val MaxComicPagesPerMinute = 30
private const val PageTurnSlack = 3
