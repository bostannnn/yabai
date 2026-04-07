package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesSnapshot
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {
    @Test
    fun `on item selected opens reader and back returns to library`() {
        val sampleItem = sampleItem(format = FormatType.EPUB)
        val viewModel = createViewModel(
            sampleItems = listOf(sampleItem),
        )

        viewModel.onItemSelected(sampleItem)
        assertEquals(sampleItem.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.EPUB, viewModel.uiState.activeReader)

        viewModel.onBackPressed()
        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
    }

    @Test
    fun `import success adds item opens reader and sets success status`() = runBlocking {
        val importedItem = sampleItem(
            id = "imported-cbz",
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 12 pages.",
            isLocalImport = true,
        )
        val importedRepository = FakeLocalDocumentRepository(
            importResult = importedItem,
            pageCounts = mutableMapOf(importedItem.id to 12),
        )
        val viewModel = createViewModel(
            importedRepository = importedRepository,
        )

        viewModel.importDocument(Uri.parse("content://imports/akira.cbz"))

        assertEquals(importedItem.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.CBZ, viewModel.uiState.activeReader)
        assertEquals(SourceType.LOCAL, viewModel.uiState.selectedSource)
        assertEquals("Imported Imported Akira (CBZ)", viewModel.uiState.libraryMessage)
        assertEquals(listOf(importedItem), viewModel.uiState.items)
    }

    @Test
    fun `import failure leaves selection closed and sets error status`() = runBlocking {
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(importResult = null),
        )

        viewModel.importDocument(Uri.parse("content://imports/bad-file.bin"))

        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
        assertEquals("Unable to import that file as a supported local document.", viewModel.uiState.libraryMessage)
    }

    @Test
    fun `import failure from settings returns to library to show error status`() = runBlocking {
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(importResult = null),
        )
        viewModel.onTopLevelSelected(TopLevelDestination.Settings)

        viewModel.importDocument(Uri.parse("content://imports/bad-file.bin"))

        assertEquals(TopLevelDestination.Library, viewModel.uiState.topLevel)
        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
        assertEquals("Unable to import that file as a supported local document.", viewModel.uiState.libraryMessage)
    }

    @Test
    fun `imported epub opens local reader and sets success status`() = runBlocking {
        val importedItem = ReadableItem(
            id = "document-abcd",
            serverId = "local-device",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "The Dispossessed",
            format = FormatType.EPUB,
            description = "Imported local EPUB book.",
            coverUrl = null,
        )
        val importedRepository = FakeLocalDocumentRepository(
            importResult = importedItem,
            importedDocumentUris = mutableMapOf(
                importedItem.id to Uri.parse("content://imports/the-dispossessed.epub"),
            ),
        )
        val viewModel = createViewModel(
            importedRepository = importedRepository,
        )

        viewModel.importDocument(Uri.parse("content://imports/the-dispossessed.epub"))

        assertEquals(importedItem.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.EPUB, viewModel.uiState.activeReader)
        assertEquals(SourceType.LOCAL, viewModel.uiState.selectedSource)
        assertEquals(SourceType.LOCAL, viewModel.uiState.selectedItem?.sourceType)
        assertEquals("Imported The Dispossessed (EPUB)", viewModel.uiState.libraryMessage)
    }

    @Test
    fun `import success uses comic metadata label in library message`() = runBlocking {
        val importedItem = sampleItem(
            id = "imported-carmilla",
            title = "Carmilla 03 (2025) (Collection) (Dark Horse Comics) (Digital-HD) (LeDuch)",
            format = FormatType.CBZ,
            isLocalImport = true,
        ).copy(
            comicMetadata = ComicMetadata(
                series = "Carmilla: The Eternal",
                volume = "2025",
                issueNumber = "3",
            ),
        )
        val importedRepository = FakeLocalDocumentRepository(
            importResult = importedItem,
            pageCounts = mutableMapOf(importedItem.id to 112),
        )
        val viewModel = createViewModel(
            importedRepository = importedRepository,
        )

        viewModel.importDocument(Uri.parse("content://imports/carmilla-03.cbz"))

        assertEquals("Imported Carmilla: The Eternal (CBZ)", viewModel.uiState.libraryMessage)
    }

    @Test
    fun `refresh progress rebuilds summaries from stored progress`() {
        val epubItem = sampleItem(
            id = "epub-1",
            title = "Left Hand",
            format = FormatType.EPUB,
        )
        val cbzItem = sampleItem(
            id = "cbz-1",
            title = "Akira",
            format = FormatType.CBZ,
        )
        val progressStore = FakeComicReaderProgressStore().apply {
            savePage(cbzItem, pageIndex = 3, pageCount = 12)
        }
        val importedRepository = FakeLocalDocumentRepository(
            importedItems = mutableListOf(cbzItem),
            pageCounts = mutableMapOf(cbzItem.id to 12),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = epubItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 1,
                ),
                pageCount = 4,
            )
            saveProgress(
                item = cbzItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 3,
                ),
                pageCount = 12,
            )
        }
        val pageSource = FakeComicPageSource(
            pageCounts = mapOf(epubItem.id to 4),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            importedRepository = importedRepository,
            pageSource = pageSource,
            progressStore = progressStore,
            readerSessionStore = readerSessionStore,
        )

        viewModel.refreshProgress()

        assertEquals("Resume on page 2 of 4", viewModel.uiState.progressByItemId[epubItem.id]?.label)
        assertEquals("Resume on page 4 of 12", viewModel.uiState.progressByItemId[cbzItem.id]?.label)
    }

    @Test
    fun `refresh progress uses session page count for epub page locator`() {
        val epubItem = sampleItem(
            id = "epub-1",
            title = "Left Hand",
            format = FormatType.EPUB,
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = epubItem.id,
                    locatorType = LocatorType.PAGE,
                    page = 2,
                    syncState = SyncState.CLEAN,
                ),
            )
            seedSession(
                com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(
                    itemId = epubItem.id,
                    pageCount = 10,
                ),
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            readerSessionStore = readerSessionStore,
            pageSource = FakeComicPageSource(),
        )

        viewModel.refreshProgress()

        assertEquals("Resume on page 3 of 10", viewModel.uiState.progressByItemId[epubItem.id]?.label)
    }

    @Test
    fun `refresh progress shows percent summary from generic reader session store`() {
        val epubItem = sampleItem(
            id = "epub-2",
            title = "Dune",
            format = FormatType.EPUB,
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = epubItem.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.42,
                    syncState = SyncState.CLEAN,
                ),
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            readerSessionStore = readerSessionStore,
        )

        viewModel.refreshProgress()

        assertEquals("Resume at 42%", viewModel.uiState.progressByItemId[epubItem.id]?.label)
    }

    @Test
    fun `refresh progress keeps epub page count for percent progress`() {
        val epubItem = sampleItem(
            id = "epub-3",
            title = "Hyperion",
            format = FormatType.EPUB,
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = epubItem.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.42,
                    syncState = SyncState.CLEAN,
                ),
            )
            seedSession(
                com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(
                    itemId = epubItem.id,
                    pageCount = 19,
                ),
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            readerSessionStore = readerSessionStore,
        )

        viewModel.refreshProgress()

        assertEquals(19, viewModel.uiState.progressByItemId[epubItem.id]?.pageCount)
    }

    @Test
    fun `repository flow updates refresh ui state`() {
        val itemRepository = FakeReadableItemRepository()
        val libraryRepository = FakeLibraryRepository()
        val viewModel = createViewModel(
            libraryRepository = libraryRepository,
            readableItemRepository = itemRepository,
        )

        val updatedLibrary = Library(
            id = "g-books",
            serverId = "local-grimmory",
            sourceType = SourceType.GRIMMORY,
            title = "Books",
        )
        val updatedItem = sampleItem(
            id = "epub-live",
            title = "Live Catalog Item",
            format = FormatType.EPUB,
            description = "Added through repository flow.",
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = "g-books",
            serverId = "local-grimmory",
        )

        libraryRepository.emitLibraries(listOf(updatedLibrary))
        itemRepository.emitItems(listOf(updatedItem))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(updatedLibrary), viewModel.uiState.libraries)
        assertEquals(listOf(updatedItem), viewModel.uiState.items)
    }

    @Test
    fun `theme mode selection updates ui state`() {
        val preferencesStore = FakeAppPreferencesStore()
        val viewModel = createViewModel(
            appPreferencesStore = preferencesStore,
        )

        viewModel.onThemeModeSelected(ThemeMode.Dark)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)
        assertEquals(ThemeMode.Dark, preferencesStore.preferencesFlow.value.themeMode)
    }

    @Test
    fun `delete imported item removes it clears selection and clears reader state`() = runBlocking {
        val importedItem = sampleItem(
            id = "imported-cbz",
            title = "Imported Akira",
            format = FormatType.CBZ,
            isLocalImport = true,
        )
        val importedRepository = FakeLocalDocumentRepository(
            importedItems = mutableListOf(importedItem),
            pageCounts = mutableMapOf(importedItem.id to 12),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = importedItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 3,
                ),
                pageCount = 12,
            )
        }
        val viewModel = createViewModel(
            importedRepository = importedRepository,
            readerSessionStore = readerSessionStore,
        )
        viewModel.onItemSelected(importedItem)

        viewModel.deleteImportedItem(importedItem)

        assertTrue(viewModel.uiState.items.none { it.id == importedItem.id })
        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
        assertEquals("Removed Imported Akira.", viewModel.uiState.libraryMessage)
        assertNull(readerSessionStore.getProgress(importedItem))
        assertNull(readerSessionStore.getSession(importedItem))
    }

    private fun createViewModel(
        sampleItems: List<ReadableItem> = emptyList(),
        importedRepository: FakeLocalDocumentRepository = FakeLocalDocumentRepository(),
        pageSource: ComicPageSource = FakeComicPageSource(),
        progressStore: ComicReaderProgressStore = FakeComicReaderProgressStore(),
        preferencesStore: ComicReaderPreferencesStore = FakeComicReaderPreferencesStore(),
        appPreferencesStore: AppPreferencesStore = FakeAppPreferencesStore(),
        readerSessionStore: FakeReaderSessionStore = FakeReaderSessionStore(),
        libraryRepository: FakeLibraryRepository = FakeLibraryRepository(
            libraries = listOf(
                Library(
                    id = "k-comics",
                    serverId = "local-kavita",
                    sourceType = SourceType.KAVITA,
                    title = "Comics",
                ),
            ),
        ),
        readableItemRepository: FakeReadableItemRepository = FakeReadableItemRepository(items = sampleItems),
    ): AppViewModel = AppViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        appPreferencesStore = appPreferencesStore,
        libraryRepository = libraryRepository,
        readableItemRepository = readableItemRepository,
        localDocumentRepository = importedRepository,
        importedItemDeletionService = DefaultImportedItemDeletionService(
            localDocumentDataSource = importedRepository,
            readerSessionStore = readerSessionStore,
        ),
        comicPageSource = pageSource,
        comicReaderProgressStore = progressStore,
        comicReaderPreferencesStore = preferencesStore,
        readerSessionStore = readerSessionStore,
        readerFactories = emptyList(),
    )
}

