package com.example.unifiedreader.feature.journal

import com.example.unifiedreader.core.database.dao.JournalDao
import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.journal.journalMediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomJournalRepository(
    private val journalDao: JournalDao,
    private val remoteCatalogDao: RemoteCatalogDao,
    private val stateComputer: JournalStateComputer = JournalStateComputer(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JournalRepository, JournalReadingSessionRecorder {
    private val recordMutex = Mutex()

    override fun observeSnapshot(): Flow<JournalSnapshot> = observeSnapshot(flowOf(emptyList()))

    override fun observeSnapshot(extraCatalogItems: Flow<List<ReadableItem>>): Flow<JournalSnapshot> = snapshotFor(
        combine(
            remoteCatalogDao.observeLibraryItems().map { entities -> entities.map { it.toDomain() } },
            extraCatalogItems,
        ) { catalogItems, extraItems -> (catalogItems + extraItems).associateBy(ReadableItem::id) },
    )

    override fun observeSnapshotForCatalog(catalogItems: Flow<List<ReadableItem>>): Flow<JournalSnapshot> = snapshotFor(
        catalogItems.map { items -> items.associateBy(ReadableItem::id) },
    )

    private fun snapshotFor(catalogItemsById: Flow<Map<String, ReadableItem>>): Flow<JournalSnapshot> = combine(
        journalDao.observeReadingEvents().map { entities -> entities.map { it.toDomain() } },
        journalDao.observeReadingSessions().map { entities -> entities.map { it.toDomain() } },
        journalDao.observeJournalEntries().map { entities -> entities.map { it.toDomain() } },
        catalogItemsById,
    ) { events, sessions, entries, catalogItemsById ->
        stateComputer.snapshot(
            events = events,
            sessions = sessions,
            entries = entries,
            catalogItemsById = catalogItemsById,
        )
    }
        // Matching the journal to the whole library is heavy: never on the collector's main thread.
        .flowOn(Dispatchers.Default)

    override fun observeWriteState(): Flow<JournalSnapshot> = combine(
        journalDao.observeReadingEvents().map { entities -> entities.map { it.toDomain() } },
        journalDao.observeReadingSessions().map { entities -> entities.map { it.toDomain() } },
        journalDao.observeJournalEntries().map { entities -> entities.map { it.toDomain() } },
    ) { events, sessions, entries ->
        JournalSnapshot(
            events = events.sortedByDescending(JournalReadingEvent::occurredAtEpochMillis),
            sessions = sessions.sortedByDescending(JournalReadingSession::endedAtEpochMillis),
            entries = entries.sortedByDescending(ItemJournalEntry::updatedAtEpochMillis),
        )
    }

    override suspend fun recordReaderProgress(
        input: JournalProgressInput,
    ) {
        withContext(ioDispatcher) {
            recordMutex.withLock {
                val existingEvents = journalDao.getReadingEventsForItem(input.item.id)
                    .map { entity -> entity.toDomain() }
                val latestEvent = existingEvents.maxByOrNull(JournalReadingEvent::occurredAtEpochMillis)
                val next = takeBackVisitFinish(
                    input = input,
                    latestEvent = latestEvent,
                    hasEarlierEvents = existingEvents.size > 1,
                ) ?: buildJournalEvent(
                    input = input,
                    latestEvent = latestEvent,
                ) ?: return@withLock
                journalDao.insertReadingEvent(next.toEntity())
            }
        }
    }

    override suspend fun recordReadingSession(
        input: JournalReadingSessionInput,
    ) {
        withContext(ioDispatcher) {
            val session = buildJournalReadingSession(input) ?: return@withContext
            journalDao.insertReadingSession(session.toEntity())
        }
    }

    override suspend fun saveJournalEntry(
        item: ReadableItem,
        rating: Int?,
        reviewText: String,
    ) {
        withContext(ioDispatcher) {
            val now = nowEpochMillis()
            val trimmedReview = reviewText.trim()
            val existingEntry = journalDao.getJournalEntry(item.id)?.toDomain()
            journalDao.insertJournalEntry(
                item.toJournalEntry(
                    rating = rating,
                    reviewText = trimmedReview,
                    reviewedAtEpochMillis = now.takeIf { trimmedReview.isNotBlank() },
                    updatedAtEpochMillis = now,
                ).copy(
                    remoteEntryId = existingEntry?.remoteEntryId,
                    remoteReviewNoteId = existingEntry?.remoteReviewNoteId,
                    remoteUpdatedAtEpochMillis = existingEntry?.remoteUpdatedAtEpochMillis,
                    syncState = item.sourceType.localJournalWriteSyncState(),
                ).toEntity(),
            )
        }
    }

    override suspend fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    ) {
        withContext(ioDispatcher) {
            val now = nowEpochMillis()
            val trimmedReview = reviewText.trim()
            val existingEntry = journalDao.getJournalEntry(item.itemId)?.toDomain()
            journalDao.insertJournalEntry(
                item.toJournalEntry(
                    rating = rating,
                    reviewText = trimmedReview,
                    reviewedAtEpochMillis = now.takeIf { trimmedReview.isNotBlank() },
                    updatedAtEpochMillis = now,
                ).copy(
                    remoteEntryId = existingEntry?.remoteEntryId,
                    remoteReviewNoteId = existingEntry?.remoteReviewNoteId,
                    remoteUpdatedAtEpochMillis = existingEntry?.remoteUpdatedAtEpochMillis,
                    syncState = item.sourceType.localJournalWriteSyncState(),
                ).toEntity(),
            )
        }
    }

    override suspend fun deleteItemJournal(
        itemId: String,
    ) {
        withContext(ioDispatcher) {
            journalDao.deleteReadingEventsForItem(itemId)
            journalDao.deleteReadingSessionsForItem(itemId)
            journalDao.deleteJournalEntry(itemId)
        }
    }

    override suspend fun takeBackFinishesSince(
        itemId: String,
        sinceEpochMillis: Long,
    ) {
        withContext(ioDispatcher) {
            journalDao.deleteFinishedReadingEventsForItemSince(itemId, sinceEpochMillis)
        }
    }

    override suspend fun getReadingSessionsForItem(
        itemId: String,
    ): List<JournalReadingSession> = withContext(ioDispatcher) {
        journalDao.getReadingSessionsForItem(itemId)
            .map { entity -> entity.toDomain() }
    }

    override suspend fun replaceCleanSourceJournalCache(
        sourceType: SourceType,
        events: List<JournalReadingEvent>,
        entries: List<ItemJournalEntry>,
        sessions: List<JournalReadingSession>,
        preserveReviewFieldsWhenIncomingBlank: Boolean,
        preserveRatingWhenIncomingBlank: Boolean,
        preserveReadingEventsWhenIncomingIncomplete: Boolean,
        preserveReadingSessionsWhenIncomingIncomplete: Boolean,
        readingEventReplacementItemIds: Set<String>?,
        readingSessionReplacementItemIds: Set<String>?,
        journalEntryReplacementItemIds: Set<String>?,
        preserveRatingOnlyEntriesWhenIncomingMissing: Boolean,
        ratingOnlyPreservationExcludedItemIds: Set<String>?,
    ) {
        withContext(ioDispatcher) {
            journalDao.replaceCleanSourceJournalCache(
                sourceType = sourceType.name,
                events = events.map { event -> event.copy(syncState = SyncState.CLEAN).toEntity() },
                entries = entries.map { entry -> entry.copy(syncState = SyncState.CLEAN).toEntity() },
                sessions = sessions.map { session -> session.copy(syncState = SyncState.CLEAN).toEntity() },
                preserveReviewFieldsWhenIncomingBlank = preserveReviewFieldsWhenIncomingBlank,
                preserveRatingWhenIncomingBlank = preserveRatingWhenIncomingBlank,
                preserveReadingEventsWhenIncomingIncomplete = preserveReadingEventsWhenIncomingIncomplete,
                preserveReadingSessionsWhenIncomingIncomplete = preserveReadingSessionsWhenIncomingIncomplete,
                readingEventReplacementItemIds = readingEventReplacementItemIds,
                readingSessionReplacementItemIds = readingSessionReplacementItemIds,
                journalEntryReplacementItemIds = journalEntryReplacementItemIds,
                preserveRatingOnlyEntriesWhenIncomingMissing = preserveRatingOnlyEntriesWhenIncomingMissing,
                ratingOnlyPreservationExcludedItemIds = ratingOnlyPreservationExcludedItemIds,
            )
        }
    }

    override suspend fun getDirtyJournalEntriesForSource(
        sourceType: SourceType,
    ): List<ItemJournalEntry> = withContext(ioDispatcher) {
        journalDao.getDirtyJournalEntriesForSource(sourceType.name)
            .map { entity -> entity.toDomain() }
    }

    override suspend fun getCleanJournalEntriesForSource(
        sourceType: SourceType,
    ): List<ItemJournalEntry> = withContext(ioDispatcher) {
        journalDao.getCleanJournalEntriesForSource(sourceType.name)
            .map { entity -> entity.toDomain() }
    }

    override suspend fun getDirtyReadingSessionsForSource(
        sourceType: SourceType,
    ): List<JournalReadingSession> = withContext(ioDispatcher) {
        journalDao.getDirtyReadingSessionsForSource(sourceType.name)
            .map { entity -> entity.toDomain() }
    }

    override suspend fun markJournalEntrySyncState(
        itemId: String,
        syncState: SyncState,
    ) {
        withContext(ioDispatcher) {
            journalDao.updateJournalEntrySyncState(itemId, syncState.name)
        }
    }

    override suspend fun updateJournalEntryRemoteReviewNoteId(
        itemId: String,
        remoteReviewNoteId: String?,
    ) {
        withContext(ioDispatcher) {
            journalDao.updateJournalEntryRemoteReviewNoteId(itemId, remoteReviewNoteId)
        }
    }

    override suspend fun markReadingSessionSyncState(
        sessionId: String,
        syncState: SyncState,
    ) {
        withContext(ioDispatcher) {
            journalDao.updateReadingSessionSyncState(sessionId, syncState.name)
        }
    }
}

private fun buildJournalReadingSession(
    input: JournalReadingSessionInput,
): JournalReadingSession? {
    val startedAt = minOf(input.startedAtEpochMillis, input.endedAtEpochMillis)
    val endedAt = maxOf(input.startedAtEpochMillis, input.endedAtEpochMillis)
    val duration = endedAt - startedAt
    if (duration <= 0L) return null
    return JournalReadingSession(
        id = "local:${input.item.id}:$startedAt:$endedAt",
        itemId = input.item.id,
        sourceType = input.item.sourceType,
        libraryId = input.item.libraryId,
        itemTitle = input.item.title,
        coverUrl = input.item.coverUrl,
        itemKind = input.item.kind,
        format = input.item.format,
        mediaType = input.item.journalMediaType(),
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = endedAt,
        durationMillis = duration,
        fromPercent = input.fromPercent?.coerceIn(0.0, 1.0),
        toPercent = input.toPercent?.coerceIn(0.0, 1.0),
        fromPage = input.fromPage,
        toPage = input.toPage,
        pageCount = input.pageCount ?: input.item.pageCount,
        syncState = input.syncState ?: SyncState.CLEAN,
    )
}

private fun SourceType.localJournalWriteSyncState(): SyncState = when (this) {
    SourceType.KAVITA,
    SourceType.GRIMMORY -> SyncState.DIRTY
    SourceType.STUMP,
    SourceType.LOCAL -> SyncState.CLEAN
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
    val readsAgain = latestEvent?.eventType == JournalReadingEventType.FINISHED &&
        latestPosition?.isMeaningfullyAfter(position) == true
    val eventType = when {
        input.isFinished && latestEvent?.eventType != JournalReadingEventType.FINISHED -> JournalReadingEventType.FINISHED
        latestEvent == null -> JournalReadingEventType.STARTED
        // A finished book read again: started over from its first tenth, or a part of it read again.
        readsAgain && position.isStartingOver() -> JournalReadingEventType.STARTED
        readsAgain -> JournalReadingEventType.CONTINUED
        latestEvent.eventType == JournalReadingEventType.FINISHED -> null
        position.isMeaningfullyAfter(latestPosition) -> JournalReadingEventType.CONTINUED
        else -> null
    } ?: return null
    // A read started over begins where it starts, not at the end where the last one finished.
    val from = if (readsAgain && eventType == JournalReadingEventType.STARTED) position else latestPosition
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
        fromPercent = from?.percent,
        toPercent = position.percent,
        fromPage = from?.page,
        toPage = position.page,
        pageCount = input.pageCount ?: input.item.pageCount,
        occurredAtEpochMillis = input.occurredAtEpochMillis,
    )
}

