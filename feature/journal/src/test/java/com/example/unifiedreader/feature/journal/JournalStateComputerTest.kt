package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalStateComputerTest {
    @Test
    fun `first reader progress creates started event`() {
        val event = buildJournalEvent(
            input = progressInput(page = 4, percent = 0.2),
            latestEvent = null,
        )

        requireNotNull(event)
        assertEquals(JournalReadingEventType.STARTED, event.eventType)
        assertEquals(null, event.fromPage)
        assertEquals(4, event.toPage)
    }

    @Test
    fun `first completed reader progress creates finished event`() {
        val event = buildJournalEvent(
            input = progressInput(page = 100, percent = 1.0, isFinished = true),
            latestEvent = null,
        )

        requireNotNull(event)
        assertEquals(JournalReadingEventType.FINISHED, event.eventType)
        assertEquals(null, event.fromPage)
        assertEquals(100, event.toPage)
    }

    @Test
    fun `small progress movement is ignored`() {
        val latest = startedEvent(page = 10, percent = 0.10)

        val event = buildJournalEvent(
            input = progressInput(page = 11, percent = 0.11),
            latestEvent = latest,
        )

        assertNull(event)
    }

    @Test
    fun `meaningful progress movement creates continued event`() {
        val latest = startedEvent(page = 10, percent = 0.10)

        val event = buildJournalEvent(
            input = progressInput(page = 14, percent = 0.14),
            latestEvent = latest,
        )

        requireNotNull(event)
        assertEquals(JournalReadingEventType.CONTINUED, event.eventType)
        assertEquals(10, event.fromPage)
        assertEquals(14, event.toPage)
    }

    @Test
    fun `finished event is deduped after item is already finished`() {
        val latest = startedEvent(
            page = 100,
            percent = 1.0,
            type = JournalReadingEventType.FINISHED,
        )

        val event = buildJournalEvent(
            input = progressInput(page = 100, percent = 1.0, isFinished = true),
            latestEvent = latest,
        )

        assertNull(event)
    }

    @Test
    fun `stats are derived from journal events only`() {
        val events = listOf(
            startedEvent(page = 1, percent = 0.01),
            startedEvent(
                id = "book-continued",
                page = 31,
                percent = 0.31,
                type = JournalReadingEventType.CONTINUED,
                fromPage = 1,
                fromPercent = 0.01,
            ),
            startedEvent(
                id = "book-finished",
                page = 100,
                percent = 1.0,
                type = JournalReadingEventType.FINISHED,
                fromPage = 31,
                fromPercent = 0.31,
            ),
        )

        val stats = computeStats(events)

        assertEquals(1, stats.booksFinished)
        assertEquals(100, stats.pagesRead)
        assertEquals(1, stats.activeDays)
        assertEquals(mapOf(SourceType.GRIMMORY to 3), stats.sourceCounts)
    }

    @Test
    fun `snapshot rating stats are scoped to current periods`() {
        val januaryFirst2026 = 1_767_225_600_000L
        val decemberFirst2025 = 1_764_547_200_000L
        val snapshot = JournalStateComputer(
            nowEpochMillis = { januaryFirst2026 },
        ).snapshot(
            events = emptyList(),
            entries = listOf(
                journalEntry(
                    itemId = "current",
                    rating = 5,
                    updatedAtEpochMillis = januaryFirst2026,
                ),
                journalEntry(
                    itemId = "old",
                    rating = 3,
                    updatedAtEpochMillis = decemberFirst2025,
                ),
            ),
            goals = emptyList(),
        )

        assertEquals(mapOf(5 to 1), snapshot.monthStats.ratingCounts)
        assertEquals(mapOf(5 to 1), snapshot.yearStats.ratingCounts)
    }

    @Test
    fun `snapshot groups timeline into one summary per item`() {
        val snapshot = JournalStateComputer(nowEpochMillis = { 123_000L }).snapshot(
            events = listOf(
                startedEvent(id = "started", page = 2, percent = 0.02),
                startedEvent(
                    id = "continued",
                    page = 20,
                    percent = 0.2,
                    type = JournalReadingEventType.CONTINUED,
                    fromPage = 2,
                    fromPercent = 0.02,
                ),
                startedEvent(
                    id = "finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    fromPage = 20,
                    fromPercent = 0.2,
                ),
            ),
            entries = listOf(journalEntry(itemId = testItem.id, rating = 4, updatedAtEpochMillis = 130_000L)),
            goals = emptyList(),
        )

        assertEquals(1, snapshot.itemSummaries.size)
        val summary = snapshot.itemSummaries.single()
        assertEquals(testItem.id, summary.item.itemId)
        assertEquals(3, summary.events.size)
        assertEquals(true, summary.isFinished)
        assertEquals(4, summary.entry?.rating)
    }

    @Test
    fun `item summaries include review-only entries`() {
        val snapshot = JournalStateComputer(nowEpochMillis = { 123_000L }).snapshot(
            events = emptyList(),
            entries = listOf(journalEntry(itemId = testItem.id, rating = 5, updatedAtEpochMillis = 130_000L)),
            goals = emptyList(),
        )

        assertEquals(1, snapshot.itemSummaries.size)
        assertEquals(testItem.id, snapshot.itemSummaries.single().item.itemId)
    }

    @Test
    fun `item summary prefers newest event cover when older events have no cover`() {
        val snapshot = JournalStateComputer(nowEpochMillis = { 123_000L }).snapshot(
            events = listOf(
                startedEvent(
                    id = "old-without-cover",
                    page = 2,
                    percent = 0.02,
                    coverUrl = null,
                    occurredAtEpochMillis = 100_000L,
                ),
                startedEvent(
                    id = "new-with-cover",
                    page = 20,
                    percent = 0.2,
                    type = JournalReadingEventType.CONTINUED,
                    fromPage = 2,
                    fromPercent = 0.02,
                    coverUrl = "https://example.test/cover.jpg",
                    occurredAtEpochMillis = 130_000L,
                ),
            ),
            entries = emptyList(),
            goals = emptyList(),
        )

        assertEquals("https://example.test/cover.jpg", snapshot.itemSummaries.single().item.coverUrl)
    }

    private fun progressInput(
        page: Int,
        percent: Double,
        isFinished: Boolean = false,
    ): JournalProgressInput = JournalProgressInput(
        item = testItem,
        page = page,
        percent = percent,
        pageCount = 100,
        isFinished = isFinished,
        occurredAtEpochMillis = 1_000L + page,
    )

    private fun startedEvent(
        id: String = "book-started",
        page: Int,
        percent: Double,
        type: JournalReadingEventType = JournalReadingEventType.STARTED,
        fromPage: Int? = null,
        fromPercent: Double? = null,
        coverUrl: String? = testItem.coverUrl,
        occurredAtEpochMillis: Long = 123_000L,
    ): JournalReadingEvent = JournalReadingEvent(
        id = id,
        itemId = testItem.id,
        sourceType = testItem.sourceType,
        libraryId = testItem.libraryId,
        itemTitle = testItem.title,
        coverUrl = coverUrl,
        itemKind = testItem.kind,
        format = testItem.format,
        eventType = type,
        fromPercent = fromPercent,
        toPercent = percent,
        fromPage = fromPage,
        toPage = page,
        pageCount = 100,
        occurredAtEpochMillis = occurredAtEpochMillis,
    )

    private fun journalEntry(
        itemId: String,
        rating: Int,
        updatedAtEpochMillis: Long,
    ): ItemJournalEntry = ItemJournalEntry(
        itemId = itemId,
        sourceType = testItem.sourceType,
        libraryId = testItem.libraryId,
        itemTitle = testItem.title,
        coverUrl = testItem.coverUrl,
        itemKind = testItem.kind,
        format = testItem.format,
        rating = rating,
        reviewText = "",
        reviewedAtEpochMillis = null,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private val testItem = ReadableItem(
    id = "grimmory:item:book",
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:books",
    title = "Book",
    format = FormatType.EPUB,
    kind = ReadableItemKind.BOOK,
    pageCount = 100,
)
