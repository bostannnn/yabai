package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
import com.example.unifiedreader.core.database.entity.ReadingGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class JournalDao {
    @Query("SELECT * FROM journal_reading_events ORDER BY occurredAtEpochMillis DESC")
    abstract fun observeReadingEvents(): Flow<List<JournalReadingEventEntity>>

    @Query("SELECT * FROM journal_reading_events ORDER BY occurredAtEpochMillis DESC")
    abstract fun getReadingEvents(): List<JournalReadingEventEntity>

    @Query("SELECT * FROM journal_reading_events WHERE itemId = :itemId ORDER BY occurredAtEpochMillis DESC")
    abstract fun getReadingEventsForItem(itemId: String): List<JournalReadingEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertReadingEvent(event: JournalReadingEventEntity)

    @Query("DELETE FROM journal_reading_events WHERE id = :eventId")
    abstract fun deleteReadingEvent(eventId: String): Int

    @Query("DELETE FROM journal_reading_events WHERE itemId = :itemId")
    abstract fun deleteReadingEventsForItem(itemId: String): Int

    @Query("SELECT * FROM item_journal_entries ORDER BY updatedAtEpochMillis DESC")
    abstract fun observeJournalEntries(): Flow<List<ItemJournalEntryEntity>>

    @Query("SELECT * FROM item_journal_entries WHERE itemId = :itemId")
    abstract fun getJournalEntry(itemId: String): ItemJournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertJournalEntry(entry: ItemJournalEntryEntity)

    @Query("DELETE FROM item_journal_entries WHERE itemId = :itemId")
    abstract fun deleteJournalEntry(itemId: String): Int

    @Query("SELECT * FROM reading_goals ORDER BY periodStartEpochMillis DESC, metric ASC")
    abstract fun observeGoals(): Flow<List<ReadingGoalEntity>>

    @Query("SELECT * FROM reading_goals ORDER BY periodStartEpochMillis DESC, metric ASC")
    abstract fun getGoals(): List<ReadingGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertGoal(goal: ReadingGoalEntity)

    @Query("DELETE FROM reading_goals WHERE id = :goalId")
    abstract fun deleteGoal(goalId: String): Int
}
