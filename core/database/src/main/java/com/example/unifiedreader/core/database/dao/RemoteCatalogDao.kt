package com.example.unifiedreader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.LibraryGridRootEntity
import com.example.unifiedreader.core.database.entity.LibraryMemberBadgeProjection
import com.example.unifiedreader.core.database.entity.LibraryRootFacetEntity
import com.example.unifiedreader.core.database.entity.LibraryRootMemberEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ReadableItemLibraryEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.database.entity.toGridRootEntity
import com.example.unifiedreader.core.database.entity.toMemberEntities
import com.example.unifiedreader.core.database.entity.toRootFacetEntities
import com.example.unifiedreader.core.model.LibraryAlphabetBucket
import com.example.unifiedreader.core.model.LibraryGridRoot
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.deriveGridRootsWithMembers
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

    @Query(READABLE_ITEM_LIBRARY_SELECT + " WHERE id IN (:itemIds)")
    abstract fun getLibraryItemsByIds(itemIds: List<String>): List<ReadableItemLibraryEntity>

    @Query(READABLE_ITEM_LIBRARY_SELECT + " WHERE id = :itemId LIMIT 1")
    abstract fun getLibraryItemById(itemId: String): ReadableItemLibraryEntity?

    /**
     * The bounded item set the library shelves need for [sourceType]: every catalog item that shares a
     * grid root (series container or standalone) with an item that has reading progress. This is exactly
     * what the in-memory shelves computer keeps from the full catalog — Continue Reading / Recently
     * Finished need the progressed items, and Next-In-Series needs each active series' full ordered issue
     * list to find the next unstarted one — so the computer can run over this instead of scanning all of
     * [getLibraryItems]. Bounded by (progressed items x series size), not the catalog size.
     */
    @Query(
        READABLE_ITEM_LIBRARY_SELECT + """
         WHERE sourceType = :sourceType AND id IN (
             SELECT m.itemId FROM library_root_members m
             WHERE m.sourceType = :sourceType AND m.rootId IN (
                 SELECT DISTINCT active.rootId FROM library_root_members active
                 INNER JOIN reading_progress p ON p.itemId = active.itemId
                 WHERE active.sourceType = :sourceType
             )
         )
        """,
    )
    abstract fun getShelvesInputItems(sourceType: String): List<ReadableItemLibraryEntity>

    /**
     * The catalog items that make up [rootIds] — a bounded set of grid roots (the visible paged
     * window): each container root's issues and each standalone root's own item, via
     * `library_root_members`. Bounded by (window size x series size), so the paged card badges
     * (progress / rating / download rollups) can be resolved for just the visible roots instead of
     * scanning the full catalog. Selects only the columns those rollups need
     * ([LibraryMemberBadgeProjection]) — not the wide `readable_items` projection — so a window's
     * members load cheaply on the cold-start path.
     */
    @Query(
        """
        SELECT id, sourceType, libraryId, remoteId, remoteFileId, remoteFileType, comicSeries, title, format, kind, pageCount
        FROM readable_items
        WHERE sourceType = :sourceType AND id IN (
            SELECT m.itemId FROM library_root_members m
            WHERE m.sourceType = :sourceType AND m.rootId IN (:rootIds)
        )
        """,
    )
    abstract fun getMemberBadgeProjectionsForRoots(
        sourceType: String,
        rootIds: List<String>,
    ): List<LibraryMemberBadgeProjection>

    @Query("SELECT * FROM library_grid_roots WHERE sourceType = :sourceType ORDER BY title COLLATE NOCASE ASC")
    abstract fun getGridRootsForSourceType(sourceType: String): List<LibraryGridRootEntity>

    // The three alphabet-rail sorts page by their precomputed case-folded key (see
    // ReadableItem.librarySortIndexKey), with the same `title` tie-break as the in-memory comparator.
    @Query(
        "SELECT * FROM library_grid_roots WHERE sourceType = :sourceType " +
            "ORDER BY sortTitleKey ASC, title COLLATE NOCASE ASC",
    )
    abstract fun pagingGridRootsByTitle(sourceType: String): PagingSource<Int, LibraryGridRootEntity>

    @Query(
        "SELECT * FROM library_grid_roots WHERE sourceType = :sourceType " +
            "ORDER BY sortAuthorKey ASC, title COLLATE NOCASE ASC",
    )
    abstract fun pagingGridRootsByAuthor(sourceType: String): PagingSource<Int, LibraryGridRootEntity>

    @Query(
        "SELECT * FROM library_grid_roots WHERE sourceType = :sourceType " +
            "ORDER BY sortSeriesKey ASC, title COLLATE NOCASE ASC",
    )
    abstract fun pagingGridRootsBySeries(sourceType: String): PagingSource<Int, LibraryGridRootEntity>

    // Alphabet-rail buckets: how many roots share each first character of the sort key, in sort order.
    // assembleLibraryAlphabetSections turns these into per-letter scroll offsets (bounded to <=~40 rows).
    @Query(
        "SELECT substr(sortTitleKey, 1, 1) AS firstChar, COUNT(*) AS count FROM library_grid_roots " +
            "WHERE sourceType = :sourceType AND sortTitleKey IS NOT NULL " +
            "GROUP BY firstChar ORDER BY MIN(sortTitleKey) ASC",
    )
    abstract fun getTitleAlphabetBuckets(sourceType: String): List<LibraryAlphabetBucket>

    @Query(
        "SELECT substr(sortAuthorKey, 1, 1) AS firstChar, COUNT(*) AS count FROM library_grid_roots " +
            "WHERE sourceType = :sourceType AND sortAuthorKey IS NOT NULL " +
            "GROUP BY firstChar ORDER BY MIN(sortAuthorKey) ASC",
    )
    abstract fun getAuthorAlphabetBuckets(sourceType: String): List<LibraryAlphabetBucket>

    @Query(
        "SELECT substr(sortSeriesKey, 1, 1) AS firstChar, COUNT(*) AS count FROM library_grid_roots " +
            "WHERE sourceType = :sourceType AND sortSeriesKey IS NOT NULL " +
            "GROUP BY firstChar ORDER BY MIN(sortSeriesKey) ASC",
    )
    abstract fun getSeriesAlphabetBuckets(sourceType: String): List<LibraryAlphabetBucket>

    @Query(
        "SELECT * FROM library_grid_roots WHERE sourceType = :sourceType " +
            "ORDER BY addedAtEpochMillis DESC, title COLLATE NOCASE ASC",
    )
    abstract fun pagingGridRootsByDateAdded(sourceType: String): PagingSource<Int, LibraryGridRootEntity>

    @Query(
        """
        SELECT r.* FROM library_grid_roots r
        LEFT JOIN (
            SELECT m.rootId AS rootId, MAX(p.updatedAtEpochMillis) AS lastReadAt
            FROM library_root_members m
            INNER JOIN reading_progress p ON p.itemId = m.itemId
            WHERE p.isExplicitUnreadReset = 0
            GROUP BY m.rootId
        ) lr ON lr.rootId = r.id
        WHERE r.sourceType = :sourceType
        ORDER BY lr.lastReadAt IS NULL, lr.lastReadAt DESC, r.title COLLATE NOCASE ASC
        """,
    )
    abstract fun pagingGridRootsByLastRead(sourceType: String): PagingSource<Int, LibraryGridRootEntity>

    /**
     * Pages grid roots restricted to a content filter (built by [buildFilteredGridRootsQuery] — one
     * `EXISTS` over `library_root_facets` per active dimension), so a filtered library view pages from
     * SQL instead of scanning the in-memory catalog. Observes every table the query can read so Room
     * invalidates the page window when the catalog, facets, or progress change.
     */
    @RawQuery(
        observedEntities = [
            LibraryGridRootEntity::class,
            LibraryRootFacetEntity::class,
            LibraryRootMemberEntity::class,
            ReadingProgressEntity::class,
        ],
    )
    abstract fun pagingGridRootsFiltered(query: SupportSQLiteQuery): PagingSource<Int, LibraryGridRootEntity>

    // Bounded, non-paged siblings of the paging queries above: just the ordered root ids for the
    // first [limit] roots, in the same order the matching paging source would yield. Used to record
    // the paged view's order for cold-start restore without materializing the in-memory grid.
    @Query(
        "SELECT id FROM library_grid_roots WHERE sourceType = :sourceType " +
            "ORDER BY title COLLATE NOCASE ASC LIMIT :limit",
    )
    abstract fun getGridRootIdsByTitle(sourceType: String, limit: Int): List<String>

    @Query(
        "SELECT id FROM library_grid_roots WHERE sourceType = :sourceType " +
            "ORDER BY addedAtEpochMillis DESC, title COLLATE NOCASE ASC LIMIT :limit",
    )
    abstract fun getGridRootIdsByDateAdded(sourceType: String, limit: Int): List<String>

    @Query(
        """
        SELECT r.id FROM library_grid_roots r
        LEFT JOIN (
            SELECT m.rootId AS rootId, MAX(p.updatedAtEpochMillis) AS lastReadAt
            FROM library_root_members m
            INNER JOIN reading_progress p ON p.itemId = m.itemId
            WHERE p.isExplicitUnreadReset = 0
            GROUP BY m.rootId
        ) lr ON lr.rootId = r.id
        WHERE r.sourceType = :sourceType
        ORDER BY lr.lastReadAt IS NULL, lr.lastReadAt DESC, r.title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    abstract fun getGridRootIdsByLastRead(sourceType: String, limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM library_grid_roots")
    abstract fun countGridRoots(): Int

    @Query(READABLE_ITEM_LIBRARY_SELECT + " WHERE libraryId = :libraryId ORDER BY title COLLATE NOCASE ASC")
    protected abstract fun getLibraryItemsForLibrary(libraryId: String): List<ReadableItemLibraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceGridRoots(roots: List<LibraryGridRootEntity>)

    @Query("DELETE FROM library_grid_roots WHERE sourceType = :sourceType")
    protected abstract fun deleteGridRootsForSourceType(sourceType: String)

    @Query("DELETE FROM library_grid_roots WHERE libraryId = :libraryId")
    protected abstract fun deleteGridRootsForLibrary(libraryId: String)

    @Query(
        "SELECT DISTINCT value FROM library_root_facets " +
            "WHERE sourceType = :sourceType AND dimension = :dimension",
    )
    abstract fun getDistinctFacetValues(sourceType: String, dimension: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceRootFacets(facets: List<LibraryRootFacetEntity>)

    @Query("DELETE FROM library_root_facets WHERE sourceType = :sourceType")
    protected abstract fun deleteRootFacetsForSourceType(sourceType: String)

    @Query("DELETE FROM library_root_facets WHERE libraryId = :libraryId")
    protected abstract fun deleteRootFacetsForLibrary(libraryId: String)

    @Query("SELECT itemId FROM library_root_members WHERE rootId = :rootId")
    abstract fun getMemberItemIds(rootId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplaceRootMembers(members: List<LibraryRootMemberEntity>)

    @Query("DELETE FROM library_root_members WHERE sourceType = :sourceType")
    protected abstract fun deleteRootMembersForSourceType(sourceType: String)

    @Query("DELETE FROM library_root_members WHERE libraryId = :libraryId")
    protected abstract fun deleteRootMembersForLibrary(libraryId: String)

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

    /**
     * Recompute the persisted grid roots for [sourceType] from the current readable_items, using
     * the shared core:model derivation. Runs inside the catalog write transaction so roots stay
     * coherent with the items they summarize.
     */
    @Transaction
    open fun rebuildGridRootsForSourceType(sourceType: String) {
        deleteGridRootsForSourceType(sourceType)
        deleteRootFacetsForSourceType(sourceType)
        deleteRootMembersForSourceType(sourceType)
        val source = runCatching { SourceType.valueOf(sourceType) }.getOrNull() ?: return
        val sourceItems = getLibraryItemsForSourceType(sourceType).map(ReadableItemLibraryEntity::toDomain)
        val gridRoots = deriveGridRootsWithMembers(source, sourceItems)
        if (gridRoots.isNotEmpty()) {
            val roots = gridRoots.map(LibraryGridRoot::root)
            insertOrReplaceGridRoots(roots.map(ReadableItem::toGridRootEntity))
            insertOrReplaceRootFacets(roots.flatMap(ReadableItem::toRootFacetEntities))
            insertOrReplaceRootMembers(gridRoots.flatMap(LibraryGridRoot::toMemberEntities))
        }
    }

    /**
     * Recompute grid roots for just the given libraries from their current readable_items. A series
     * never spans libraries (its group key is prefixed by libraryId), so a per-library recompute is
     * both correct and bounded — the delta path uses this instead of scanning the whole source.
     */
    @Transaction
    open fun rebuildGridRootsForLibraries(
        sourceType: String,
        libraryIds: Set<String>,
    ) {
        val source = runCatching { SourceType.valueOf(sourceType) }.getOrNull() ?: return
        libraryIds.forEach { libraryId ->
            deleteGridRootsForLibrary(libraryId)
            deleteRootFacetsForLibrary(libraryId)
            deleteRootMembersForLibrary(libraryId)
            val items = getLibraryItemsForLibrary(libraryId).map(ReadableItemLibraryEntity::toDomain)
            val gridRoots = deriveGridRootsWithMembers(source, items)
            if (gridRoots.isNotEmpty()) {
                val roots = gridRoots.map(LibraryGridRoot::root)
                insertOrReplaceGridRoots(roots.map(ReadableItem::toGridRootEntity))
                insertOrReplaceRootFacets(roots.flatMap(ReadableItem::toRootFacetEntities))
                insertOrReplaceRootMembers(gridRoots.flatMap(LibraryGridRoot::toMemberEntities))
            }
        }
    }

    /**
     * One-time backfill after the 24->25 migration (or a fresh install): populate grid roots for
     * every source when the table is empty. A no-op once populated; catalog writes keep it current.
     */
    @Transaction
    open fun rebuildAllGridRootsIfEmpty() {
        if (countGridRoots() > 0) return
        SourceType.entries.forEach { source -> rebuildGridRootsForSourceType(source.name) }
    }

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
        rebuildGridRootsForSourceType(sourceType)
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
        // Capture the libraries touched by this delta (including the pre-image libraryId of each
        // upserted/removed item, so a library that loses its last item — or an item that moves
        // libraries — is recomputed too) before the item rows change.
        val affectedLibraryIds = mutableSetOf<String>()
        val preImageItemIds = upsertItems.map(ReadableItemEntity::id) + removeItemIds
        preImageItemIds.chunked(SQLITE_BIND_SAFE_CHUNK_SIZE).forEach { chunk ->
            getLibraryItemsByIds(chunk).forEach { item -> affectedLibraryIds.add(item.libraryId) }
        }
        upsertItems.forEach { item -> affectedLibraryIds.add(item.libraryId) }

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
        if (affectedLibraryIds.isNotEmpty()) {
            rebuildGridRootsForLibraries(sourceType, affectedLibraryIds)
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