/**
 * Reaching the end and turning back is not finishing the book: the finish recorded on this visit
 * (see [JournalProgressInput.visitStartedAtEpochMillis]) goes back to reading progress once the place
 * moves back from it. A finish from an earlier visit stays: going back then is reading it again.
 */
internal fun takeBackVisitFinish(
    input: JournalProgressInput,
    latestEvent: JournalReadingEvent?,
    hasEarlierEvents: Boolean,
): JournalReadingEvent? {
    val visitStartedAt = input.visitStartedAtEpochMillis ?: return null
    if (input.isFinished) return null
    val finish = latestEvent?.takeIf { event ->
        event.eventType == JournalReadingEventType.FINISHED &&
            event.remoteEventId == null &&
            event.occurredAtEpochMillis >= visitStartedAt
    } ?: return null
    val position = JournalPosition.from(input)
    if (!JournalPosition.from(finish).isMeaningfullyAfter(position)) return null
    return finish.copy(
        eventType = if (hasEarlierEvents) JournalReadingEventType.CONTINUED else JournalReadingEventType.STARTED,
        toPercent = position.percent,
        toPage = position.page,
        occurredAtEpochMillis = input.occurredAtEpochMillis,
    )
}

private data class JournalPosition(
    val page: Int?,
    val percent: Double?,
) {
    fun isStartingOver(): Boolean = percent != null && percent < JournalStartedOverPercent

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
            val page = input.page
            val percent = input.percent
                ?: if (page != null && pageCount != null && pageCount > 0) {
                    page.toDouble() / pageCount.toDouble()
                } else {
                    null
                }
            return JournalPosition(
                page = page,
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
