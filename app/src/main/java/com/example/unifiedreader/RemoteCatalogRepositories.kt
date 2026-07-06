package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadableItemLibraryEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.database.entity.toRemoteLibraryCacheEntity
import com.example.unifiedreader.core.model.SourceType
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface RemoteCatalogReadiness {
    val isHydratedFlow: StateFlow<Boolean>
}

interface RemoteCatalogHydrator {
    suspend fun hydrate()
}

interface StartupRemoteCatalogCacheSeeder {
    suspend fun seedForStartupSource(sourceType: SourceType)
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

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState.asStateFlow()

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState.asStateFlow()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = withContext(ioDispatcher) {
        remoteCatalogDao.getLibraryItemsForSourceType(sourceType.name)
            .map(ReadableItemLibraryEntity::toDomain)
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

private fun <T> List<T>.replaceSource(
    sourceType: SourceType,
    replacement: List<T>,
    sourceSelector: (T) -> SourceType,
): List<T> = filterNot { item -> sourceSelector(item) == sourceType } + replacement

private const val LOG_TAG = "RemoteCatalogRepo"
