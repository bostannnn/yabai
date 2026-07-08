package com.example.unifiedreader

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.LibraryGridRootEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadableItemLibraryEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.database.entity.toRemoteLibraryCacheEntity
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesGroupKey
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.SampleCatalogRepository
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface RemoteCatalogReadiness {
    val isHydratedFlow: StateFlow<Boolean>
}

/**
 * Targeted item lookups backed by bounded SQL, so id-based consumers (selected item, reader
 * next-item, series titles, cover preload, downloads) can resolve specific items without scanning
 * the whole in-memory catalog. The migration foundation for retiring `AppUiState.items` (Stage E).
 */
interface ReadableItemLookup {
    suspend fun getItemById(itemId: String): ReadableItem?

    suspend fun getItemsByIds(itemIds: List<String>): List<ReadableItem>

    /**
     * The catalog items in [item]'s series (its sibling issues), resolved via `library_root_members`
     * so series continuation reads only the relevant issues instead of the whole catalog. Empty for a
     * standalone item with no series.
     */
    suspend fun getSeriesMemberItems(item: ReadableItem): List<ReadableItem>
}

/**
 * Streams the library grid as pages of already-derived roots from `library_grid_roots`, so only the
 * visible window is resident instead of the whole catalog. Only the column sorts (TITLE, DATE_ADDED)
 * are paged today; LAST_READ needs the progress join (Stage D).
 */
interface LibraryGridPager {
    fun gridRootsPagingData(
        source: SourceType,
        sortMode: LibrarySortMode,
    ): Flow<PagingData<ReadableItem>>

    /** One-time backfill of the derived grid roots after the 24->25 migration. No-op once populated. */
    suspend fun rebuildGridRootsIfEmpty()
}

interface RemoteCatalogHydrator {
    suspend fun hydrate()
}

interface StartupRemoteCatalogCacheSeeder {
    suspend fun seedForStartupSource(sourceType: SourceType)
}

interface ColdStartCatalogSeeder {
    /**
     * Loads exactly the given cached items (by id) into the in-memory catalog so a cold start can
     * render the last-seen view before the full catalog hydrates. No-ops if the source already has
     * items or is hydrated, so it never shrinks a fully loaded catalog.
     */
    suspend fun seedItemsByIds(sourceType: SourceType, orderedItemIds: List<String>)
}

interface RemoteLibraryCacheStore {
    suspend fun getCachedLibraries(
        sourceType: SourceType,
        baseUrl: String,
        username: String,
    ): List<Library>

    suspend fun replaceCachedLibraries(
        sourceType: SourceType,
        baseUrl: String,
        username: String,
        libraries: List<Library>,
    )
}

