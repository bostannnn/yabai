package com.example.unifiedreader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A list kept on a server (a Grimmory shelf or magic shelf, a Kavita reading list or collection), as the app last read it, so it still shows offline.
 * The server's fields ([name], [icon], [iconType], [isPublic], [isOwned], [remoteSortField],
 * [remoteItemCount]) are rewritten at every refresh; [orderMode] and [hideFinished] are chosen on
 * this device and kept across refreshes. [kind] and [orderMode] hold enum names from
 * `feature:lists`. Its books are [ServerListItemEntity] rows.
 */
@Entity(tableName = "server_lists")
data class ServerListEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val kind: String,
    val remoteId: String,
    val name: String,
    val icon: String?,
    val iconType: String?,
    val isPublic: Boolean,
    /** False for someone else's public shelf, which this user can read but not change. */
    val isOwned: Boolean,
    val remoteSortField: String?,
    val remoteItemCount: Int,
    val orderMode: String,
    val hideFinished: Boolean,
    val syncedAtEpochMillis: Long,
    /** A magic shelf's rules as its server wrote them (Grimmory's JSON); null for every other list. */
    val rules: String? = null,
)

/** A book in a [ServerListEntity], by the app's item id; [position] is the order the server gave. */
@Entity(
    tableName = "server_list_items",
    primaryKeys = ["listId", "itemId"],
    indices = [Index(value = ["itemId"])],
)
data class ServerListItemEntity(
    val listId: String,
    val itemId: String,
    val position: Int,
)
