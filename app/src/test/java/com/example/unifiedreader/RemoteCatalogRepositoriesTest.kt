package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import androidx.paging.PagingSource
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.LibraryGridRootEntity
import com.example.unifiedreader.core.database.entity.LibraryMemberBadgeProjection
import com.example.unifiedreader.core.database.entity.LibraryRootFacetEntity
import com.example.unifiedreader.core.database.entity.LibraryRootMemberEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.ReadableItemLibraryEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
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
import org.junit.Assert.assertFalse
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
    fun `room remote catalog repository startup seed reads selected source cache without hydrating catalog`() = runBlocking {
        val existingGrimmoryLibrary = remoteCatalogLibrary(
            sourceType = SourceType.GRIMMORY,
            remoteId = "existing-books",
            title = "Existing Books",
        )
        val cachedKavitaLibrary = remoteCatalogLibrary(
            sourceType = SourceType.KAVITA,
            remoteId = "cached-comics",
            title = "Cached Comics",
        )
        val otherStumpLibrary = remoteCatalogLibrary(
            sourceType = SourceType.STUMP,
            remoteId = "other-comics",
            title = "Other Comics",
        )
        val existingGrimmoryItem = remoteCatalogItem(
            sourceType = SourceType.GRIMMORY,
            remoteId = "existing-book",
            title = "Existing Book",
        )
        val cachedKavitaItem = remoteCatalogItem(
            sourceType = SourceType.KAVITA,
            remoteId = "cached-issue",
            title = "Cached Issue",
        )
        val otherStumpItem = remoteCatalogItem(
            sourceType = SourceType.STUMP,
            remoteId = "other-issue",
            title = "Other Issue",
        )
        val dao = FakeRemoteCatalogDao(
            libraries = listOf(cachedKavitaLibrary, otherStumpLibrary),
            items = listOf(cachedKavitaItem, otherStumpItem),
            observedLibraries = listOf(existingGrimmoryLibrary),
            observedItems = listOf(existingGrimmoryItem),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomRemoteCatalogRepository(
                remoteCatalogDao = dao,
                ioDispatcher = Dispatchers.Unconfined,
                scope = scope,
            )

            repository.seedForStartupSource(SourceType.KAVITA)

            assertEquals(
                setOf("Existing Books", "Cached Comics"),
                repository.getLibraries().map { library -> library.title }.toSet(),
            )
            assertEquals(
                setOf("Existing Book", "Cached Issue"),
                repository.getItems().map { item -> item.title }.toSet(),
            )
            assertFalse(repository.isHydratedFlow.value)
            assertEquals(listOf(SourceType.KAVITA.name), dao.getLibraryItemsForSourceTypeCalls)
            assertEquals(0, dao.getLibraryItemsCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cold start seed loads only the requested item ids without hydrating`() = runBlocking {
        val kavitaLibrary = remoteCatalogLibrary(
            sourceType = SourceType.KAVITA,
            remoteId = "comics",
            title = "Comics",
        )
        val items = (1..4).map { index ->
            remoteCatalogItem(
                sourceType = SourceType.KAVITA,
                remoteId = "issue-$index",
                title = "Issue $index",
            )
        }
        val dao = FakeRemoteCatalogDao(
            libraries = listOf(kavitaLibrary),
            items = items,
            observedLibraries = emptyList(),
            observedItems = emptyList(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = RoomRemoteCatalogRepository(
                remoteCatalogDao = dao,
                ioDispatcher = Dispatchers.Unconfined,
                scope = scope,
            )
            val requestedIds = listOf(items[2].id, items[0].id)

            repository.seedItemsByIds(SourceType.KAVITA, requestedIds)

            assertEquals(
                setOf("Issue 3", "Issue 1"),
                repository.getItems()
                    .filter { item -> item.sourceType == SourceType.KAVITA }
                    .map { item -> item.title }
                    .toSet(),
            )
            assertEquals(listOf(requestedIds), dao.getLibraryItemsByIdsCalls)
            assertFalse(repository.isHydratedFlow.value)
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
    fun `readable item lookup resolves items by id via bounded queries`() = runBlocking {
        val alpha = remoteCatalogItem(sourceType = SourceType.KAVITA, remoteId = "a", title = "Alpha")
        val beta = remoteCatalogItem(sourceType = SourceType.KAVITA, remoteId = "b", title = "Beta")
        val dao = FakeRemoteCatalogDao(libraries = emptyList(), items = listOf(alpha, beta))
        val repository = RoomRemoteCatalogRepository(
            remoteCatalogDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        assertEquals("Alpha", repository.getItemById(alpha.id)?.title)
        assertEquals(null, repository.getItemById("kavita:item:missing"))
        assertEquals(
            setOf("Alpha", "Beta"),
            repository.getItemsByIds(listOf(alpha.id, beta.id, "kavita:item:missing")).map { it.title }.toSet(),
        )
        assertEquals(emptyList<String>(), repository.getItemsByIds(emptyList()).map { it.title })
    }

    @Test
    fun `getSeriesMemberItems resolves a series from a container or an issue`() = runBlocking {
        val dao = FakeRemoteCatalogDao(libraries = emptyList(), items = emptyList())
        val repository = RoomRemoteCatalogRepository(
            remoteCatalogDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        // Two comics sharing remoteFileType "series" collapse into one container with both as members.
        repository.replaceSourceCatalog(
            sourceType = SourceType.KAVITA,
            libraries = listOf(remoteCatalogLibrary(SourceType.KAVITA, "library", "Library")),
            items = listOf(
                remoteCatalogItem(SourceType.KAVITA, "1", "Issue 1"),
                remoteCatalogItem(SourceType.KAVITA, "2", "Issue 2"),
            ),
        )

        val libraryId = SourceType.KAVITA.remoteLibraryId("library")
        val seriesKey = "$libraryId::series"
        val expectedIds = setOf(SourceType.KAVITA.remoteItemId("1"), SourceType.KAVITA.remoteItemId("2"))

        val container = ReadableItem(
            id = kavitaSeriesContainerId(seriesKey),
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = seriesKey,
            title = "Series",
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
        )
        assertEquals(expectedIds, repository.getSeriesMemberItems(container).map(ReadableItem::id).toSet())

        val issue = ReadableItem(
            id = SourceType.KAVITA.remoteItemId("1"),
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteFileType = "series",
            title = "Issue 1",
            format = FormatType.CBZ,
            kind = ReadableItemKind.COMIC,
        )
        assertEquals(expectedIds, repository.getSeriesMemberItems(issue).map(ReadableItem::id).toSet())

        val standalone = ReadableItem(
            id = "kavita:item:solo",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            title = "Solo",
            format = FormatType.EPUB,
            kind = ReadableItemKind.BOOK,
        )
        assertEquals(emptyList<String>(), repository.getSeriesMemberItems(standalone).map(ReadableItem::id))
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

private fun remoteCatalogLibrary(
    sourceType: SourceType,
    remoteId: String,
    title: String,
): LibraryEntity = LibraryEntity(
    id = sourceType.remoteLibraryId(remoteId),
    sourceType = sourceType.name,
    title = title,
    remoteId = remoteId,
    contentType = LibraryContentType.COMICS.name,
)

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
    observedLibraries: List<LibraryEntity> = libraries,
    observedItems: List<ReadableItemEntity> = items,
) : RemoteCatalogDao() {
    private val librariesState = MutableStateFlow(libraries)
    private val itemsState = MutableStateFlow(items)
    private val observedLibrariesState = MutableStateFlow(observedLibraries)
    private val observedItemsState = MutableStateFlow(observedItems)
    private val syncGroupsState = MutableStateFlow<List<RemoteCatalogSyncGroupEntity>>(emptyList())
    private val cachedLibrariesByKey = mutableMapOf<Triple<String, String, String>, List<RemoteLibraryCacheEntity>>()
    val deletedItemBatchSizes = mutableListOf<Int>()
    val deletedSyncGroupBatchSizes = mutableListOf<Int>()
    val getLibraryItemsForSourceTypeCalls = mutableListOf<String>()
    val getLibraryItemsByIdsCalls = mutableListOf<List<String>>()
    var getLibraryItemsCalls: Int = 0
        private set

    override fun getLibraries(): List<LibraryEntity> = librariesState.value

    override fun observeLibraries(): Flow<List<LibraryEntity>> = observedLibrariesState

    override fun getItems(): List<ReadableItemEntity> {
        return itemsState.value
    }

    override fun observeItems(): Flow<List<ReadableItemEntity>> = observedItemsState

    override fun getItemsForSourceType(sourceType: String): List<ReadableItemEntity> =
        itemsState.value.filter { it.sourceType == sourceType }

    override fun getLibraryItems(): List<ReadableItemLibraryEntity> {
        getLibraryItemsCalls += 1
        getItemsFailure?.let { throw it }
        return itemsState.value.map(ReadableItemEntity::toLibraryEntity)
    }

    override fun observeLibraryItems(): Flow<List<ReadableItemLibraryEntity>> =
        observedItemsState.map { items ->
            items.map(ReadableItemEntity::toLibraryEntity)
        }

    override fun getLibraryItemsForSourceType(sourceType: String): List<ReadableItemLibraryEntity> {
        getLibraryItemsForSourceTypeCalls += sourceType
        return itemsState.value
            .filter { it.sourceType == sourceType }
            .map(ReadableItemEntity::toLibraryEntity)
    }

    override fun getLibraryItemsByIds(itemIds: List<String>): List<ReadableItemLibraryEntity> {
        getLibraryItemsByIdsCalls += itemIds
        val requested = itemIds.toSet()
        return itemsState.value
            .filter { it.id in requested }
            .map(ReadableItemEntity::toLibraryEntity)
    }

    override fun getLibraryItemById(itemId: String): ReadableItemLibraryEntity? =
        itemsState.value.firstOrNull { it.id == itemId }?.toLibraryEntity()

    // The real bounded "progressed items + series siblings" logic is SQL, tested in core:database;
    // the fake just returns the source items (this test doesn't exercise shelves input).
    override fun getShelvesInputItems(sourceType: String): List<ReadableItemLibraryEntity> =
        itemsState.value.filter { it.sourceType == sourceType }.map(ReadableItemEntity::toLibraryEntity)

    // The real root->members join is SQL, tested in core:database; this test doesn't exercise it.
    override fun getMemberBadgeProjectionsForRoots(
        sourceType: String,
        rootIds: List<String>,
    ): List<LibraryMemberBadgeProjection> = emptyList()

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
        observedLibrariesState.value = observedLibrariesState.value
            .filterNot { library -> library.id in replacementIds } + libraries
    }

    override fun insertOrReplaceItems(items: List<ReadableItemEntity>) {
        val replacementIds = items.mapTo(mutableSetOf(), ReadableItemEntity::id)
        itemsState.value = itemsState.value.filterNot { item -> item.id in replacementIds } + items
        observedItemsState.value = observedItemsState.value.filterNot { item -> item.id in replacementIds } + items
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
        observedItemsState.value = observedItemsState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteItemsForSourceTypeByIds(
        sourceType: String,
        itemIds: List<String>,
    ) {
        deletedItemBatchSizes += itemIds.size
        itemsState.value = itemsState.value.filterNot { item ->
            item.sourceType == sourceType && item.id in itemIds
        }
        observedItemsState.value = observedItemsState.value.filterNot { item ->
            item.sourceType == sourceType && item.id in itemIds
        }
    }

    protected override fun deleteLibrariesForSourceType(sourceType: String) {
        librariesState.value = librariesState.value.filterNot { it.sourceType == sourceType }
        observedLibrariesState.value = observedLibrariesState.value.filterNot { it.sourceType == sourceType }
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

    private val gridRootsState = MutableStateFlow<List<LibraryGridRootEntity>>(emptyList())

    override fun getGridRootsForSourceType(sourceType: String): List<LibraryGridRootEntity> =
        gridRootsState.value
            .filter { it.sourceType == sourceType }
            .sortedBy { it.title.lowercase() }

    override fun pagingGridRootsByTitle(sourceType: String): PagingSource<Int, LibraryGridRootEntity> =
        throw UnsupportedOperationException("paging not used in FakeRemoteCatalogDao")

    override fun pagingGridRootsByDateAdded(sourceType: String): PagingSource<Int, LibraryGridRootEntity> =
        throw UnsupportedOperationException("paging not used in FakeRemoteCatalogDao")

    override fun pagingGridRootsByLastRead(sourceType: String): PagingSource<Int, LibraryGridRootEntity> =
        throw UnsupportedOperationException("paging not used in FakeRemoteCatalogDao")

    override fun pagingGridRootsFiltered(
        query: androidx.sqlite.db.SupportSQLiteQuery,
    ): PagingSource<Int, LibraryGridRootEntity> =
        throw UnsupportedOperationException("paging not used in FakeRemoteCatalogDao")

    override fun countGridRoots(): Int = gridRootsState.value.size

    override fun getGridRootIdsByTitle(sourceType: String, limit: Int): List<String> =
        gridRootsState.value
            .filter { it.sourceType == sourceType }
            .sortedBy { it.title.lowercase() }
            .map(LibraryGridRootEntity::id)
            .take(limit)

    override fun getGridRootIdsByDateAdded(sourceType: String, limit: Int): List<String> =
        gridRootsState.value
            .filter { it.sourceType == sourceType }
            .sortedWith(compareByDescending<LibraryGridRootEntity> { it.addedAtEpochMillis ?: 0L }.thenBy { it.title.lowercase() })
            .map(LibraryGridRootEntity::id)
            .take(limit)

    // No reading_progress join in the fake; title order is sufficient for the repository tests.
    override fun getGridRootIdsByLastRead(sourceType: String, limit: Int): List<String> =
        getGridRootIdsByTitle(sourceType, limit)

    protected override fun getLibraryItemsForLibrary(libraryId: String): List<ReadableItemLibraryEntity> =
        itemsState.value.filter { it.libraryId == libraryId }.map(ReadableItemEntity::toLibraryEntity)

    override fun insertOrReplaceGridRoots(roots: List<LibraryGridRootEntity>) {
        val replacementIds = roots.mapTo(mutableSetOf(), LibraryGridRootEntity::id)
        gridRootsState.value = gridRootsState.value.filterNot { it.id in replacementIds } + roots
    }

    protected override fun deleteGridRootsForSourceType(sourceType: String) {
        gridRootsState.value = gridRootsState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteGridRootsForLibrary(libraryId: String) {
        gridRootsState.value = gridRootsState.value.filterNot { it.libraryId == libraryId }
    }

    private val rootFacetsState = MutableStateFlow<List<LibraryRootFacetEntity>>(emptyList())

    override fun getDistinctFacetValues(sourceType: String, dimension: String): List<String> =
        rootFacetsState.value
            .filter { it.sourceType == sourceType && it.dimension == dimension }
            .map { it.value }
            .distinct()

    override fun insertOrReplaceRootFacets(facets: List<LibraryRootFacetEntity>) {
        val replacementKeys = facets.mapTo(mutableSetOf()) { Triple(it.rootId, it.dimension, it.value) }
        rootFacetsState.value = rootFacetsState.value
            .filterNot { Triple(it.rootId, it.dimension, it.value) in replacementKeys } + facets
    }

    protected override fun deleteRootFacetsForSourceType(sourceType: String) {
        rootFacetsState.value = rootFacetsState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteRootFacetsForLibrary(libraryId: String) {
        rootFacetsState.value = rootFacetsState.value.filterNot { it.libraryId == libraryId }
    }

    private val rootMembersState = MutableStateFlow<List<LibraryRootMemberEntity>>(emptyList())

    override fun getMemberItemIds(rootId: String): List<String> =
        rootMembersState.value.filter { it.rootId == rootId }.map { it.itemId }

    override fun insertOrReplaceRootMembers(members: List<LibraryRootMemberEntity>) {
        val replacementKeys = members.mapTo(mutableSetOf()) { it.rootId to it.itemId }
        rootMembersState.value = rootMembersState.value
            .filterNot { (it.rootId to it.itemId) in replacementKeys } + members
    }

    protected override fun deleteRootMembersForSourceType(sourceType: String) {
        rootMembersState.value = rootMembersState.value.filterNot { it.sourceType == sourceType }
    }

    protected override fun deleteRootMembersForLibrary(libraryId: String) {
        rootMembersState.value = rootMembersState.value.filterNot { it.libraryId == libraryId }
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
