package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RemoteCatalogDao {
    @Query("SELECT * FROM servers ORDER BY displayName COLLATE NOCASE ASC")
    abstract fun getServers(): List<ServerEntity>

    @Query("SELECT * FROM servers ORDER BY displayName COLLATE NOCASE ASC")
    abstract fun observeServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM libraries ORDER BY title COLLATE NOCASE ASC")
    abstract fun getLibraries(): List<LibraryEntity>

    @Query("SELECT * FROM libraries ORDER BY title COLLATE NOCASE ASC")
    abstract fun observeLibraries(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM readable_items ORDER BY title COLLATE NOCASE ASC")
    abstract fun getItems(): List<ReadableItemEntity>

    @Query("SELECT * FROM readable_items ORDER BY title COLLATE NOCASE ASC")
    abstract fun observeItems(): Flow<List<ReadableItemEntity>>

    @Query("SELECT * FROM readable_items WHERE sourceType = :sourceType ORDER BY title COLLATE NOCASE ASC")
    abstract fun getItemsForSourceType(sourceType: String): List<ReadableItemEntity>

    @Query("SELECT * FROM readable_items WHERE serverId = :serverId ORDER BY title COLLATE NOCASE ASC")
    abstract fun getItemsForServer(serverId: String): List<ReadableItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceServer(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceLibraries(libraries: List<LibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceItems(items: List<ReadableItemEntity>)

    @Query("DELETE FROM readable_items WHERE serverId = :serverId")
    protected abstract fun deleteItemsForServer(serverId: String)

    @Query("DELETE FROM readable_items WHERE sourceType = :sourceType")
    protected abstract fun deleteItemsForSourceType(sourceType: String)

    @Query("DELETE FROM libraries WHERE serverId = :serverId")
    protected abstract fun deleteLibrariesForServer(serverId: String)

    @Query("DELETE FROM libraries WHERE sourceType = :sourceType")
    protected abstract fun deleteLibrariesForSourceType(sourceType: String)

    @Query("DELETE FROM servers WHERE sourceType = :sourceType")
    protected abstract fun deleteServersForSourceType(sourceType: String)

    @Transaction
    open fun replaceServerCatalog(
        server: ServerEntity,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) {
        if (server.sourceType == "GRIMMORY") {
            deleteItemsForSourceType(server.sourceType)
            deleteLibrariesForSourceType(server.sourceType)
            deleteServersForSourceType(server.sourceType)
        }
        insertOrReplaceServer(server)
        deleteItemsForServer(server.id)
        deleteLibrariesForServer(server.id)
        insertOrReplaceLibraries(libraries)
        insertOrReplaceItems(items)
    }
}
