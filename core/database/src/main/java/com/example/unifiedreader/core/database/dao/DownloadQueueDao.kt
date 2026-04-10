package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.unifiedreader.core.database.entity.DownloadQueueEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DownloadQueueDao {
    @Query("SELECT * FROM download_queue_entries ORDER BY createdAtEpochMillis DESC")
    abstract fun observeAll(): Flow<List<DownloadQueueEntryEntity>>

    @Query("SELECT * FROM download_queue_entries ORDER BY createdAtEpochMillis DESC")
    abstract fun getAll(): List<DownloadQueueEntryEntity>

    @Query("SELECT * FROM download_queue_entries WHERE id = :entryId")
    abstract fun getById(entryId: String): DownloadQueueEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplace(entry: DownloadQueueEntryEntity)

    @Query(
        """
        UPDATE download_queue_entries
        SET status = :status,
            currentItemId = :currentItemId,
            currentItemTitle = :currentItemTitle,
            errorMessage = :errorMessage,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            completedAtEpochMillis = :completedAtEpochMillis
        WHERE id = :entryId
        """,
    )
    abstract fun updateStatus(
        entryId: String,
        status: String,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        updatedAtEpochMillis: Long,
        completedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE download_queue_entries
        SET status = :status,
            currentItemId = :currentItemId,
            currentItemTitle = :currentItemTitle,
            errorMessage = :errorMessage,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            completedAtEpochMillis = :completedAtEpochMillis
        WHERE id = :entryId AND status IN (:expectedStatuses)
        """,
    )
    abstract fun updateStatusIfCurrentStatusIn(
        entryId: String,
        expectedStatuses: List<String>,
        status: String,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        updatedAtEpochMillis: Long,
        completedAtEpochMillis: Long?,
    ): Int

    @Query(
        """
        UPDATE download_queue_entries
        SET completedItemIdsEncoded = :completedItemIdsEncoded,
            currentItemId = :currentItemId,
            currentItemTitle = :currentItemTitle,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :entryId
        """,
    )
    abstract fun updateProgress(
        entryId: String,
        completedItemIdsEncoded: String,
        currentItemId: String?,
        currentItemTitle: String?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM download_queue_entries WHERE status = :status")
    abstract fun deleteByStatus(status: String): Int

    @Query("DELETE FROM download_queue_entries WHERE id = :entryId")
    abstract fun deleteById(entryId: String): Int
}
