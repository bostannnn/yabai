package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalFinishesTest {
    private val oct10 = epochMillis(2026, Calendar.OCTOBER, 10)
    private val sep10 = epochMillis(2026, Calendar.SEPTEMBER, 10)

    @Test
    fun `turning back from the end on the same visit takes the finish back`() {
        val finish = event(id = "visit-finish", type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, at = oct10 + 2_000L)

        val event = takeBackVisitFinish(
            input = input(percent = 0.3, at = oct10 + 3_000L, visitStartedAt = oct10 + 1_000L),
            latestEvent = finish,
            hasEarlierEvents = true,
        )

        requireNotNull(event)
        assertEquals("visit-finish", event.id)
        assertEquals(JournalReadingEventType.CONTINUED, event.eventType)
        assertEquals(0.3, checkNotNull(event.toPercent), 0.0)
    }

    @Test
    fun `a finish that was the book's only event goes back to started`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = null, to = 1.0, at = oct10 + 2_000L)

        val event = takeBackVisitFinish(
            input = input(percent = 0.1, at = oct10 + 3_000L, visitStartedAt = oct10 + 1_000L),
            latestEvent = finish,
            hasEarlierEvents = false,
        )

        assertEquals(JournalReadingEventType.STARTED, event?.eventType)
    }

    @Test
    fun `a finish from an earlier visit stays when the place goes back`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, at = sep10)

        assertNull(
            takeBackVisitFinish(
                input = input(percent = 0.3, at = oct10 + 3_000L, visitStartedAt = oct10),
                latestEvent = finish,
                hasEarlierEvents = true,
            ),
        )
    }

    @Test
    fun `a finish stays when the place only moves within the last pages`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, page = 100, at = oct10 + 2_000L)

        assertNull(
            takeBackVisitFinish(
                input = input(page = 99, percent = 0.99, at = oct10 + 3_000L, visitStartedAt = oct10 + 1_000L),
                latestEvent = finish,
                hasEarlierEvents = true,
            ),
        )
    }

    @Test
    fun `a finish from the server stays`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, at = oct10 + 2_000L)
            .copy(remoteEventId = "session-1")

        assertNull(
            takeBackVisitFinish(
                input = input(percent = 0.3, at = oct10 + 3_000L, visitStartedAt = oct10 + 1_000L),
                latestEvent = finish,
                hasEarlierEvents = true,
            ),
        )
    }

    @Test
    fun `a finished book started over is read again`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, page = 100, at = sep10)

        val event = buildJournalEvent(
            input = input(page = 2, percent = 0.02, at = oct10),
            latestEvent = finish,
        )

        requireNotNull(event)
        assertEquals(JournalReadingEventType.STARTED, event.eventType)
        assertEquals(0.02, checkNotNull(event.fromPercent), 0.0)
    }

    @Test
    fun `a finished book's last chapters read again are progress`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, page = 100, at = sep10)

        val event = buildJournalEvent(
            input = input(page = 85, percent = 0.85, at = oct10),
            latestEvent = finish,
        )

        assertEquals(JournalReadingEventType.CONTINUED, event?.eventType)
    }

    @Test
    fun `a finished book opened at its end records nothing`() {
        val finish = event(type = JournalReadingEventType.FINISHED, from = 0.3, to = 1.0, page = 100, at = sep10)

        assertNull(buildJournalEvent(input = input(page = 99, percent = 0.99, at = oct10), latestEvent = finish))
    }

    @Test
    fun `a look at a finished book does not finish it again`() {
        val events = listOf(
            event(id = "read", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = sep10),
            // A Grimmory reading session that opened the book at its end and closed it there.
            event(id = "look", type = JournalReadingEventType.FINISHED, from = 1.0, to = 1.0, at = oct10),
        ).withFinishesOncePerRead()

        assertEquals(
            listOf(JournalReadingEventType.FINISHED, JournalReadingEventType.CONTINUED),
            events.map(JournalReadingEvent::eventType),
        )
    }

    @Test
    fun `the last chapters read again do not finish the book again`() {
        val events = listOf(
            event(id = "read", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = sep10),
            event(id = "back", type = JournalReadingEventType.CONTINUED, from = 1.0, to = 0.85, at = oct10),
            event(id = "again", type = JournalReadingEventType.FINISHED, from = 0.85, to = 1.0, at = oct10 + 1_000L),
        ).withFinishesOncePerRead()

        assertEquals(JournalReadingEventType.CONTINUED, events.single { it.id == "again" }.eventType)
    }

    @Test
    fun `a book started over and finished is finished twice`() {
        val inPieces = listOf(
            event(id = "read", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = sep10),
            event(id = "start-over", type = JournalReadingEventType.STARTED, from = 0.02, to = 0.4, at = oct10),
            event(id = "again", type = JournalReadingEventType.FINISHED, from = 0.4, to = 1.0, at = oct10 + 1_000L),
        ).withFinishesOncePerRead()
        val inOneGo = listOf(
            event(id = "read", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = sep10),
            event(id = "again", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = oct10),
        ).withFinishesOncePerRead()

        assertEquals(JournalReadingEventType.FINISHED, inPieces.single { it.id == "again" }.eventType)
        assertEquals(JournalReadingEventType.FINISHED, inOneGo.single { it.id == "again" }.eventType)
    }

    @Test
    fun `a book read twice is finished in each month it was finished in`() {
        val snapshot = JournalStateComputer(nowEpochMillis = { oct10 }).snapshot(
            events = listOf(
                event(id = "read", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = sep10),
                event(id = "start-over", type = JournalReadingEventType.STARTED, from = 0.01, to = 0.5, at = oct10),
                event(id = "again", type = JournalReadingEventType.FINISHED, from = 0.5, to = 1.0, at = oct10 + 1_000L),
            ),
            entries = emptyList(),
        )

        val september = computePeriodOverview(snapshot, JournalPeriod.MONTH, sep10)
        val october = computePeriodOverview(snapshot, JournalPeriod.MONTH, oct10)

        assertEquals(listOf(testBook.id), september.finishedItems.map { it.item.itemId })
        assertEquals(listOf(testBook.id), october.finishedItems.map { it.item.itemId })
        assertEquals(1, september.stats.booksFinished)
        assertEquals(1, october.stats.booksFinished)
    }

    @Test
    fun `a book only looked at again stays finished in its month alone`() {
        val snapshot = JournalStateComputer(nowEpochMillis = { oct10 }).snapshot(
            events = listOf(
                event(id = "read", type = JournalReadingEventType.FINISHED, from = 0.0, to = 1.0, at = sep10),
                event(id = "look", type = JournalReadingEventType.FINISHED, from = 1.0, to = 1.0, at = oct10),
            ),
            entries = emptyList(),
        )

        val september = computePeriodOverview(snapshot, JournalPeriod.MONTH, sep10)
        val october = computePeriodOverview(snapshot, JournalPeriod.MONTH, oct10)

        assertEquals(listOf(testBook.id), september.finishedItems.map { it.item.itemId })
        assertEquals(emptyList<String>(), october.finishedItems.map { it.item.itemId })
        assertEquals(0, october.stats.booksFinished)
        assertEquals(0, snapshot.monthStats.booksFinished)
    }

    private fun input(
        page: Int? = null,
        percent: Double,
        at: Long,
        visitStartedAt: Long? = null,
    ): JournalProgressInput = JournalProgressInput(
        item = testBook,
        page = page,
        percent = percent,
        pageCount = 100,
        isFinished = false,
        occurredAtEpochMillis = at,
        visitStartedAtEpochMillis = visitStartedAt,
    )

    private fun event(
        id: String = "event",
        type: JournalReadingEventType,
        from: Double?,
        to: Double,
        page: Int? = null,
        at: Long,
    ): JournalReadingEvent = JournalReadingEvent(
        id = id,
        itemId = testBook.id,
        sourceType = testBook.sourceType,
        libraryId = testBook.libraryId,
        itemTitle = testBook.title,
        coverUrl = null,
        itemKind = testBook.kind,
        format = testBook.format,
        eventType = type,
        fromPercent = from,
        toPercent = to,
        fromPage = null,
        toPage = page,
        pageCount = 100,
        occurredAtEpochMillis = at,
    )
}

private val testBook = ReadableItem(
    id = "grimmory:item:reread",
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:books",
    title = "Read Twice",
    format = FormatType.EPUB,
    kind = ReadableItemKind.BOOK,
    pageCount = 100,
)

private fun epochMillis(
    year: Int,
    month: Int,
    dayOfMonth: Int,
): Long = Calendar.getInstance(Locale.US).apply {
    clear()
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, dayOfMonth)
    set(Calendar.HOUR_OF_DAY, 12)
}.timeInMillis
