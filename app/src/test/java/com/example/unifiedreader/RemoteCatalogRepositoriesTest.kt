package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadableItemLibraryEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogRepositoriesTest {
    @Test
    fun `room remote catalog repository hydrates cached libraries and items on demand`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = listOf(
                LibraryEntity(
                    id = SourceType.GRIMMORY.remoteLibraryId("books"),
                    sourceType = SourceType.GRIMMORY.name,
                    title = "Books",
                    remoteId = "books",
                    contentType = LibraryContentType.BOOKS.name,
                ),
            ),
            items = listOf(
                ReadableItemEntity(
                    id = SourceType.GRIMMORY.remoteItemId("book-1"),
                    sourceType = SourceType.GRIMMORY.name,
                    libraryId = SourceType.GRIMMORY.remoteLibraryId("books"),
                    remoteId = "book-1",
                    isLocalImport = false,
                    remoteFileId = "11",
                    remoteFileType = FormatType.EPUB.name,
                    title = "Book 1",
                    format = FormatType.EPUB.name,
                    kind = ReadableItemKind.BOOK.name,
                    pageCount = 200,
                    coverUrl = null,
                    description = null,
                    addedAtEpochMillis = 10L,
                    bookAuthor = "Author",
                    bookAuthorsJson = null,
                    bookSeriesName = null,
                    bookPublishersJson = null,
                    bookGenresJson = null,
                    bookTagsJson = null,
                    bookLanguagesJson = null,
                    comicSeries = null,
                    comicIssueTitle = null,
                    comicVolume = null,
                    comicIssueNumber = null,
                    comicPublishersJson = null,
                    comicGenresJson = null,
                    comicTagsJson = null,
                    comicLanguagesJson = null,
                    updatedAtEpochMillis = 10L,
                ),
            ),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomRemoteCatalogRepository(
                remoteCatalogDao = dao,
                ioDispatcher = Dispatchers.Unconfined,
                scope = scope,
            )
            repository.hydrate()

            val libraries = withTimeout(1_000L) {
                repository.observeLibraries()
                    .dropWhile { it.isEmpty() }
                    .first()
            }
            val items = withTimeout(1_000L) {
                repository.observeItems()
                    .dropWhile { it.isEmpty() }
                    .first()
            }

            assertEquals(listOf("Books"), libraries.map { it.title })
            assertEquals(listOf("Book 1"), items.map { it.title })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `room remote catalog repository still reports hydrated when initial item seed fails`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = emptyList(),
            items = emptyList(),
            getItemsFailure = IllegalStateException("boom"),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomRemoteCatalogRepository(
                remoteCatalogDao = dao,
                ioDispatcher = Dispatchers.Unconfined,
                scope = scope,
            )
            repository.hydrate()

            assertTrue(withTimeout(1_000L) {
                repository.isHydratedFlow.first { it }
            })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `room remote catalog repository persists cached remote libraries by source and account`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = emptyList(),
            items = emptyList(),
        )
        val repository = RoomRemoteCatalogRepository(
            remoteCatalogDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        repository.replaceCachedLibraries(
            sourceType = SourceType.KAVITA,
            baseUrl = "https://kavita.example",
            username = "reader",
            libraries = listOf(
                com.example.unifiedreader.core.model.Library(
                    id = SourceType.KAVITA.remoteLibraryId("comics"),
                    sourceType = SourceType.KAVITA,
                    title = "Comics",
                    remoteId = "comics",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
        )

        val cachedLibraries = repository.getCachedLibraries(
            sourceType = SourceType.KAVITA,
            baseUrl = "https://kavita.example",
            username = "reader",
        )

        assertEquals(listOf("Comics"), cachedLibraries.map { it.title })
    }

    @Test
    fun `room remote catalog repository applies source catalog delta`() = runBlocking {
        val unchangedItem = remoteCatalogItem(
            sourceType = SourceType.KAVITA,
            remoteId = "unchanged",
            title = "Unchanged",
        )
        val removedItem = remoteCatalogItem(
            sourceType = SourceType.KAVITA,
            remoteId = "removed",
            title = "Removed",
        )
        val otherSourceItem = remoteCatalogItem(
            sourceType = SourceType.GRIMMORY,
            remoteId = "other",
            title = "Other",
        )
        val dao = FakeRemoteCatalogDao(
            libraries = listOf(
                LibraryEntity(
                    id = SourceType.KAVITA.remoteLibraryId("old-comics"),
                    sourceType = SourceType.KAVITA.name,
                    title = "Old Comics",
                    remoteId = "old-comics",
                    contentType = LibraryContentType.COMICS.name,
                ),
            ),
            items = listOf(unchangedItem, removedItem, otherSourceItem),
        )
        val repository = RoomRemoteCatalogRepository(
            remoteCatalogDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        repository.applySourceCatalogDelta(
            RemoteCatalogDelta(
                sourceType = SourceType.KAVITA,
                libraries = listOf(
                    LibraryEntity(
                        id = SourceType.KAVITA.remoteLibraryId("new-comics"),
                        sourceType = SourceType.KAVITA.name,
                        title = "New Comics",
                        remoteId = "new-comics",
                        contentType = LibraryContentType.COMICS.name,
                    ),
                ),
                upsertItems = listOf(
                    remoteCatalogItem(
                        sourceType = SourceType.KAVITA,
                        remoteId = "changed",
                        title = "Changed",
                    ),
                ),
                removeItemIds = setOf(removedItem.id),
                syncGroups = listOf(
                    RemoteCatalogSyncGroupEntity(
                        sourceType = SourceType.KAVITA.name,
                        groupId = "new-group",
                        libraryId = SourceType.KAVITA.remoteLibraryId("new-comics"),
                        fingerprint = "fingerprint",
                        updatedAtEpochMillis = 1_000L,
                    ),
                ),
            ),
        )

        assertEquals(
            setOf("Unchanged", "Changed"),
            repository.getItemsForSourceType(SourceType.KAVITA).map { item -> item.title }.toSet(),
        )
        assertEquals(listOf("Other"), repository.getItemsForSourceType(SourceType.GRIMMORY).map { item -> item.title })
        assertEquals(listOf("new-group"), repository.getCatalogSyncGroups(SourceType.KAVITA).map { group -> group.groupId })
    }

    @Test
    fun `room remote catalog repository chunks large source catalog delta deletes`() = runBlocking {
        val dao = FakeRemoteCatalogDao(
            libraries = emptyList(),
            items = emptyList(),
        )
        val repository = RoomRemoteCatalogRepository(
            remoteCatalogDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        repository.applySourceCatalogDelta(
            RemoteCatalogDelta(
                sourceType = SourceType.KAVITA,
                libraries = emptyList(),
                upsertItems = emptyList(),
                removeItemIds = (1..1_200).map { index -> "item-$index" }.toSet(),
                removeSyncGroupIds = (1..1_200).map { index -> "group-$index" }.toSet(),
            ),
        )

        assertEquals(listOf(900, 300), dao.deletedItemBatchSizes)
        assertEquals(listOf(900, 300), dao.deletedSyncGroupBatchSizes)
    }
}

private fun remoteCatalogItem(
    sourceType: SourceType,
    remoteId: String,
    title: String,
): ReadableItemEntity = ReadableItemEntity(
    id = sourceType.remoteItemId(remoteId),
    sourceType = sourceType.name,
    libraryId = sourceType.remoteLibraryId("library"),
    remoteId = remoteId,
    isLocalImport = false,
    remoteFileId = remoteId,
    remoteFileType = "series",
    title = title,
    format = FormatType.CBZ.name,
    kind = ReadableItemKind.COMIC.name,
    pageCount = 24,
    coverUrl = null,
    description = null,
    addedAtEpochMillis = null,
    bookAuthor = null,
    bookAuthorsJson = null,
    bookSeriesName = null,
    bookPublishersJson = null,
    bookGenresJson = null,
    bookTagsJson = null,
    bookLanguagesJson = null,
    comicSeries = null,
    comicIssueTitle = null,
    comicVolume = null,
    comicIssueNumber = null,
    comicPublishersJson = null,
    comicGenresJson = null,
    comicTagsJson = null,
    comicLanguagesJson = null,
    updatedAtEpochMillis = 1_000L,
)

private class FakeRemoteCatalogDao(
    libraries: List<LibraryEntity>,
    items: List<ReadableItemEntity>,
    private val getItemsFailure: Throwable? = null,
) : RemoteCatalogDao() {
    private val librariesState = MutableStateFlow(libraries)
    private val itemsState = MutableStateFlow(items)
    private val syncGroupsState = MutableStateFlow<List<RemoteCatalogSyncGroupEntity>>(emptyList())
    private val cachedLibrariesByKey = mutableMapOf<Triple<String, String, String>, List<RemoteLibraryCacheEntity>>()
    val deletedItemBatchSizes = mutableListOf<Int>()
    val deletedSyncGroupBatchSizes = mutableListOf<Int>()

    override fun getLibraries(): List<LibraryEntity> = librariesState.value

    override fun observeLibraries(): Flow<List<LibraryEntity>> = librariesState

    override fun getItems(): List<ReadableItemEntity> {
        return itemsState.value
    }

    override fun observeItems(): Flow<List<ReadableItemEntity>> = itemsState

    override fun getItemsForSourceType(sourceType: String): List<ReadableItemEntity> =
        itemsState.value.filter { it.sourceType == sourceType }

    override fun getLibraryItems(): List<ReadableItemLibraryEntity> {
        getItemsFailure?.let { throw it }
        return itemsState.value.map(ReadableItemEntity::toLibraryEntity)
    }

    override fun observeLibraryItems(): Flow<List<ReadableItemLibraryEntity>> =
        itemsState.map { items ->
            items.map(ReadableItemEntity::toLibraryEntity)
        }

    override fun getLibraryItemsForSourceType(sourceType: String): List<ReadableItemLibraryEntity> =
        itemsState.value
            .filter { it.sourceType == sourceType }
            .map(ReadableItemEntity::toLibraryEntity)

    override fun getCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
    ): List<RemoteLibraryCacheEntity> = cachedLibrariesByKey[Triple(sourceType, baseUrl, username)].orEmpty()

    override fun getCatalogSyncGroupsForSourceType(
        sourceType: String,
    ): List<RemoteCatalogSyncGroupEntity> = syncGroupsState.value.filter { it.sourceType == sourceType }

    override fun insertOrReplaceLibraries(libraries: List<LibraryEntity>) {
        val replacementIds = libraries.mapTo(mutableSetOf(), LibraryEntity::id)
        librariesState.value = librariesState.value.filterNot { library -> library.id in replacementIds } + libraries
    }

    override fun insertOrReplaceItems(items: List<ReadableItemEntity>) {
        val replacementIds = items.mapTo(mutableSetOf(), ReadableItemEntity::id)
        itemsState.value = itemsState.value.filterNot { item -> item.id in replacementIds } + items
    }

    override fun insertOrReplaceCatalogSyncGroups(groups: List<RemoteCatalogSyncGroupEntity>) {
        val replacementKeys = groups.mapTo(mutableSetOf()) { group -> group.sourceType to group.groupId }
        syncGroupsState.value = syncGroupsState.value
            .filterNot { group -> group.sourceType to group.groupId in replacementKeys } + groups
    }

    override fun insertOrReplaceCachedLibraries(entries: List<RemoteLibraryCacheEntity>) {
        entries.groupBy { Triple(it.sourceType, it.baseUrl, it.username) }
            .forEach { (key, value) ->
                cachedLibrariesByKey[key] = value
            }
    }

    protected override fun deleteItemsForSourceType(sourceType: String) {
        itemsState.value = itemsState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteItemsForSourceTypeByIds(
        sourceType: String,
        itemIds: List<String>,
    ) {
        deletedItemBatchSizes += itemIds.size
        itemsState.value = itemsState.value.filterNot { item ->
            item.sourceType == sourceType && item.id in itemIds
        }
    }

    protected override fun deleteLibrariesForSourceType(sourceType: String) {
        librariesState.value = librariesState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteCatalogSyncGroupsForSourceType(sourceType: String) {
        syncGroupsState.value = syncGroupsState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteCatalogSyncGroupsForSourceTypeByIds(
        sourceType: String,
        groupIds: List<String>,
    ) {
        deletedSyncGroupBatchSizes += groupIds.size
        syncGroupsState.value = syncGroupsState.value.filterNot { group ->
            group.sourceType == sourceType && group.groupId in groupIds
        }
    }

    protected override fun deleteCachedLibraries(
        sourceType: String,
        baseUrl: String,
        username: String,
    ) {
        cachedLibrariesByKey.remove(Triple(sourceType, baseUrl, username))
    }
}

private fun ReadableItemEntity.toLibraryEntity(): ReadableItemLibraryEntity = ReadableItemLibraryEntity(
    id = id,
    sourceType = sourceType,
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format,
    kind = kind,
    pageCount = pageCount,
    coverUrl = coverUrl,
    description = description?.take(512),
    addedAtEpochMillis = addedAtEpochMillis,
    bookAuthor = bookAuthor,
    bookAuthorsJson = bookAuthorsJson,
    bookSeriesName = bookSeriesName,
    bookPublishersJson = bookPublishersJson,
    bookGenresJson = bookGenresJson,
    bookTagsJson = bookTagsJson,
    bookLanguagesJson = bookLanguagesJson,
    comicSeries = comicSeries,
    comicIssueTitle = comicIssueTitle,
    comicVolume = comicVolume,
    comicIssueNumber = comicIssueNumber,
    comicPublishersJson = comicPublishersJson,
    comicGenresJson = comicGenresJson,
    comicTagsJson = comicTagsJson,
    comicLanguagesJson = comicLanguagesJson,
)
