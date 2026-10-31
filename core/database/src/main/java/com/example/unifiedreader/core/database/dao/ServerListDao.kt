package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.ServerListEntity
import com.example.unifiedreader.core.database.entity.ServerListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ServerListDao {
    @Query("SELECT * FROM server_lists")
    abstract fun observeLists(): Flow<List<ServerListEntity>>

    @Query("SELECT * FROM server_list_items ORDER BY listId, position")
    abstract fun observeItems(): Flow<List<ServerListItemEntity>>

    @Query("SELECT * FROM server_lists WHERE id = :listId")
    abstract fun getList(listId: String): ServerListEntity?

    @Query("SELECT * FROM server_lists WHERE sourceType = :sourceType")
    abstract fun getLists(sourceType: String): List<ServerListEntity>

    @Query("SELECT * FROM server_lists WHERE sourceType = :sourceType AND kind = :kind")
    abstract fun getLists(sourceType: String, kind: String): List<ServerListEntity>

    @Query("SELECT * FROM server_list_items WHERE listId = :listId ORDER BY position")
    abstract fun getItems(listId: String): List<ServerListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertList(list: ServerListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertLists(lists: List<ServerListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertItems(items: List<ServerListItemEntity>)

    @Query("DELETE FROM server_list_items WHERE listId = :listId AND itemId IN (:itemIds)")
    abstract fun deleteItems(listId: String, itemIds: List<String>)

    @Query("DELETE FROM server_list_items WHERE listId = :listId")
    abstract fun deleteAllItems(listId: String)

    @Query("DELETE FROM server_list_items WHERE listId IN (SELECT id FROM server_lists WHERE sourceType = :sourceType)")
    abstract fun deleteItemsOfSource(sourceType: String)

    @Query("DELETE FROM server_lists WHERE sourceType = :sourceType")
    abstract fun deleteListsOfSource(sourceType: String)

    @Query("DELETE FROM server_list_items WHERE listId IN (SELECT id FROM server_lists WHERE sourceType = :sourceType AND kind = :kind)")
    abstract fun deleteItemsOfKind(sourceType: String, kind: String)

    @Query("DELETE FROM server_lists WHERE sourceType = :sourceType AND kind = :kind")
    abstract fun deleteListsOfKind(sourceType: String, kind: String)

    @Query("DELETE FROM server_lists WHERE id = :listId")
    abstract fun deleteListRow(listId: String)

    /** A list and its books go together. */
    @Transaction
    open fun deleteList(listId: String) {
        deleteAllItems(listId)
        deleteListRow(listId)
    }

    /** What a refresh read from the server replaces everything kept for [sourceType], in one go. */
    @Transaction
    open fun replaceSource(
        sourceType: String,
        lists: List<ServerListEntity>,
        items: List<ServerListItemEntity>,
    ) {
        deleteItemsOfSource(sourceType)
        deleteListsOfSource(sourceType)
        upsertLists(lists)
        upsertItems(items)
    }

    /**
     * What a refresh read from the server replaces everything kept for [sourceType]'s lists of
     * [kind] (its shelves, say, leaving its magic shelves), in one go.
     */
    @Transaction
    open fun replaceKind(
        sourceType: String,
        kind: String,
        lists: List<ServerListEntity>,
        items: List<ServerListItemEntity>,
    ) {
        deleteItemsOfKind(sourceType, kind)
        deleteListsOfKind(sourceType, kind)
        upsertLists(lists)
        upsertItems(items)
    }

    /** One list's books, replaced by [itemIds] in that order. */
    @Transaction
    open fun replaceItems(listId: String, itemIds: List<String>) {
        deleteAllItems(listId)
        upsertItems(itemIds.mapIndexed { index, itemId -> ServerListItemEntity(listId, itemId, index) })
    }

    /** Adds books after the ones already there; a book already in the list stays where it is. */
    @Transaction
    open fun addItems(listId: String, itemIds: List<String>) {
        val existing = getItems(listId)
        val existingIds = existing.mapTo(HashSet(), ServerListItemEntity::itemId)
        var next = (existing.maxOfOrNull(ServerListItemEntity::position) ?: -1) + 1
        val added = itemIds.distinct().filter { it !in existingIds }.map { ServerListItemEntity(listId, it, next++) }
        if (added.isNotEmpty()) upsertItems(added)
    }
}
