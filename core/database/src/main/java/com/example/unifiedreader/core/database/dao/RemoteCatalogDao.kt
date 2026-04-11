package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RemoteCatalogDao {
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

    @Query(
        """
        SELECT * FROM remote_library_cache_entries
        WHERE sourceType = :sourceType AND baseUrl = :baseUrl AND username = :username
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    abstract fun getCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
    ): List<RemoteLibraryCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceLibraries(libraries: List<LibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceItems(items: List<ReadableItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceCachedLibraries(entries: List<RemoteLibraryCacheEntity>)

    @Query("DELETE FROM readable_items WHERE sourceType = :sourceType")
    protected abstract fun deleteItemsForSourceType(sourceType: String)

    @Query("DELETE FROM libraries WHERE sourceType = :sourceType")
    protected abstract fun deleteLibrariesForSourceType(sourceType: String)

    @Query(
        """
        DELETE FROM remote_library_cache_entries
        WHERE sourceType = :sourceType AND baseUrl = :baseUrl AND username = :username
        """,
    )
    protected abstract fun deleteCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
    )

    @Transaction
    open fun replaceSourceCatalog(
        sourceType: String,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) {
        deleteItemsForSourceType(sourceType)
        deleteLibrariesForSourceType(sourceType)
        insertOrReplaceLibraries(libraries)
        insertOrReplaceItems(items)
    }

    @Transaction
    open fun replaceCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
        entries: List<RemoteLibraryCacheEntity>,
    ) {
        deleteCachedLibraries(
            sourceType = sourceType,
            baseUrl = baseUrl,
            username = username,
        )
        if (entries.isNotEmpty()) {
            insertOrReplaceCachedLibraries(entries)
        }
    }
}
