package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ServerEntity
import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.SampleCatalogRepository
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface RemoteCatalogWriter {
    suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem>

    suspend fun replaceServerCatalog(
        server: ServerEntity,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    )
}

class RoomRemoteCatalogRepository(
    private val remoteCatalogDao: RemoteCatalogDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryRepository, ReadableItemRepository, RemoteCatalogWriter {
    override fun getLibraries(): List<Library> = emptyList()

    override fun observeLibraries(): Flow<List<Library>> =
        remoteCatalogDao.observeLibraries().map { libraries ->
            libraries.map(LibraryEntity::toDomain)
        }

    override fun getItems(): List<ReadableItem> = emptyList()

    override fun observeItems(): Flow<List<ReadableItem>> =
        remoteCatalogDao.observeItems().map { items ->
            items.map(ReadableItemEntity::toDomain)
        }

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = withContext(ioDispatcher) {
        remoteCatalogDao.getItemsForSourceType(sourceType.name)
            .map(ReadableItemEntity::toDomain)
    }

    override suspend fun replaceServerCatalog(
        server: ServerEntity,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
    ) = withContext(ioDispatcher) {
        remoteCatalogDao.replaceServerCatalog(
            server = server,
            libraries = libraries,
            items = items,
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
