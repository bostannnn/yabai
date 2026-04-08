package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceLibraries(libraries: List<LibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceItems(items: List<ReadableItemEntity>)

    @Query("DELETE FROM readable_items WHERE sourceType = :sourceType")
    protected abstract fun deleteItemsForSourceType(sourceType: String)

    @Query("DELETE FROM libraries WHERE sourceType = :sourceType")
    protected abstract fun deleteLibrariesForSourceType(sourceType: String)

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
}
