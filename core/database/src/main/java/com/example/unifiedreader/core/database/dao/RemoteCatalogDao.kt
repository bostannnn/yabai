package com.example.unifiedreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadableItemLibraryEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import kotlinx.coroutines.flow.Flow

private const val READABLE_ITEM_LIBRARY_SELECT = """
    SELECT
        id,
        sourceType,
        libraryId,
        remoteId,
        isLocalImport,
        remoteFileId,
        remoteFileType,
        title,
        format,
        kind,
        pageCount,
        coverUrl,
        seriesCoverUrl,
        CASE
            WHEN description IS NULL THEN NULL
            ELSE substr(description, 1, 512)
        END AS description,
        addedAtEpochMillis,
        bookAuthor,
        bookAuthorsJson,
        bookSeriesName,
        bookPublishersJson,
        bookGenresJson,
        bookTagsJson,
        bookLanguagesJson,
        comicSeries,
        comicIssueTitle,
        comicVolume,
        comicIssueNumber,
        comicWritersJson,
        comicPencillersJson,
        comicPublishersJson,
        comicGenresJson,
        comicTagsJson,
        comicLanguagesJson
    FROM readable_items
"""
private const val SQLITE_BIND_SAFE_CHUNK_SIZE = 900

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

    @Query(READABLE_ITEM_LIBRARY_SELECT + " ORDER BY title COLLATE NOCASE ASC")
    abstract fun getLibraryItems(): List<ReadableItemLibraryEntity>

    @Query(READABLE_ITEM_LIBRARY_SELECT + " ORDER BY title COLLATE NOCASE ASC")
    abstract fun observeLibraryItems(): Flow<List<ReadableItemLibraryEntity>>

    @Query(READABLE_ITEM_LIBRARY_SELECT + " WHERE sourceType = :sourceType ORDER BY title COLLATE NOCASE ASC")
    abstract fun getLibraryItemsForSourceType(sourceType: String): List<ReadableItemLibraryEntity>

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

    @Query(
        """
        SELECT * FROM remote_catalog_sync_groups
        WHERE sourceType = :sourceType
        """,
    )
    abstract fun getCatalogSyncGroupsForSourceType(
        sourceType: String,
    ): List<RemoteCatalogSyncGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceLibraries(libraries: List<LibraryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceItems(items: List<ReadableItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceCatalogSyncGroups(groups: List<RemoteCatalogSyncGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceCachedLibraries(entries: List<RemoteLibraryCacheEntity>)

    @Query("DELETE FROM readable_items WHERE sourceType = :sourceType")
    protected abstract fun deleteItemsForSourceType(sourceType: String)

    @Query("DELETE FROM readable_items WHERE sourceType = :sourceType AND id IN (:itemIds)")
    protected abstract fun deleteItemsForSourceTypeByIds(
        sourceType: String,
        itemIds: List<String>,
    )

    @Query("DELETE FROM libraries WHERE sourceType = :sourceType")
    protected abstract fun deleteLibrariesForSourceType(sourceType: String)

    @Query("DELETE FROM remote_catalog_sync_groups WHERE sourceType = :sourceType")
    protected abstract fun deleteCatalogSyncGroupsForSourceType(sourceType: String)

    @Query("DELETE FROM remote_catalog_sync_groups WHERE sourceType = :sourceType AND groupId IN (:groupIds)")
    protected abstract fun deleteCatalogSyncGroupsForSourceTypeByIds(
        sourceType: String,
        groupIds: List<String>,
    )

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
        syncGroups: List<RemoteCatalogSyncGroupEntity> = emptyList(),
    ) {
        deleteItemsForSourceType(sourceType)
        deleteLibrariesForSourceType(sourceType)
        deleteCatalogSyncGroupsForSourceType(sourceType)
        insertOrReplaceLibraries(libraries)
        insertOrReplaceItems(items)
        if (syncGroups.isNotEmpty()) {
            insertOrReplaceCatalogSyncGroups(syncGroups)
        }
    }

    @Transaction
    open fun applySourceCatalogDelta(
        sourceType: String,
        libraries: List<LibraryEntity>,
        upsertItems: List<ReadableItemEntity>,
        removeItemIds: List<String> = emptyList(),
        syncGroups: List<RemoteCatalogSyncGroupEntity> = emptyList(),
        removeSyncGroupIds: List<String> = emptyList(),
    ) {
        deleteLibrariesForSourceType(sourceType)
        insertOrReplaceLibraries(libraries)
        if (removeItemIds.isNotEmpty()) {
            removeItemIds.chunked(SQLITE_BIND_SAFE_CHUNK_SIZE).forEach { chunk ->
                deleteItemsForSourceTypeByIds(sourceType, chunk)
            }
        }
        if (upsertItems.isNotEmpty()) {
            insertOrReplaceItems(upsertItems)
        }
        if (removeSyncGroupIds.isNotEmpty()) {
            removeSyncGroupIds.chunked(SQLITE_BIND_SAFE_CHUNK_SIZE).forEach { chunk ->
                deleteCatalogSyncGroupsForSourceTypeByIds(sourceType, chunk)
            }
        }
        if (syncGroups.isNotEmpty()) {
            insertOrReplaceCatalogSyncGroups(syncGroups)
        }
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
