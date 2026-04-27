package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

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
        val latest = startedEvent(page = 10, percent = 0.10, occurredAtEpochMillis = -86_400_000L)

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
    fun `same day progress updates existing journal event`() {
        val latest = startedEvent(
            id = "same-day",
            page = 10,
            percent = 0.10,
            occurredAtEpochMillis = 1_000L,
        )

        val event = buildJournalEvent(
            input = progressInput(page = 14, percent = 0.14),
            latestEvent = latest,
        )

        requireNotNull(event)
        assertEquals("same-day", event.id)
        assertEquals(JournalReadingEventType.STARTED, event.eventType)
        assertEquals(null, event.fromPage)
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
        )

        assertEquals("https://example.test/cover.jpg", snapshot.itemSummaries.single().item.coverUrl)
    }

    @Test
    fun `item summary uses catalog display metadata and cover`() {
        val comic = testItem.copy(
            kind = ReadableItemKind.COMIC,
            format = FormatType.CBZ,
            title = "Chapter v24",
            coverUrl = "https://example.test/catalog-cover.jpg",
            comicMetadata = ComicMetadata(
                series = "Chainsaw Man",
                issueTitle = "Chapter v24",
            ),
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { 123_000L }).snapshot(
            events = listOf(
                startedEvent(
                    item = comic,
                    page = 2,
                    percent = 0.02,
                    coverUrl = null,
                ),
            ),
            entries = emptyList(),
            catalogItemsById = mapOf(comic.id to comic),
        )

        val item = snapshot.itemSummaries.single().item
        assertEquals("Chainsaw Man", item.displayTitle)
        assertEquals("Chapter v24", item.displaySubtitle)
        assertEquals("https://example.test/catalog-cover.jpg", item.coverUrl)
    }

    @Test
    fun `period overview scopes activity to selected month`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val april26 = epochMillis(2026, Calendar.APRIL, 26)
        val may2 = epochMillis(2026, Calendar.MAY, 2)
        val snapshot = JournalStateComputer(nowEpochMillis = { may2 }).snapshot(
            events = listOf(
                startedEvent(
                    id = "april-started",
                    page = 1,
                    percent = 0.01,
                    occurredAtEpochMillis = april10,
                ),
                startedEvent(
                    id = "april-continued",
                    page = 41,
                    percent = 0.41,
                    type = JournalReadingEventType.CONTINUED,
                    fromPage = 1,
                    fromPercent = 0.01,
                    occurredAtEpochMillis = april26,
                ),
                startedEvent(
                    id = "may-finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    fromPage = 41,
                    fromPercent = 0.41,
                    occurredAtEpochMillis = may2,
                ),
            ),
            entries = emptyList(),
        )

        val aprilOverview = computePeriodOverview(
            snapshot = snapshot,
            period = JournalPeriod.MONTH,
            referenceEpochMillis = april26,
        )
        val mayOverview = computePeriodOverview(
            snapshot = snapshot,
            period = JournalPeriod.MONTH,
            referenceEpochMillis = may2,
        )

        assertEquals(41, aprilOverview.stats.pagesRead)
        assertEquals(2, aprilOverview.stats.activeDays)
        assertEquals(0, aprilOverview.stats.booksFinished)
        assertEquals(listOf(10, 26), aprilOverview.activityDays.map(JournalActivityDay::dayOfMonth))
        assertEquals(59, mayOverview.stats.pagesRead)
        assertEquals(1, mayOverview.stats.booksFinished)
        assertEquals(listOf(testItem.id), mayOverview.finishedItems.map { summary -> summary.item.itemId })
    }

    @Test
    fun `first journal timestamp uses earliest activity or entry`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val may2 = epochMillis(2026, Calendar.MAY, 2)
        val snapshot = JournalSnapshot(
            events = listOf(
                startedEvent(
                    id = "may-started",
                    page = 5,
                    percent = 0.05,
                    occurredAtEpochMillis = may2,
                ),
            ),
            entries = listOf(journalEntry(itemId = testItem.id, rating = 4, updatedAtEpochMillis = april10)),
        )

        assertEquals(april10, snapshot.firstJournalAtEpochMillis())
    }

    @Test
    fun `millis until next local day advances to midnight`() {
        val april10 = Calendar.getInstance(Locale.US).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }.timeInMillis

        assertEquals(30 * 60 * 1000L, millisUntilNextLocalDay(april10))
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
        item: ReadableItem = testItem,
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
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = coverUrl,
        itemKind = item.kind,
        format = item.format,
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

private fun epochMillis(
    year: Int,
    month: Int,
    dayOfMonth: Int,
): Long = Calendar.getInstance(Locale.US).apply {
    clear()
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month)
    set(Calendar.DAY_OF_MONTH, dayOfMonth)
}.timeInMillis
