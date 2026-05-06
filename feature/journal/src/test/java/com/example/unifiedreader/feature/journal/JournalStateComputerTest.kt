package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.journal.classifyJournalMedia
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

        assertEquals(mapOf(5 to 1), snapshot.monthStats.ratingCountsByMediaType[JournalMediaType.BOOK])
        assertEquals(mapOf(5 to 1), snapshot.yearStats.ratingCountsByMediaType[JournalMediaType.BOOK])
    }

    @Test
    fun `rating stats use item kind for PDF media classification`() {
        val now = 1_767_225_600_000L
        val kavitaPdf = testItem.copy(
            id = "kavita:item:pdf",
            sourceType = SourceType.KAVITA,
            format = FormatType.PDF,
            kind = ReadableItemKind.BOOK,
        )
        val grimmoryPdf = testItem.copy(
            id = "grimmory:item:pdf",
            sourceType = SourceType.GRIMMORY,
            format = FormatType.PDF,
            kind = ReadableItemKind.COMIC,
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { now }).snapshot(
            events = emptyList(),
            entries = listOf(
                journalEntry(item = kavitaPdf, rating = 7, updatedAtEpochMillis = now),
                journalEntry(item = grimmoryPdf, rating = 9, updatedAtEpochMillis = now),
            ),
            catalogItemsById = mapOf(
                kavitaPdf.id to kavitaPdf,
                grimmoryPdf.id to grimmoryPdf,
            ),
        )

        assertEquals(mapOf(7 to 1), snapshot.monthStats.ratingCountsByMediaType[JournalMediaType.BOOK])
        assertEquals(mapOf(9 to 1), snapshot.monthStats.ratingCountsByMediaType[JournalMediaType.COMIC])
    }

    @Test
    fun `period overview splits session time across hour buckets`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = emptyList(),
            sessions = listOf(
                journalSession(
                    startedAtEpochMillis = april10 + 10 * 60 * 60 * 1000L + 45 * 60 * 1000L,
                    endedAtEpochMillis = april10 + 11 * 60 * 60 * 1000L + 15 * 60 * 1000L,
                ),
            ),
            entries = emptyList(),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)

        assertEquals(30 * 60 * 1000L, overview.stats.readingTimeMillis)
        assertEquals(15 * 60 * 1000L / 30L, overview.averageReadingTimeBuckets[10].averageMillis)
        assertEquals(15 * 60 * 1000L / 30L, overview.averageReadingTimeBuckets[11].averageMillis)
    }

    @Test
    fun `stats add pages across event-only and session-only items`() {
        val sessionItem = testItem.copy(id = "session-item")
        val eventItem = testItem.copy(id = "event-item")

        val stats = computeStats(
            events = listOf(
                startedEvent(
                    item = eventItem,
                    id = "event-pages",
                    page = 40,
                    percent = 0.4,
                    fromPage = 0,
                    fromPercent = 0.0,
                ),
            ),
            sessions = listOf(
                journalSession(
                    item = sessionItem,
                    startedAtEpochMillis = 1_000L,
                    endedAtEpochMillis = 2_000L,
                    fromPage = 0,
                    toPage = 20,
                ),
            ),
        )

        assertEquals(60, stats.pagesRead)
        assertEquals(60, stats.bookPagesRead)
        assertEquals(0, stats.comicPagesRead)
    }

    @Test
    fun `stats avoid double counting pages when event and session describe same item`() {
        val stats = computeStats(
            events = listOf(
                startedEvent(
                    id = "event-pages",
                    page = 40,
                    percent = 0.4,
                    fromPage = 0,
                    fromPercent = 0.0,
                ),
            ),
            sessions = listOf(
                journalSession(
                    startedAtEpochMillis = 1_000L,
                    endedAtEpochMillis = 2_000L,
                    fromPage = 0,
                    toPage = 20,
                ),
            ),
        )

        assertEquals(40, stats.pagesRead)
        assertEquals(40, stats.bookPagesRead)
    }

    @Test
    fun `stats split pages by item kind media type`() {
        val pdfBook = testItem.copy(
            id = "pdf-book",
            sourceType = SourceType.KAVITA,
            format = FormatType.PDF,
            kind = ReadableItemKind.BOOK,
        )
        val comic = testItem.copy(id = "comic", kind = ReadableItemKind.COMIC)

        val stats = computeStats(
            events = listOf(
                startedEvent(
                    item = pdfBook,
                    id = "pdf-book-pages",
                    page = 30,
                    percent = 0.3,
                    fromPage = 0,
                    fromPercent = 0.0,
                ),
            ),
            sessions = listOf(
                journalSession(
                    item = comic,
                    startedAtEpochMillis = 1_000L,
                    endedAtEpochMillis = 2_000L,
                    fromPage = 0,
                    toPage = 15,
                ),
            ),
        )

        assertEquals(45, stats.pagesRead)
        assertEquals(30, stats.bookPagesRead)
        assertEquals(15, stats.comicPagesRead)
    }

    @Test
    fun `stats split active days by media type`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val april11 = epochMillis(2026, Calendar.APRIL, 11)
        val april20 = epochMillis(2026, Calendar.APRIL, 20)
        val april21 = epochMillis(2026, Calendar.APRIL, 21)
        val pdfBook = testItem.copy(
            id = "pdf-book",
            sourceType = SourceType.KAVITA,
            format = FormatType.PDF,
            kind = ReadableItemKind.BOOK,
        )

        val stats = computeStats(
            events = listOf(
                startedEvent(
                    id = "book-day-1",
                    page = 10,
                    percent = 0.1,
                    occurredAtEpochMillis = april10,
                ),
                startedEvent(
                    id = "book-day-2",
                    page = 20,
                    percent = 0.2,
                    occurredAtEpochMillis = april11,
                ),
            ),
            sessions = listOf(
                journalSession(
                    item = pdfBook,
                    startedAtEpochMillis = april20,
                    endedAtEpochMillis = april21 + 1_000L,
                ),
            ),
        )

        assertEquals(4, stats.activeDays)
        assertEquals(4, stats.bookActiveDays)
        assertEquals(0, stats.comicActiveDays)
    }

    @Test
    fun `period reading time uses active duration proportionally instead of wall time`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val sessionStart = april10 + 10 * 60 * 60 * 1000L
        val sessionEnd = april10 + 12 * 60 * 60 * 1000L
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = emptyList(),
            sessions = listOf(
                journalSession(
                    startedAtEpochMillis = sessionStart,
                    endedAtEpochMillis = sessionEnd,
                    durationMillis = 30 * 60 * 1000L,
                ),
            ),
            entries = emptyList(),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)

        assertEquals(30 * 60 * 1000L, overview.stats.readingTimeMillis)
        assertEquals(15 * 60 * 1000L / 30L, overview.averageReadingTimeBuckets[10].averageMillis)
        assertEquals(15 * 60 * 1000L / 30L, overview.averageReadingTimeBuckets[11].averageMillis)
    }

    @Test
    fun `activity day finished counts use item kind media classification`() {
        val kavitaPdf = testItem.copy(
            id = "kavita-pdf",
            sourceType = SourceType.KAVITA,
            format = FormatType.PDF,
            kind = ReadableItemKind.BOOK,
        )
        val grimmoryPdf = testItem.copy(
            id = "grimmory-pdf",
            sourceType = SourceType.GRIMMORY,
            format = FormatType.PDF,
            kind = ReadableItemKind.COMIC,
        )

        val activityDay = computeActivityDays(
            listOf(
                startedEvent(item = kavitaPdf, id = "kavita-finished", page = 100, percent = 1.0, type = JournalReadingEventType.FINISHED),
                startedEvent(item = grimmoryPdf, id = "grimmory-finished", page = 100, percent = 1.0, type = JournalReadingEventType.FINISHED),
            ),
        ).single()

        assertEquals(1, activityDay.finishedBooks)
        assertEquals(1, activityDay.finishedComics)
    }

    @Test
    fun `period overview activity days include session only reading`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = emptyList(),
            sessions = listOf(
                journalSession(
                    startedAtEpochMillis = april10 + 10 * 60 * 60 * 1000L,
                    endedAtEpochMillis = april10 + 11 * 60 * 60 * 1000L,
                    fromPage = 0,
                    toPage = 20,
                ),
            ),
            entries = emptyList(),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)
        val activityDay = overview.activityDays.single()

        assertEquals(10, activityDay.dayOfMonth)
        assertEquals(20, activityDay.pagesRead)
        assertEquals(1, activityDay.eventCount)
        assertEquals(20, overview.stats.pagesRead)
        assertEquals(60 * 60 * 1000L, overview.stats.readingTimeMillis)
    }

    @Test
    fun `top facets omit items without metadata values`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val known = testItem.copy(
            id = "known",
            bookMetadata = BookMetadata(authors = listOf("Ursula Le Guin"), genres = listOf("Fantasy")),
        )
        val missing = testItem.copy(id = "missing")
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = listOf(
                startedEvent(
                    item = known,
                    id = "known-finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                ),
                startedEvent(
                    item = missing,
                    id = "missing-finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                ),
            ),
            sessions = emptyList(),
            entries = emptyList(),
            catalogItemsById = mapOf(known.id to known, missing.id to missing),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)
        val bookGenres = overview.topFacets.first { section -> section.title == "Book genres" }

        assertEquals(listOf(known.id), bookGenres.rows.single { row -> row.label == "Fantasy" }.itemIds)
        assertEquals(emptyList<JournalFacetRow>(), bookGenres.rows.filter { row -> row.itemIds.contains(missing.id) })
    }

    @Test
    fun `top facets include event-only finished items`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val finished = testItem.copy(
            id = "finished",
            bookMetadata = BookMetadata(authors = listOf("Octavia Butler"), genres = listOf("Science Fiction")),
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = listOf(
                startedEvent(
                    item = finished,
                    id = "finished-event",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                ),
            ),
            sessions = emptyList(),
            entries = emptyList(),
            catalogItemsById = mapOf(finished.id to finished),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)
        val bookGenres = overview.topFacets.first { section -> section.title == "Book genres" }

        assertEquals(listOf(finished.id), bookGenres.rows.first { row -> row.label == "Science Fiction" }.itemIds)
    }

    @Test
    fun `top facets match catalog metadata when journal item id is stale`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val eventItem = testItem.copy(id = "stale-id", title = "Same Book")
        val catalogItem = testItem.copy(
            id = "fresh-id",
            title = "Same Book",
            bookMetadata = BookMetadata(genres = listOf("Fantasy")),
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = listOf(
                startedEvent(
                    item = eventItem,
                    id = "stale-finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                ),
            ),
            sessions = emptyList(),
            entries = emptyList(),
            catalogItemsById = mapOf(catalogItem.id to catalogItem),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)
        val bookGenres = overview.topFacets.first { section -> section.title == "Book genres" }

        assertEquals(listOf(eventItem.id), bookGenres.rows.single { row -> row.label == "Fantasy" }.itemIds)
        assertEquals(emptyList<JournalFacetRow>(), bookGenres.rows.filter { row -> row.label == "Unknown" })
    }

    @Test
    fun `top facets exclude unfinished active items`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val started = testItem.copy(
            id = "started",
            bookMetadata = BookMetadata(authors = listOf("Started Author"), genres = listOf("Started Genre")),
        )
        val continued = testItem.copy(
            id = "continued",
            bookMetadata = BookMetadata(authors = listOf("Continued Author"), genres = listOf("Continued Genre")),
        )
        val sessionOnly = testItem.copy(
            id = "session-only",
            bookMetadata = BookMetadata(authors = listOf("Session Author"), genres = listOf("Session Genre")),
        )
        val finished = testItem.copy(
            id = "finished",
            bookMetadata = BookMetadata(authors = listOf("Finished Author"), genres = listOf("Finished Genre")),
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = listOf(
                startedEvent(
                    item = started,
                    id = "started-event",
                    page = 20,
                    percent = 0.2,
                    occurredAtEpochMillis = april10,
                ),
                startedEvent(
                    item = continued,
                    id = "continued-event",
                    page = 40,
                    percent = 0.4,
                    type = JournalReadingEventType.CONTINUED,
                    occurredAtEpochMillis = april10,
                ),
                startedEvent(
                    item = finished,
                    id = "finished-event",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                ),
            ),
            sessions = listOf(
                journalSession(item = sessionOnly, startedAtEpochMillis = april10, endedAtEpochMillis = april10 + 1_000L),
            ),
            entries = emptyList(),
            catalogItemsById = listOf(started, continued, sessionOnly, finished).associateBy(ReadableItem::id),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)
        val bookGenres = overview.topFacets.first { section -> section.title == "Book genres" }
        val bookAuthors = overview.topFacets.first { section -> section.title == "Book authors" }

        assertEquals(setOf(started.id, continued.id, sessionOnly.id, finished.id), overview.activeItems.map { it.item.itemId }.toSet())
        assertEquals(listOf(finished.id), bookGenres.rows.single { row -> row.label == "Finished Genre" }.itemIds)
        assertEquals(listOf(finished.id), bookAuthors.rows.single { row -> row.label == "Finished Author" }.itemIds)
        assertEquals(emptyList<JournalFacetRow>(), bookGenres.rows.filter { row -> row.label != "Finished Genre" })
    }

    @Test
    fun `top facets exclude rating-only journal entries`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val ratedOnly = testItem.copy(
            id = "rated-only",
            bookMetadata = BookMetadata(authors = listOf("Unopened Author"), genres = listOf("Unopened Genre")),
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = emptyList(),
            sessions = emptyList(),
            entries = listOf(
                journalEntry(item = ratedOnly, rating = 8, updatedAtEpochMillis = april10),
            ),
            catalogItemsById = mapOf(ratedOnly.id to ratedOnly),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)

        assertEquals(mapOf(8 to 1), overview.stats.ratingCountsByMediaType[JournalMediaType.BOOK])
        assertEquals(
            listOf("Book genres", "Book authors", "Comic genres", "Comic tags", "Comic writers", "Comic pencillers"),
            overview.topFacets.map(JournalFacetSection::title),
        )
        assertEquals(emptyList<JournalFacetRow>(), overview.topFacets.flatMap(JournalFacetSection::rows))
        assertEquals(emptyList<JournalItemSummary>(), overview.activeItems)
    }

    @Test
    fun `top facets split comic writers and pencillers`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val comic = testItem.copy(
            id = "comic",
            sourceType = SourceType.KAVITA,
            kind = ReadableItemKind.COMIC,
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Saga",
                writers = listOf("Brian K. Vaughan"),
                pencillers = listOf("Fiona Staples"),
                genres = listOf("Science Fiction"),
            ),
        )
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = listOf(
                startedEvent(
                    item = comic,
                    id = "comic-finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                ),
            ),
            sessions = emptyList(),
            entries = emptyList(),
            catalogItemsById = mapOf(comic.id to comic),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)
        val writers = overview.topFacets.first { section -> section.title == "Comic writers" }
        val pencillers = overview.topFacets.first { section -> section.title == "Comic pencillers" }

        assertEquals(JournalFacetType.WRITER, writers.facetType)
        assertEquals(listOf(comic.id), writers.rows.single { row -> row.label == "Brian K. Vaughan" }.itemIds)
        assertEquals(JournalFacetType.PENCILLER, pencillers.facetType)
        assertEquals(listOf(comic.id), pencillers.rows.single { row -> row.label == "Fiona Staples" }.itemIds)
    }

    @Test
    fun `finished shelf helper keeps only unrated finished items`() {
        val april10 = epochMillis(2026, Calendar.APRIL, 10)
        val rated = testItem.copy(id = "rated")
        val unrated = testItem.copy(id = "unrated")
        val extraFinishedItems = (1..30).map { index ->
            testItem.copy(id = "extra-$index")
        }
        val allFinishedItems = listOf(rated, unrated) + extraFinishedItems
        val snapshot = JournalStateComputer(nowEpochMillis = { april10 }).snapshot(
            events = allFinishedItems.map { item ->
                startedEvent(
                    item = item,
                    id = "${item.id}-finished",
                    page = 100,
                    percent = 1.0,
                    type = JournalReadingEventType.FINISHED,
                    occurredAtEpochMillis = april10,
                )
            },
            entries = listOf(
                journalEntry(item = rated, rating = 8, updatedAtEpochMillis = april10),
            ),
            catalogItemsById = allFinishedItems.associateBy(ReadableItem::id),
        )

        val overview = computePeriodOverview(snapshot, JournalPeriod.MONTH, april10)

        assertEquals(
            allFinishedItems.map(ReadableItem::id).toSet() - rated.id,
            finishedItemsNeedingRating(overview).map { summary -> summary.item.itemId }.toSet(),
        )
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
        item: ReadableItem = testItem,
        itemId: String = item.id,
        rating: Int,
        updatedAtEpochMillis: Long,
    ): ItemJournalEntry = ItemJournalEntry(
        itemId = itemId,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        rating = rating,
        reviewText = "",
        reviewedAtEpochMillis = null,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun journalSession(
        item: ReadableItem = testItem,
        startedAtEpochMillis: Long,
        endedAtEpochMillis: Long,
        durationMillis: Long = endedAtEpochMillis - startedAtEpochMillis,
        fromPage: Int? = null,
        toPage: Int? = null,
    ): JournalReadingSession = JournalReadingSession(
        id = "session:${item.id}:$startedAtEpochMillis",
        itemId = item.id,
        sourceType = item.sourceType,
        libraryId = item.libraryId,
        itemTitle = item.title,
        coverUrl = item.coverUrl,
        itemKind = item.kind,
        format = item.format,
        mediaType = classifyJournalMedia(
            sourceType = item.sourceType,
            itemKind = item.kind,
            format = item.format,
        ),
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        durationMillis = durationMillis,
        fromPercent = null,
        toPercent = null,
        fromPage = fromPage,
        toPage = toPage,
        pageCount = item.pageCount,
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
