package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
import com.example.unifiedreader.core.database.entity.JournalReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class JournalDao {
    @Query("SELECT * FROM journal_reading_events ORDER BY occurredAtEpochMillis DESC")
    abstract fun observeReadingEvents(): Flow<List<JournalReadingEventEntity>>

    @Query("SELECT * FROM journal_reading_events ORDER BY occurredAtEpochMillis DESC")
    abstract fun getReadingEvents(): List<JournalReadingEventEntity>

    @Query("SELECT * FROM journal_reading_events WHERE itemId = :itemId ORDER BY occurredAtEpochMillis DESC")
    abstract fun getReadingEventsForItem(itemId: String): List<JournalReadingEventEntity>

    @Query("SELECT id FROM journal_reading_events WHERE sourceType = :sourceType AND syncState IN ('DIRTY', 'FAILED')")
    abstract fun getDirtyReadingEventIdsForSource(sourceType: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertReadingEvent(event: JournalReadingEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertReadingEvents(events: List<JournalReadingEventEntity>)

    @Query("SELECT * FROM journal_reading_sessions ORDER BY endedAtEpochMillis DESC")
    abstract fun observeReadingSessions(): Flow<List<JournalReadingSessionEntity>>

    @Query("SELECT * FROM journal_reading_sessions WHERE itemId = :itemId ORDER BY endedAtEpochMillis DESC")
    abstract fun getReadingSessionsForItem(itemId: String): List<JournalReadingSessionEntity>

    @Query("SELECT id FROM journal_reading_sessions WHERE sourceType = :sourceType AND syncState IN ('DIRTY', 'FAILED')")
    abstract fun getDirtyReadingSessionIdsForSource(sourceType: String): List<String>

    @Query("SELECT * FROM journal_reading_sessions WHERE sourceType = :sourceType AND syncState IN ('DIRTY', 'FAILED') ORDER BY endedAtEpochMillis ASC")
    abstract fun getDirtyReadingSessionsForSource(sourceType: String): List<JournalReadingSessionEntity>

    @Query("SELECT * FROM journal_reading_sessions WHERE sourceType = :sourceType AND remoteSessionId IS NULL AND id LIKE 'local:%'")
    abstract fun getLocalReadingSessionsForSource(sourceType: String): List<JournalReadingSessionEntity>

    @Query("UPDATE journal_reading_sessions SET syncState = 'CLEAN' WHERE sourceType = :sourceType AND remoteSessionId IS NULL AND id LIKE 'local:%'")
    abstract fun markLocalReadingSessionsCleanForSource(sourceType: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertReadingSession(session: JournalReadingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertReadingSessions(sessions: List<JournalReadingSessionEntity>)

    @Query("DELETE FROM journal_reading_sessions WHERE itemId = :itemId")
    abstract fun deleteReadingSessionsForItem(itemId: String): Int

    @Query("DELETE FROM journal_reading_sessions WHERE sourceType = :sourceType AND syncState = 'CLEAN'")
    abstract fun deleteCleanReadingSessionsForSource(sourceType: String): Int

    @Query("DELETE FROM journal_reading_sessions WHERE sourceType = :sourceType AND syncState = 'CLEAN' AND itemId IN (:itemIds)")
    abstract fun deleteCleanReadingSessionsForSourceItems(sourceType: String, itemIds: List<String>): Int

    @Query("DELETE FROM journal_reading_sessions WHERE id IN (:sessionIds)")
    abstract fun deleteReadingSessionsByIds(sessionIds: List<String>): Int

    @Query("UPDATE journal_reading_sessions SET syncState = :syncState WHERE id = :sessionId")
    abstract fun updateReadingSessionSyncState(sessionId: String, syncState: String): Int

    @Query("DELETE FROM journal_reading_events WHERE itemId = :itemId")
    abstract fun deleteReadingEventsForItem(itemId: String): Int

    @Query("DELETE FROM journal_reading_events WHERE sourceType = :sourceType AND syncState = 'CLEAN'")
    abstract fun deleteCleanReadingEventsForSource(sourceType: String): Int

    @Query("DELETE FROM journal_reading_events WHERE sourceType = :sourceType AND syncState = 'CLEAN' AND itemId IN (:itemIds)")
    abstract fun deleteCleanReadingEventsForSourceItems(sourceType: String, itemIds: List<String>): Int

    @Query("SELECT * FROM item_journal_entries ORDER BY updatedAtEpochMillis DESC")
    abstract fun observeJournalEntries(): Flow<List<ItemJournalEntryEntity>>

    @Query("SELECT * FROM item_journal_entries WHERE itemId = :itemId")
    abstract fun getJournalEntry(itemId: String): ItemJournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertJournalEntry(entry: ItemJournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertJournalEntries(entries: List<ItemJournalEntryEntity>)

    @Query("SELECT * FROM item_journal_entries WHERE sourceType = :sourceType AND syncState IN ('DIRTY', 'FAILED') ORDER BY updatedAtEpochMillis ASC")
    abstract fun getDirtyJournalEntriesForSource(sourceType: String): List<ItemJournalEntryEntity>

    @Query("SELECT * FROM item_journal_entries WHERE sourceType = :sourceType AND syncState = 'CLEAN'")
    abstract fun getCleanJournalEntriesForSource(sourceType: String): List<ItemJournalEntryEntity>

    @Query("SELECT itemId FROM item_journal_entries WHERE sourceType = :sourceType AND syncState IN ('DIRTY', 'FAILED')")
    abstract fun getDirtyJournalEntryIdsForSource(sourceType: String): List<String>

    @Query("DELETE FROM item_journal_entries WHERE sourceType = :sourceType AND syncState = 'CLEAN'")
    abstract fun deleteCleanJournalEntriesForSource(sourceType: String): Int

    @Query("DELETE FROM item_journal_entries WHERE sourceType = :sourceType AND syncState = 'CLEAN' AND itemId IN (:itemIds)")
    abstract fun deleteCleanJournalEntriesForSourceItems(sourceType: String, itemIds: List<String>): Int

    @Query("DELETE FROM item_journal_entries WHERE itemId = :itemId")
    abstract fun deleteJournalEntry(itemId: String): Int

    @Query("UPDATE item_journal_entries SET syncState = :syncState WHERE itemId = :itemId")
    abstract fun updateJournalEntrySyncState(itemId: String, syncState: String): Int

    @Query("UPDATE item_journal_entries SET remoteReviewNoteId = :remoteReviewNoteId WHERE itemId = :itemId")
    abstract fun updateJournalEntryRemoteReviewNoteId(itemId: String, remoteReviewNoteId: String?): Int

    @Transaction
    open fun replaceCleanSourceJournalCache(
        sourceType: String,
        events: List<JournalReadingEventEntity>,
        entries: List<ItemJournalEntryEntity>,
        sessions: List<JournalReadingSessionEntity> = emptyList(),
        preserveReviewFieldsWhenIncomingBlank: Boolean = false,
        preserveRatingWhenIncomingBlank: Boolean = false,
        preserveReadingEventsWhenIncomingIncomplete: Boolean = false,
        preserveReadingSessionsWhenIncomingIncomplete: Boolean = false,
        readingEventReplacementItemIds: Set<String>? = null,
        readingSessionReplacementItemIds: Set<String>? = null,
        journalEntryReplacementItemIds: Set<String>? = null,
        preserveRatingOnlyEntriesWhenIncomingMissing: Boolean = false,
        ratingOnlyPreservationExcludedItemIds: Set<String>? = null,
    ) {
        val protectedEventIds = getDirtyReadingEventIdsForSource(sourceType).toSet()
        val localSessionsToPreserve = getLocalReadingSessionsForSource(sourceType)
            .filterNot { localSession ->
                sessions.any { incomingSession -> incomingSession.overlaps(localSession) }
            }
        val localSessionIdsToPreserve = localSessionsToPreserve.mapTo(mutableSetOf(), JournalReadingSessionEntity::id)
        val dirtySessionIdsToReplace = getDirtyReadingSessionsForSource(sourceType)
            .filter { dirtySession ->
                sessions.any { incomingSession -> incomingSession.overlaps(dirtySession) }
            }
            .mapTo(mutableSetOf(), JournalReadingSessionEntity::id)
        val protectedSessionIds = getDirtyReadingSessionIdsForSource(sourceType).toSet() -
            dirtySessionIdsToReplace +
            localSessionIdsToPreserve
        val protectedEntryIds = getDirtyJournalEntryIdsForSource(sourceType).toSet()
        val cleanEntriesByItemId = if (preserveReviewFieldsWhenIncomingBlank || preserveRatingOnlyEntriesWhenIncomingMissing) {
            getCleanJournalEntriesForSource(sourceType).associateBy(ItemJournalEntryEntity::itemId)
        } else {
            emptyMap()
        }
        val mergedEntries = entries
            .filterNot { entry -> entry.itemId in protectedEntryIds }
            .map { entry ->
                val existing = cleanEntriesByItemId[entry.itemId]
                if (existing != null && entry.reviewText.isBlank() && existing.hasReviewCache()) {
                    entry.copy(
                        rating = if (preserveRatingWhenIncomingBlank) existing.rating else entry.rating,
                        reviewText = existing.reviewText,
                        reviewedAtEpochMillis = existing.reviewedAtEpochMillis,
                        remoteEntryId = existing.remoteEntryId,
                        remoteReviewNoteId = existing.remoteReviewNoteId,
                        remoteUpdatedAtEpochMillis = listOfNotNull(
                            entry.remoteUpdatedAtEpochMillis,
                            existing.remoteUpdatedAtEpochMillis,
                        ).maxOrNull(),
                    )
                } else {
                    entry
                }
            }
        val mergedEntryIds = mergedEntries.map(ItemJournalEntryEntity::itemId).toSet()
        val reviewOnlyEntries = if (preserveReviewFieldsWhenIncomingBlank) {
            cleanEntriesByItemId.values.filter { entry ->
                entry.itemId !in protectedEntryIds &&
                    entry.itemId !in mergedEntryIds &&
                    entry.hasReviewCache()
            }.map { entry ->
                entry.toReviewOnlyCacheEntry(
                    preserveRating = preserveRatingWhenIncomingBlank,
                )
            }
        } else {
            emptyList()
        }
        val reviewOnlyEntryIds = reviewOnlyEntries.mapTo(mutableSetOf(), ItemJournalEntryEntity::itemId)
        val ratingOnlyPreservationExcludedIds = ratingOnlyPreservationExcludedItemIds.orEmpty()
        val ratingOnlyEntries = if (preserveRatingOnlyEntriesWhenIncomingMissing) {
            cleanEntriesByItemId.values.filter { entry ->
                entry.itemId !in protectedEntryIds &&
                    entry.itemId !in mergedEntryIds &&
                    entry.itemId !in reviewOnlyEntryIds &&
                    entry.itemId !in ratingOnlyPreservationExcludedIds &&
                    entry.rating != null
            }.map(ItemJournalEntryEntity::toRatingOnlyCacheEntry)
        } else {
            emptyList()
        }
        if (readingEventReplacementItemIds != null) {
            val replaceItemIds = readingEventReplacementItemIds.filterNot(String::isBlank)
            if (replaceItemIds.isNotEmpty()) {
                deleteCleanReadingEventsForSourceItems(sourceType, replaceItemIds)
            }
        } else if (!preserveReadingEventsWhenIncomingIncomplete) {
            deleteCleanReadingEventsForSource(sourceType)
        }
        if (readingSessionReplacementItemIds != null) {
            val replaceItemIds = readingSessionReplacementItemIds.filterNot(String::isBlank)
            if (replaceItemIds.isNotEmpty()) {
                deleteCleanReadingSessionsForSourceItems(sourceType, replaceItemIds)
            }
        } else if (!preserveReadingSessionsWhenIncomingIncomplete) {
            deleteCleanReadingSessionsForSource(sourceType)
        }
        if (dirtySessionIdsToReplace.isNotEmpty()) {
            deleteReadingSessionsByIds(dirtySessionIdsToReplace.toList())
        }
        if (journalEntryReplacementItemIds != null) {
            val replaceItemIds = journalEntryReplacementItemIds.filterNot(String::isBlank)
            if (replaceItemIds.isNotEmpty()) {
                deleteCleanJournalEntriesForSourceItems(sourceType, replaceItemIds)
            }
        } else {
            deleteCleanJournalEntriesForSource(sourceType)
        }
        insertReadingEvents(events.filterNot { event -> event.id in protectedEventIds })
        insertReadingSessions(localSessionsToPreserve + sessions.filterNot { session -> session.id in protectedSessionIds })
        insertJournalEntries(mergedEntries + reviewOnlyEntries + ratingOnlyEntries)
    }
}

private fun ItemJournalEntryEntity.hasReviewCache(): Boolean =
    reviewText.isNotBlank() || remoteEntryId != null || remoteReviewNoteId != null

private fun ItemJournalEntryEntity.toReviewOnlyCacheEntry(
    preserveRating: Boolean,
): ItemJournalEntryEntity = ItemJournalEntryEntity(
    itemId = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind,
    format = format,
    rating = if (preserveRating) rating else null,
    reviewText = reviewText,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = reviewedAtEpochMillis ?: 0L,
    remoteEntryId = remoteEntryId,
    remoteReviewNoteId = remoteReviewNoteId,
    remoteUpdatedAtEpochMillis = null,
    syncState = syncState,
)

private fun ItemJournalEntryEntity.toRatingOnlyCacheEntry(): ItemJournalEntryEntity = ItemJournalEntryEntity(
    itemId = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = itemTitle,
    coverUrl = coverUrl,
    itemKind = itemKind,
    format = format,
    rating = rating,
    reviewText = "",
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    remoteEntryId = null,
    remoteReviewNoteId = null,
    remoteUpdatedAtEpochMillis = null,
    syncState = syncState,
)

private fun JournalReadingSessionEntity.overlaps(
    other: JournalReadingSessionEntity,
): Boolean =
    itemId == other.itemId &&
        startedAtEpochMillis < other.endedAtEpochMillis &&
        endedAtEpochMillis > other.startedAtEpochMillis
