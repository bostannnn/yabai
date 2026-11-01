package com.example.unifiedreader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What this device keeps about any list, the user's own or a server's, by its id: whether it is
 * pinned to the top of Lists ([pinnedAtEpochMillis], null when not, so pinned lists keep the
 * order they were pinned in), whether it is hidden from Lists, and when its page was last
 * opened (0: never). None of it reaches a server.
 */
@Entity(tableName = "list_preferences")
data class ListPreferenceEntity(
    @PrimaryKey val listId: String,
    val pinnedAtEpochMillis: Long?,
    val hidden: Boolean,
    val openedAtEpochMillis: Long,
)

/**
 * One of the Lists screen's choices on this device, by name: a place's kind, sort and Show as,
 * and what What Lists shows leaves out. `feature:lists` writes and reads the keys and values.
 */
@Entity(tableName = "list_settings")
data class ListSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
