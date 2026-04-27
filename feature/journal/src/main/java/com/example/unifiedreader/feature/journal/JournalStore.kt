package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.database.dao.JournalDao
import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface JournalEventRecorder {
    suspend fun recordReaderProgress(input: JournalProgressInput)
}

interface JournalRepository : JournalEventRecorder {
    fun observeSnapshot(): Flow<JournalSnapshot>

    suspend fun saveJournalEntry(
        item: ReadableItem,
        rating: Int?,
        reviewText: String,
    )

    suspend fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    )

    suspend fun deleteItemJournal(itemId: String)
}

class RoomJournalRepository(
    private val journalDao: JournalDao,
    private val remoteCatalogDao: RemoteCatalogDao,
    private val stateComputer: JournalStateComputer = JournalStateComputer(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : JournalRepository {
    private val recordMutex = Mutex()

    override fun observeSnapshot(): Flow<JournalSnapshot> = combine(
        journalDao.observeReadingEvents().map { entities -> entities.map { it.toDomain() } },
        journalDao.observeJournalEntries().map { entities -> entities.map { it.toDomain() } },
        remoteCatalogDao.observeItems().map { entities -> entities.map { it.toDomain() }.associateBy(ReadableItem::id) },
    ) { events, entries, catalogItemsById ->
        stateComputer.snapshot(
            events = events,
            entries = entries,
            catalogItemsById = catalogItemsById,
        )
    }

    override suspend fun recordReaderProgress(
        input: JournalProgressInput,
    ) {
        withContext(Dispatchers.IO) {
            recordMutex.withLock {
                val existingEvents = journalDao.getReadingEventsForItem(input.item.id)
                    .map { entity -> entity.toDomain() }
                val next = buildJournalEvent(
                    input = input,
                    latestEvent = existingEvents.maxByOrNull(JournalReadingEvent::occurredAtEpochMillis),
                ) ?: return@withLock
                journalDao.insertReadingEvent(next.toEntity())
            }
        }
    }

    override suspend fun saveJournalEntry(
        item: ReadableItem,
        rating: Int?,
        reviewText: String,
    ) {
        withContext(Dispatchers.IO) {
            val now = nowEpochMillis()
            val trimmedReview = reviewText.trim()
            journalDao.insertJournalEntry(
                item.toJournalEntry(
                    rating = rating,
                    reviewText = trimmedReview,
                    reviewedAtEpochMillis = now.takeIf { trimmedReview.isNotBlank() },
                    updatedAtEpochMillis = now,
                ).toEntity(),
            )
        }
    }

    override suspend fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    ) {
        withContext(Dispatchers.IO) {
            val now = nowEpochMillis()
            val trimmedReview = reviewText.trim()
            journalDao.insertJournalEntry(
                item.toJournalEntry(
                    rating = rating,
                    reviewText = trimmedReview,
                    reviewedAtEpochMillis = now.takeIf { trimmedReview.isNotBlank() },
                    updatedAtEpochMillis = now,
                ).toEntity(),
            )
        }
    }

    override suspend fun deleteItemJournal(
        itemId: String,
    ) {
        withContext(Dispatchers.IO) {
            journalDao.deleteReadingEventsForItem(itemId)
            journalDao.deleteJournalEntry(itemId)
        }
    }
}

internal fun buildJournalEvent(
    input: JournalProgressInput,
    latestEvent: JournalReadingEvent?,
): JournalReadingEvent? {
    val position = JournalPosition.from(input)
    val latestPosition = latestEvent?.let(JournalPosition::from)
    if (
        latestEvent != null &&
        latestEvent.eventType != JournalReadingEventType.FINISHED &&
        latestEvent.occurredAtEpochMillis.localDayKey() == input.occurredAtEpochMillis.localDayKey() &&
        (input.isFinished || position.isMeaningfullyAfter(latestPosition))
    ) {
        val eventType = if (input.isFinished) JournalReadingEventType.FINISHED else latestEvent.eventType
        return latestEvent.copy(
            eventType = eventType,
            toPercent = position.percent,
            toPage = position.page,
            pageCount = input.pageCount ?: input.item.pageCount,
            occurredAtEpochMillis = input.occurredAtEpochMillis,
        )
    }
    val eventType = when {
        input.isFinished && latestEvent?.eventType != JournalReadingEventType.FINISHED -> JournalReadingEventType.FINISHED
        latestEvent == null -> JournalReadingEventType.STARTED
        latestEvent.eventType == JournalReadingEventType.FINISHED -> null
        position.isMeaningfullyAfter(latestPosition) -> JournalReadingEventType.CONTINUED
        else -> null
    } ?: return null
    return JournalReadingEvent(
        id = "${input.item.id}:${eventType.name}:${input.occurredAtEpochMillis}",
        itemId = input.item.id,
        sourceType = input.item.sourceType,
        libraryId = input.item.libraryId,
        itemTitle = input.item.title,
        coverUrl = input.item.coverUrl,
        itemKind = input.item.kind,
        format = input.item.format,
        eventType = eventType,
        fromPercent = latestPosition?.percent,
        toPercent = position.percent,
        fromPage = latestPosition?.page,
        toPage = position.page,
        pageCount = input.pageCount ?: input.item.pageCount,
        occurredAtEpochMillis = input.occurredAtEpochMillis,
    )
}

private data class JournalPosition(
    val page: Int?,
    val percent: Double?,
) {
    fun isMeaningfullyAfter(
        previous: JournalPosition?,
    ): Boolean {
        if (previous == null) return true
        val pageDelta = if (page != null && previous.page != null) {
            page - previous.page
        } else {
            null
        }
        if (pageDelta != null && pageDelta >= MIN_CONTINUED_PAGE_DELTA) return true
        val percentDelta = if (percent != null && previous.percent != null) {
            percent - previous.percent
        } else {
            null
        }
        return percentDelta != null && percentDelta >= MIN_CONTINUED_PERCENT_DELTA
    }

    companion object {
        fun from(input: JournalProgressInput): JournalPosition {
            val pageCount = input.pageCount ?: input.item.pageCount
            val percent = input.percent
                ?: if (input.page != null && pageCount != null && pageCount > 0) {
                    input.page.toDouble() / pageCount.toDouble()
                } else {
                    null
                }
            return JournalPosition(
                page = input.page,
                percent = percent?.coerceIn(0.0, 1.0),
            )
        }

        fun from(event: JournalReadingEvent): JournalPosition = JournalPosition(
            page = event.toPage,
            percent = event.toPercent,
        )
    }
}

private const val MIN_CONTINUED_PAGE_DELTA = 3
private const val MIN_CONTINUED_PERCENT_DELTA = 0.02

private fun Long.localDayKey(): Int = localDateKey(this)
