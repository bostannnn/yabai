package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.ListPreferenceEntity
import com.example.unifiedreader.core.database.entity.ListSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ListPreferenceDao {
    @Query("SELECT * FROM list_preferences")
    abstract fun observePreferences(): Flow<List<ListPreferenceEntity>>

    @Query("SELECT * FROM list_settings")
    abstract fun observeSettings(): Flow<List<ListSettingEntity>>

    @Query("SELECT * FROM list_preferences WHERE listId IN (:listIds)")
    abstract fun getPreferences(listIds: List<String>): List<ListPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertPreferences(preferences: List<ListPreferenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertSetting(setting: ListSettingEntity)

    /**
     * Pins [listIds] in that order, from [atEpochMillis] a millisecond apart (a list pinned already
     * keeps its place), and shows them again; or, [pinned] false, unpins them.
     */
    @Transaction
    open fun setPinned(listIds: List<String>, pinned: Boolean, atEpochMillis: Long) {
        val order = listIds.withIndex().associate { (index, id) -> id to atEpochMillis + index }
        change(listIds) { row ->
            if (pinned) row.copy(pinnedAtEpochMillis = row.pinnedAtEpochMillis ?: order.getValue(row.listId), hidden = false)
            else row.copy(pinnedAtEpochMillis = null)
        }
    }

    /** Hides [listIds] (a hidden list is no longer pinned), or shows them again. */
    @Transaction
    open fun setHidden(listIds: List<String>, hidden: Boolean) {
        change(listIds) { row -> if (hidden) row.copy(hidden = true, pinnedAtEpochMillis = null) else row.copy(hidden = false) }
    }

    @Transaction
    open fun markOpened(listId: String, atEpochMillis: Long) {
        change(listOf(listId)) { row -> row.copy(openedAtEpochMillis = atEpochMillis) }
    }

    /** Changes each of [listIds]' preferences, starting from none for a list that has none yet. */
    private fun change(listIds: List<String>, change: (ListPreferenceEntity) -> ListPreferenceEntity) {
        val kept = getPreferences(listIds).associateBy(ListPreferenceEntity::listId)
        upsertPreferences(
            listIds.distinct().map { listId ->
                change(kept[listId] ?: ListPreferenceEntity(listId, pinnedAtEpochMillis = null, hidden = false, openedAtEpochMillis = 0L))
            },
        )
    }
}