private class FakeLibraryRepository(
    private val libraries: List<Library> = emptyList(),
) : LibraryRepository {
    private val librariesState = MutableStateFlow(libraries)

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState

    fun emitLibraries(libraries: List<Library>) {
        librariesState.value = libraries
    }
}

private class FakeReadableItemRepository(
    private val items: List<ReadableItem> = emptyList(),
) : ReadableItemRepository {
    private val itemsState = MutableStateFlow(items)

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState

    fun emitItems(items: List<ReadableItem>) {
        itemsState.value = items
    }
}

private class FakeLocalDocumentRepository(
    private var importResult: ReadableItem? = null,
    private val importedItems: MutableList<ReadableItem> = mutableListOf(),
    val pageCounts: MutableMap<String, Int> = mutableMapOf(),
    val importedDocumentUris: MutableMap<String, Uri> = mutableMapOf(),
) : LocalDocumentDataSource {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val importedItemsState = MutableStateFlow(importedItems.toList())
    override val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState

    override fun getImportedItems(): List<ReadableItem> = importedItems.toList()

    override suspend fun import(uri: Uri): ReadableItem? = importResult?.also { item ->
        importedItems.removeAll { existing -> existing.id == item.id }
        importedItems += item
        importedDocumentUris[item.id] = uri
        importedItemsState.value = importedItems.toList()
    }

    override suspend fun removeById(itemId: String): Boolean {
        val removed = importedItems.removeAll { existing -> existing.id == itemId }
        if (!removed) return false
        pageCounts.remove(itemId)
        importedDocumentUris.remove(itemId)
        importedItemsState.value = importedItems.toList()
        return true
    }

    override fun getPageCount(item: ReadableItem): Int = pageCounts[item.id] ?: 0

    override fun getDocumentUri(item: ReadableItem): Uri? = importedDocumentUris[item.id]
}

