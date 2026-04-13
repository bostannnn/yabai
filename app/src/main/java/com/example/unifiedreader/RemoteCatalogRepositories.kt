package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
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
import kotlinx.coroutines.withContext

interface RemoteCatalogReadiness {
    val isHydratedFlow: StateFlow<Boolean>
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
    RemoteLibraryCacheStore {
    private val librariesState = MutableStateFlow<List<Library>>(emptyList())
    private val itemsState = MutableStateFlow<List<ReadableItem>>(emptyList())
    private val hydrationState = MutableStateFlow(false)
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState.asStateFlow()

    init {
        scope.launch {
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
                    remoteCatalogDao.getItems()
                        .map(ReadableItemEntity::toDomain)
                }.onSuccess { items ->
                    itemsState.value = items
                }.onFailure { throwable ->
                    logSeedFailure("Unable to seed remote catalog items.", throwable)
                }
            } finally {
                hydrationState.value = true
            }
        }
        scope.launch {
            remoteCatalogDao.observeLibraries().collect { libraries ->
                librariesState.value = libraries.map(LibraryEntity::toDomain)
            }
        }
        scope.launch {
            remoteCatalogDao.observeItems().collect { items ->
                itemsState.value = items.map(ReadableItemEntity::toDomain)
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
        remoteCatalogDao.getItemsForSourceType(sourceType.name)
            .map(ReadableItemEntity::toDomain)
    }

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) = withContext(ioDispatcher) {
        remoteCatalogDao.replaceSourceCatalog(
            sourceType = sourceType.name,
            libraries = libraries,
            items = items,
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

private const val LOG_TAG = "RemoteCatalogRepo"
