package com.example.unifiedreader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A list the user keeps on this device: Want to read (built in), one they made, or a smart list.
 * Its books are [UserListEntryEntity] rows; a smart list has none and holds what [smartFilter]
 * picks out. [kind] and [orderMode] hold enum names from `feature:lists`;
 * [lastOpenedAtEpochMillis] is the last time a book was opened from the list (0: never), which
 * makes it the reading order under way. [keepNextDownloaded] keeps its next three books downloaded.
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
    @ColumnInfo(defaultValue = "0") val keepNextDownloaded: Boolean = false,
    /** A smart list's saved filter, as `feature:lists` writes it; null for every other list. */
    val smartFilter: String? = null,
)

/**
 * A list read to the end, as the journal keeps it: it stays when the list is deleted. [itemIds]
 * are its books in the list's order, one per line, for the row of covers.
 */
@Entity(tableName = "finished_lists")
data class FinishedListEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val bookCount: Int,
    val seriesCount: Int,
    val readMillis: Long,
    val itemIds: String,
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