private class FakeComicPageSource(
    private val pageCounts: Map<String, Int> = emptyMap(),
) : ComicPageSource {
    override fun getPages(item: ReadableItem): List<ComicPageSpec> = List(pageCounts[item.id] ?: 0) { index ->
        ComicPageSpec(
            id = "${item.id}-$index",
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult = ComicPageLoadResult.Failure("Not needed for AppViewModel tests.")
}

private class FakeComicReaderProgressStore : ComicReaderProgressStore {
    private val pagesByItemId = mutableMapOf<String, Int>()
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPagesFlow: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())

    override fun loadPage(item: ReadableItem): Int? = pagesByItemId[item.id]

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
    ) {
        val safePage = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pagesByItemId[item.id] = safePage
        (savedPagesFlow as MutableStateFlow).value = pagesByItemId.toMap()
    }
}

private class FakeComicReaderPreferencesStore : ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        MutableStateFlow(emptyMap())
}

private class FakeAppPreferencesStore(
    initialPreferences: AppPreferences = AppPreferences(),
) : AppPreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<AppPreferences> = mutablePreferencesFlow

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        mutablePreferencesFlow.value = AppPreferences(themeMode = themeMode)
    }
}

private class FakeReaderSessionStore : ReaderSessionStore {
    private val progressByItemId = MutableStateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>>(emptyMap())
    private val sessionByItemId = MutableStateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>>(emptyMap())
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>> =
        progressByItemId
    override val sessionByItemIdFlow: StateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>> =
        sessionByItemId

