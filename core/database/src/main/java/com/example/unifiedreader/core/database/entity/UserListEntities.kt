package com.example.unifiedreader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A list the user keeps on this device: Want to read (built in) or one they made. Its books are
 * [UserListEntryEntity] rows. [kind] and [orderMode] hold enum names from `feature:lists`;
 * [lastOpenedAtEpochMillis] is the last time a book was opened from the list (0: never), which
 * makes it the reading order under way.
 */
@Entity(tableName = "user_lists")
data class UserListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val kind: String,
    val orderMode: String,
    val hideFinished: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long,
)

/**
 * A book in a [UserListEntity], from any source. [position] is its place in the list's own order
 * (0 first). The display fields are a copy taken when it was added, as the journal keeps them, so
 * the row still shows if the book leaves the library.
 */
@Entity(
    tableName = "user_list_entries",
    primaryKeys = ["listId", "itemId"],
    indices = [Index(value = ["itemId"])],
)
data class UserListEntryEntity(
    val listId: String,
    val itemId: String,
    val position: Int,
    val addedAtEpochMillis: Long,
    val sourceType: String,
    val libraryId: String,
    val itemTitle: String,
    val itemPrimaryText: String?,
    val itemSecondaryText: String?,
    val coverUrl: String?,
    val itemKind: String,
    val format: String,
)