class RoomRemoteCatalogRepository(
    private val remoteCatalogDao: RemoteCatalogDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : LibraryRepository,
    ReadableItemRepository,
    RemoteCatalogWriter,
    RemoteCatalogReadiness,
    RemoteCatalogHydrator,
    StartupRemoteCatalogCacheSeeder,
    ColdStartCatalogSeeder,
    LibraryGridPager,
    ReadableItemLookup,
    RemoteLibraryCacheStore {
    private val librariesState = MutableStateFlow<List<Library>>(emptyList())
    private val itemsState = MutableStateFlow<List<ReadableItem>>(emptyList())
    private val hydrationState = MutableStateFlow(false)
    private val hydrationMutex = kotlinx.coroutines.sync.Mutex()
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState.asStateFlow()

    init {
        scope.launch {
            remoteCatalogDao.observeLibraries().collect { libraries ->
                librariesState.value = libraries.map(LibraryEntity::toDomain)
            }
        }
        scope.launch {
            remoteCatalogDao.observeLibraryItems().collect { items ->
                itemsState.value = items.map(ReadableItemLibraryEntity::toDomain)
            }
        }
    }

    override suspend fun hydrate() {
        if (hydrationState.value) return
        hydrationMutex.withLock {
            if (hydrationState.value) return@withLock
            try {
                runCatching {
                    remoteCatalogDao.getLibraries()
                        .map(LibraryEntity::toDomain)
                }.onSuccess { libraries ->
                    librariesState.value = libraries
                }.onFailure { throwable ->
                    logSeedFailure("Unable to seed remote catalog libraries.", throwable)
                }
                runCatching {
                    remoteCatalogDao.getLibraryItems()
                        .map(ReadableItemLibraryEntity::toDomain)
                }.onSuccess { items ->
                    itemsState.value = items
                }.onFailure { throwable ->
                    logSeedFailure("Unable to seed remote catalog items.", throwable)
                }
            } finally {
                hydrationState.value = true
            }
        }
    }

    override suspend fun seedForStartupSource(
        sourceType: SourceType,
    ) {
        if (sourceType == SourceType.LOCAL) return
        withContext(ioDispatcher) {
            runCatching {
                remoteCatalogDao.getLibraries()
                    .filter { library -> library.sourceType == sourceType.name }
                    .map(LibraryEntity::toDomain)
            }.onSuccess { libraries ->
                librariesState.value = librariesState.value.replaceSource(
                    sourceType = sourceType,
                    replacement = libraries,
                    sourceSelector = Library::sourceType,
                )
            }.onFailure { throwable ->
                logSeedFailure("Unable to seed startup remote catalog libraries.", throwable)
            }
            runCatching {
                remoteCatalogDao.getLibraryItemsForSourceType(sourceType.name)
                    .map(ReadableItemLibraryEntity::toDomain)
            }.onSuccess { items ->
                itemsState.value = itemsState.value.replaceSource(
                    sourceType = sourceType,
                    replacement = items,
                    sourceSelector = ReadableItem::sourceType,
                )
            }.onFailure { throwable ->
                logSeedFailure("Unable to seed startup remote catalog items.", throwable)
            }
        }
    }

    override suspend fun seedItemsByIds(
        sourceType: SourceType,
        orderedItemIds: List<String>,
    ) {
        if (sourceType == SourceType.LOCAL || orderedItemIds.isEmpty()) return
        if (hydrationState.value || itemsState.value.any { item -> item.sourceType == sourceType }) return
        withContext(ioDispatcher) {
            runCatching {
                remoteCatalogDao.getLibraries()
                    .filter { library -> library.sourceType == sourceType.name }
                    .map(LibraryEntity::toDomain)
            }.onSuccess { libraries ->
                if (librariesState.value.none { library -> library.sourceType == sourceType }) {
                    librariesState.value = librariesState.value.replaceSource(
                        sourceType = sourceType,
                        replacement = libraries,
                        sourceSelector = Library::sourceType,
                    )
                }
            }.onFailure { throwable ->
                logSeedFailure("Unable to seed cold-start remote catalog libraries.", throwable)
            }
            runCatching {
                remoteCatalogDao.getLibraryItemsByIds(orderedItemIds)
                    .map(ReadableItemLibraryEntity::toDomain)
                    .filter { item -> item.sourceType == sourceType }
            }.onSuccess { items ->
                // Only publish while the source is still empty; the full catalog (once loaded)
                // always wins so the bounded cold-start set never shrinks it.
                val current = itemsState.value
                if (current.none { item -> item.sourceType == sourceType }) {
                    itemsState.value = current.replaceSource(
                        sourceType = sourceType,
                        replacement = items,
                        sourceSelector = ReadableItem::sourceType,
                    )
                }
            }.onFailure { throwable ->
                logSeedFailure("Unable to seed cold-start remote catalog items.", throwable)
            }
        }
    }

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState.asStateFlow()

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState.asStateFlow()

    override fun gridRootsPagingData(
        source: SourceType,
        sortMode: LibrarySortMode,
    ): Flow<PagingData<ReadableItem>> = Pager(
        config = PagingConfig(
            pageSize = GRID_PAGE_SIZE,
            enablePlaceholders = false,
        ),
    ) {
        when (sortMode) {
            LibrarySortMode.DATE_ADDED -> remoteCatalogDao.pagingGridRootsByDateAdded(source.name)
            LibrarySortMode.LAST_READ -> remoteCatalogDao.pagingGridRootsByLastRead(source.name)
            else -> remoteCatalogDao.pagingGridRootsByTitle(source.name)
        }
    }.flow.map { pagingData -> pagingData.map(LibraryGridRootEntity::toDomain) }

    /**
     * One-time backfill of the derived grid roots after the 24->25 migration, so existing installs
     * page a populated table before their next catalog sync. A no-op once populated.
     */
    override suspend fun rebuildGridRootsIfEmpty() = withContext(ioDispatcher) {
        runCatching { remoteCatalogDao.rebuildAllGridRootsIfEmpty() }
            .onFailure { throwable -> logSeedFailure("Unable to backfill library grid roots.", throwable) }
        Unit
    }

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = withContext(ioDispatcher) {
        remoteCatalogDao.getLibraryItemsForSourceType(sourceType.name)
            .map(ReadableItemLibraryEntity::toDomain)
    }

    override suspend fun getItemById(
        itemId: String,
    ): ReadableItem? = withContext(ioDispatcher) {
        remoteCatalogDao.getLibraryItemById(itemId)?.toDomain()
    }

    override suspend fun getItemsByIds(
        itemIds: List<String>,
    ): List<ReadableItem> {
        if (itemIds.isEmpty()) return emptyList()
        return withContext(ioDispatcher) {
            itemIds.chunked(ITEM_ID_LOOKUP_CHUNK_SIZE).flatMap { chunk ->
                remoteCatalogDao.getLibraryItemsByIds(chunk).map(ReadableItemLibraryEntity::toDomain)
            }
        }
    }

    override suspend fun getSeriesMemberItems(
        item: ReadableItem,
    ): List<ReadableItem> {
        val seriesContainerId = item.seriesContainerIdOrNull() ?: return emptyList()
        return withContext(ioDispatcher) {
            val memberItemIds = remoteCatalogDao.getMemberItemIds(seriesContainerId)
            if (memberItemIds.isEmpty()) {
                emptyList()
            } else {
                remoteCatalogDao.getLibraryItemsByIds(memberItemIds).map(ReadableItemLibraryEntity::toDomain)
            }
        }
    }

    override suspend fun getCatalogSyncGroups(
        sourceType: SourceType,
    ): List<RemoteCatalogSyncGroup> = withContext(ioDispatcher) {
        remoteCatalogDao.getCatalogSyncGroupsForSourceType(sourceType.name)
            .map { entity ->
                RemoteCatalogSyncGroup(
                    groupId = entity.groupId,
                    libraryId = entity.libraryId,
                    fingerprint = entity.fingerprint,
                )
            }
    }

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
        syncGroups: List<RemoteCatalogSyncGroupEntity>,
    ) = withContext(ioDispatcher) {
        remoteCatalogDao.replaceSourceCatalog(
            sourceType = sourceType.name,
            libraries = libraries,
            items = items,
            syncGroups = syncGroups,
        )
    }

    override suspend fun applySourceCatalogDelta(
        delta: RemoteCatalogDelta,
    ) = withContext(ioDispatcher) {
        remoteCatalogDao.applySourceCatalogDelta(
            sourceType = delta.sourceType.name,
            libraries = delta.libraries,
            upsertItems = delta.upsertItems,
            removeItemIds = delta.removeItemIds.toList(),
            syncGroups = delta.syncGroups,
            removeSyncGroupIds = delta.removeSyncGroupIds.toList(),
        )
    }

    override suspend fun getCachedLibraries(
        sourceType: SourceType,
        baseUrl: String,
        username: String,
    ): List<Library> = withContext(ioDispatcher) {
        remoteCatalogDao.getCachedLibraries(
            sourceType = sourceType.name,
            baseUrl = baseUrl,
            username = username,
        ).map { entity -> entity.toDomain() }
    }

    override suspend fun replaceCachedLibraries(
        sourceType: SourceType,
        baseUrl: String,
        username: String,
        libraries: List<Library>,
    ) = withContext(ioDispatcher) {
        remoteCatalogDao.replaceCachedLibraries(
            sourceType = sourceType.name,
            baseUrl = baseUrl,
            username = username,
            entries = libraries.map { library ->
                library.toRemoteLibraryCacheEntity(
                    baseUrl = baseUrl,
                    username = username,
                )
            },
        )
    }
}