    override fun getProgress(item: ReadableItem) = progressByItemId.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: com.example.unifiedreader.feature.reader.core.ReaderLocator,
        isFinished: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
    ) {
        progressByItemId.value = progressByItemId.value + (
            item.id to com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                syncState = syncState,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem) = sessionByItemId.value[item.id]

    override fun updateSession(
        item: ReadableItem,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionByItemId.value[item.id]
            ?: com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(itemId = item.id)
        sessionByItemId.value = sessionByItemId.value + (
            item.id to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearItem(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
        sessionByItemId.value = sessionByItemId.value - itemId
    }

    fun seedProgress(
        snapshot: com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot,
    ) {
        progressByItemId.value = progressByItemId.value + (snapshot.itemId to snapshot)
    }

    fun seedSession(
        snapshot: com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot,
    ) {
        sessionByItemId.value = sessionByItemId.value + (snapshot.itemId to snapshot)
    }
}

private fun sampleItem(
    id: String = "sample-item",
    title: String = "Sample Item",
    format: FormatType,
    description: String? = null,
    isLocalImport: Boolean = false,
): ReadableItem = ReadableItem(
    id = id,
    serverId = if (isLocalImport) "local-device" else "local-kavita",
    sourceType = if (isLocalImport) SourceType.LOCAL else SourceType.KAVITA,
    libraryId = if (isLocalImport) "local-imports" else "k-comics",
    isLocalImport = isLocalImport,
    title = title,
    format = format,
    description = description,
    coverUrl = null,
)
