package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.UserListEntity
import com.example.unifiedreader.core.database.entity.UserListEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserListDao {
    @Query("SELECT * FROM user_lists")
    abstract fun observeLists(): Flow<List<UserListEntity>>

    @Query("SELECT * FROM user_list_entries ORDER BY listId, position")
    abstract fun observeEntries(): Flow<List<UserListEntryEntity>>

    @Query("SELECT * FROM user_lists WHERE id = :listId")
    abstract fun getList(listId: String): UserListEntity?

    @Query("SELECT * FROM user_list_entries WHERE listId = :listId ORDER BY position")
    abstract fun getEntries(listId: String): List<UserListEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertList(list: UserListEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertListIfMissing(list: UserListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertEntries(entries: List<UserListEntryEntity>)

    @Query("DELETE FROM user_list_entries WHERE listId = :listId AND itemId IN (:itemIds)")
    abstract fun deleteEntries(listId: String, itemIds: List<String>)

    @Query("DELETE FROM user_list_entries WHERE listId = :listId")
    abstract fun deleteAllEntries(listId: String)

    @Query("DELETE FROM user_lists WHERE id = :listId")
    abstract fun deleteListRow(listId: String)

    @Query("UPDATE user_lists SET lastOpenedAtEpochMillis = :atEpochMillis WHERE id = :listId")
    abstract fun markOpened(listId: String, atEpochMillis: Long)

    /** A list and its books go together. */
    @Transaction
    open fun deleteList(listId: String) {
        deleteAllEntries(listId)
        deleteListRow(listId)
    }

    /** Rewrites the list's own order: [orderedItemIds] first to last; books not named keep theirs after them. */
    @Transaction
    open fun reorder(listId: String, orderedItemIds: List<String>, atEpochMillis: Long) {
        val entries = getEntries(listId)
        val byId = entries.associateBy(UserListEntryEntity::itemId)
        val named = orderedItemIds.mapNotNull(byId::get)
        val rest = entries.filter { it.itemId !in orderedItemIds.toSet() }
        upsertEntries((named + rest).mapIndexed { index, entry -> entry.copy(position = index) })
        getList(listId)?.let { upsertList(it.copy(updatedAtEpochMillis = atEpochMillis)) }
    }

    /** Adds books at the end of the list; books already in it stay where they are. */
    @Transaction
    open fun addEntries(listId: String, entries: List<UserListEntryEntity>, atEpochMillis: Long) {
        val existing = getEntries(listId)
        val existingIds = existing.mapTo(HashSet(), UserListEntryEntity::itemId)
        var next = (existing.maxOfOrNull(UserListEntryEntity::position) ?: -1) + 1
        val added = entries
            .filter { it.itemId !in existingIds }
            .distinctBy(UserListEntryEntity::itemId)
            .map { entry -> entry.copy(listId = listId, position = next++) }
        if (added.isEmpty()) return
        upsertEntries(added)
        getList(listId)?.let { upsertList(it.copy(updatedAtEpochMillis = atEpochMillis)) }
    }
}