class CompositeCatalogRepository(
    private val sampleCatalogRepository: SampleCatalogRepository,
    private val remoteCatalogRepository: RoomRemoteCatalogRepository,
) : LibraryRepository, ReadableItemRepository {
    override fun getLibraries(): List<Library> =
        sampleCatalogRepository.getLibraries() + remoteCatalogRepository.getLibraries()

    override fun observeLibraries(): Flow<List<Library>> = combine(
        sampleCatalogRepository.observeLibraries(),
        remoteCatalogRepository.observeLibraries(),
    ) { sampleLibraries, remoteLibraries ->
        sampleLibraries + remoteLibraries
    }

    override fun getItems(): List<ReadableItem> =
        sampleCatalogRepository.getItems() + remoteCatalogRepository.getItems()

    override fun observeItems(): Flow<List<ReadableItem>> = combine(
        sampleCatalogRepository.observeItems(),
        remoteCatalogRepository.observeItems(),
    ) { sampleItems, remoteItems ->
        sampleItems + remoteItems
    }
}

private fun logSeedFailure(
    message: String,
    throwable: Throwable,
) {
    runCatching {
        Log.w(LOG_TAG, message, throwable)
    }
}

private fun ReadableItem.seriesContainerIdOrNull(): String? = when (sourceType) {
    SourceType.KAVITA -> kavitaSeriesGroupKey()?.let(::kavitaSeriesContainerId)
    SourceType.STUMP -> stumpSeriesGroupKey()?.let(::stumpSeriesContainerId)
    SourceType.GRIMMORY,
    SourceType.LOCAL -> null
}

private fun <T> List<T>.replaceSource(
    sourceType: SourceType,
    replacement: List<T>,
    sourceSelector: (T) -> SourceType,
): List<T> = filterNot { item -> sourceSelector(item) == sourceType } + replacement

private const val LOG_TAG = "RemoteCatalogRepo"
private const val GRID_PAGE_SIZE = 60
// SQLite caps bound variables (~999); chunk id lookups well under that, matching the DAO's own limit.
private const val ITEM_ID_LOOKUP_CHUNK_SIZE = 900
