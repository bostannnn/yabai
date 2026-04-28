package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
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
        preserveReviewFieldsWhenIncomingBlank: Boolean = false,
        preserveRatingWhenIncomingBlank: Boolean = false,
        preserveReadingEventsWhenIncomingIncomplete: Boolean = false,
        readingEventReplacementItemIds: Set<String>? = null,
    ) {
        val protectedEventIds = getDirtyReadingEventIdsForSource(sourceType).toSet()
        val protectedEntryIds = getDirtyJournalEntryIdsForSource(sourceType).toSet()
        val cleanEntriesByItemId = if (preserveReviewFieldsWhenIncomingBlank) {
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
                entry.copy(
                    rating = if (preserveRatingWhenIncomingBlank) entry.rating else null,
                    updatedAtEpochMillis = entry.reviewedAtEpochMillis ?: entry.updatedAtEpochMillis,
                )
            }
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
        deleteCleanJournalEntriesForSource(sourceType)
        insertReadingEvents(events.filterNot { event -> event.id in protectedEventIds })
        insertJournalEntries(mergedEntries + reviewOnlyEntries)
    }
}

private fun ItemJournalEntryEntity.hasReviewCache(): Boolean =
    reviewText.isNotBlank() || remoteEntryId != null || remoteReviewNoteId != null
