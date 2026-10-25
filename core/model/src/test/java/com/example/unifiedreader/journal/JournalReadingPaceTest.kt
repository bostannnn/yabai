package com.example.unifiedreader.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalReadingPaceTest {
    @Test
    fun `time left follows the pace of the sessions that moved forward`() {
        // 20 minutes for the first 40%: the remaining 50% after 50% should take 25.
        val snapshot = JournalSnapshot(
            sessions = listOf(
                session(minutes = 12, from = 0.0, to = 0.25),
                session(minutes = 8, from = 0.25, to = 0.4),
                // Went back to check something: says nothing about pace.
                session(minutes = 3, from = 0.4, to = 0.3),
                // Another book.
                session(minutes = 30, from = 0.0, to = 0.1, itemId = "other"),
                // No positions saved.
                session(minutes = 10, from = null, to = 0.5),
            ),
        )

        assertEquals(25 * Minute, snapshot.readingTimeLeftMillis("book", fraction = 0.5f))
    }

    @Test
    fun `no estimate until there is enough reading to go on`() {
        assertNull(JournalSnapshot(sessions = listOf(session(minutes = 4, from = 0.0, to = 0.2))).readingTimeLeftMillis("book", 0.2f))
        assertNull(JournalSnapshot(sessions = listOf(session(minutes = 10, from = 0.0, to = 0.01))).readingTimeLeftMillis("book", 0.01f))
        assertNull(JournalSnapshot().readingTimeLeftMillis("book", 0.3f))
    }

    @Test
    fun `no estimate once the book is done`() {
        val snapshot = JournalSnapshot(sessions = listOf(session(minutes = 20, from = 0.0, to = 1.0)))

        assertNull(snapshot.readingTimeLeftMillis("book", fraction = 1f))
    }

    private fun session(
        minutes: Long,
        from: Double?,
        to: Double?,
        itemId: String = "book",
    ) = JournalReadingSession(
        id = "$itemId-$minutes-$from-$to",
        itemId = itemId,
        sourceType = SourceType.LOCAL,
        libraryId = "local",
        itemTitle = "Glass Meridian",
        coverUrl = null,
        itemKind = ReadableItemKind.BOOK,
        format = FormatType.EPUB,
        mediaType = JournalMediaType.BOOK,
        startedAtEpochMillis = 0L,
        endedAtEpochMillis = minutes * Minute,
        durationMillis = minutes * Minute,
        fromPercent = from,
        toPercent = to,
        fromPage = null,
        toPage = null,
        pageCount = null,
    )

    private companion object {
        const val Minute = 60_000L
    }
}
