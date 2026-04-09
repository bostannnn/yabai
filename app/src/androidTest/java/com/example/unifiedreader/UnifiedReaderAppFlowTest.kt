package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import com.example.unifiedreader.feature.reader.comics.ComicsSpreadModeKey
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicsReaderSessionAdapter
import com.example.unifiedreader.feature.reader.comics.comicsReaderFactory
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnifiedReaderAppFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun import_opens_reader_and_reopens_on_last_page() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore(
            initialSessions = mapOf(
                importedItem.id to ReaderSessionSnapshot(
                    itemId = importedItem.id,
                    preferences = mapOf(
                        ComicsSpreadModeKey to ComicSpreadMode.Single.name,
                    ),
                ),
            ),
        )
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val pageSource = FakeComicPageSource(pageCount = 3)
        val localDocumentRepository = FakeLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = createViewModel(
            localDocumentRepository = localDocumentRepository,
            pageSource = pageSource,
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
            readerFactories = listOf(
                comicsReaderFactory(
                    pageSource = pageSource,
                    progressStore = comicsSessionAdapter,
                    preferencesStore = comicsSessionAdapter,
                ),
            ),
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {
                    importDocumentIntoApp(
                        contentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver,
                        viewModel = viewModel,
                        uri = importUri,
                    )
                },
            )
        }

        composeRule.onNodeWithContentDescription("Import local file").performClick()
        composeRule.waitForText("1 / 3")

        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-0"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("2 / 3")

        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Imported Akira")

        composeRule.onNodeWithText("Imported Akira").performClick()
        composeRule.waitForText("2 / 3")
    }

    @Test
    fun imported_item_can_be_removed_from_overflow_menu() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val pageSource = FakeComicPageSource(pageCount = 3)
        val localDocumentRepository = FakeLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = createViewModel(
            localDocumentRepository = localDocumentRepository,
            pageSource = pageSource,
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
            readerFactories = listOf(
                comicsReaderFactory(
                    pageSource = pageSource,
                    progressStore = comicsSessionAdapter,
                    preferencesStore = comicsSessionAdapter,
                ),
            ),
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {
                    importDocumentIntoApp(
                        contentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver,
                        viewModel = viewModel,
                        uri = importUri,
                    )
                },
            )
        }

        composeRule.onNodeWithContentDescription("Import local file").performClick()
        composeRule.waitForText("1 / 3")
        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Imported Akira")
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Remove local file").performClick()
        composeRule.onNodeWithText("Remove").performClick()

        composeRule.waitForTextGone("Imported Akira")
        composeRule.waitForTextGone("Removed Imported Akira.")
    }

    @Test
    fun library_search_filters_cyrillic_catalog_items_from_toolbar() {
        val russianBook = ReadableItem(
            id = "russian-book",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            remoteId = "russian-book",
            title = "Некрополи Петербурга",
            format = FormatType.EPUB,
            coverUrl = null,
        )
        val englishBook = ReadableItem(
            id = "english-book",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            remoteId = "english-book",
            title = "Remote Book",
            format = FormatType.EPUB,
            coverUrl = null,
        )
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val viewModel = createViewModel(
            readableItemRepository = FakeReadableItemRepository(
                items = listOf(russianBook, englishBook),
            ),
            localDocumentRepository = FakeLocalDocumentRepository(
                importedItem = englishBook.copy(
                    id = "unused-import",
                    sourceType = SourceType.LOCAL,
                    isLocalImport = true,
                ),
                importUri = Uri.parse("content://imports/unused.cbz"),
                pageCount = 1,
            ),
            comicPageSource = FakeComicPageSource(pageCount = 1),
            readerSessionStore = readerSessionStore,
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Некрополи Петербурга")
        composeRule.waitForText("Remote Book")

        composeRule.onNodeWithTag("library-search-field").performTextInput("петербурга")

        composeRule.waitForText("Некрополи Петербурга")
        composeRule.waitForTextGone("Remote Book")
    }

    @Test
    fun library_error_messages_render() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val localDocumentRepository = FakeLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = createViewModel(
            localDocumentRepository = localDocumentRepository,
            comicPageSource = FakeComicPageSource(pageCount = 3),
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {
                    importDocumentIntoApp(
                        contentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver,
                        viewModel = viewModel,
                        uri = Uri.parse("content://imports/bad-file.bin"),
                    )
                },
            )
        }

        composeRule.onNodeWithContentDescription("Import local file").performClick()
        composeRule.waitForText("Unable to import that file as a supported local document.")
    }

    @Test
    fun library_view_mode_buttons_switch_between_grid_and_detailed_cards() {
        val item = ReadableItem(
            id = "catalog-book",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            remoteId = "catalog-book",
            title = "Catalog Book",
            format = FormatType.EPUB,
            coverUrl = null,
        )
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val viewModel = createViewModel(
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            localDocumentRepository = FakeLocalDocumentRepository(
                importedItem = item.copy(
                    id = "unused-import",
                    sourceType = SourceType.LOCAL,
                    isLocalImport = true,
                ),
                importUri = Uri.parse("content://imports/unused.cbz"),
                pageCount = 1,
            ),
            comicPageSource = FakeComicPageSource(pageCount = 1),
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {},
            )
        }

        composeRule.onNodeWithTag("library-grid-card-catalog-book").fetchSemanticsNode()
        composeRule.waitForTextGone("Catalog Book")

        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Catalog Book")

        composeRule.onNodeWithContentDescription("Grid view").performClick()
        composeRule.onNodeWithTag("library-grid-card-catalog-book").fetchSemanticsNode()
        composeRule.waitForTextGone("Catalog Book")
    }
}

private fun createViewModel(
    localDocumentRepository: FakeLocalDocumentRepository = FakeLocalDocumentRepository(),
    readableItemRepository: FakeReadableItemRepository = FakeReadableItemRepository(),
    libraryRepository: FakeLibraryRepository = FakeLibraryRepository(),
    comicPageSource: ComicPageSource = FakeComicPageSource(pageCount = 1),
    pageSource: ComicPageSource = comicPageSource,
    progressStore: com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore,
    preferencesStore: com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore,
    appPreferencesStore: AppPreferencesStore = FakeAppPreferencesStore(),
    readerSessionStore: FakeReaderSessionStore = FakeReaderSessionStore(),
    remoteCatalogReadiness: RemoteCatalogReadiness = FakeRemoteCatalogReadiness(),
    kavitaCatalogSyncer: KavitaCatalogSyncer = FakeKavitaCatalogSyncer(),
    kavitaCredentialStore: KavitaCredentialStore = FakeKavitaCredentialStore(),
    grimmoryCatalogSyncer: GrimmoryCatalogSyncer = FakeGrimmoryCatalogSyncer(),
    grimmoryCredentialStore: GrimmoryCredentialStore = FakeGrimmoryCredentialStore(),
    grimmoryCoverRepository: GrimmoryCoverDataSource = FakeGrimmoryCoverRepository(),
    grimmoryContentRepository: GrimmoryContentDataSource = FakeGrimmoryContentRepository(),
    sourceProgressSyncs: List<SourceProgressSync> = emptyList(),
    readerFactories: List<com.example.unifiedreader.feature.reader.core.ReaderScreenFactory> = emptyList(),
): AppViewModel {
    val libraryStateAssembler = AppLibraryStateAssembler(
        localDocumentRepository = localDocumentRepository,
        grimmoryContentRepository = grimmoryContentRepository,
    )
    val stumpCatalogSyncer = object : StumpCatalogSyncer {
        override suspend fun connect(
            request: StumpCatalogSyncRequest,
        ): StumpCatalogConnectionResult = StumpCatalogConnectionResult(libraryCount = 0)

        override suspend fun sync(
            request: StumpCatalogSyncRequest,
            onProgress: (StumpSyncProgress) -> Unit,
        ): StumpCatalogSyncResult = StumpCatalogSyncResult(
            libraryCount = 0,
            itemCount = 0,
            supportedFormatCounts = emptyMap(),
        )
    }
    val stumpCredentialStore = object : StumpCredentialStore {
        private val sessionState = MutableStateFlow<StumpStoredSession?>(null)
        override val sessionFlow: StateFlow<StumpStoredSession?> = sessionState

        override suspend fun save(session: StumpStoredSession) {
            sessionState.value = session
        }

        override suspend fun clear() {
            sessionState.value = null
        }
    }
    val stumpCoverRepository = object : StumpCoverDataSource {
        override suspend fun getCoverUri(item: ReadableItem): Uri? = null

        override suspend fun clearCachedCovers() = Unit
    }
    val stumpCoverCacheController = StumpCoverCacheController(
        stumpCoverRepository = stumpCoverRepository,
    )
    val grimmoryCoverCacheController = GrimmoryCoverCacheController(
        grimmoryCoverRepository = grimmoryCoverRepository,
    )
    val comicPageSourceCacheController = object : ComicPageSourceCacheController {
        override fun clearSourcePageCache(sourceType: SourceType) = Unit
    }
    return AppViewModel(
        appPreferencesStore = appPreferencesStore,
        catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = libraryRepository,
            readableItemRepository = readableItemRepository,
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                appPreferencesStore = appPreferencesStore,
                libraryRepository = libraryRepository,
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = remoteCatalogReadiness,
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
            ),
            readerSessionStore = readerSessionStore,
            stumpCoverCacheController = stumpCoverCacheController,
            grimmoryCoverCacheController = grimmoryCoverCacheController,
        ),
        libraryActionsCoordinator = AppLibraryActionsCoordinator(
            importDocumentUseCase = ImportDocumentUseCase(
                localDocumentRepository = localDocumentRepository,
            ),
            deleteImportedItemUseCase = DeleteImportedItemUseCase(
                importedItemDeletionService = DefaultImportedItemDeletionService(
                    localDocumentDataSource = localDocumentRepository,
                    readerSessionStore = readerSessionStore,
                ),
            ),
            downloadRemoteItemUseCase = DownloadRemoteItemUseCase(
                grimmoryContentRepository = grimmoryContentRepository,
                libraryStateAssembler = libraryStateAssembler,
                sourceProgressSyncs = sourceProgressSyncs,
            ),
            deleteRemoteLocalFileUseCase = DeleteRemoteLocalFileUseCase(
                grimmoryContentRepository = grimmoryContentRepository,
                libraryStateAssembler = libraryStateAssembler,
            ),
            stumpCoverCacheController = stumpCoverCacheController,
            grimmoryCoverCacheController = grimmoryCoverCacheController,
        ),
        readerCoordinator = AppReaderCoordinator(
            libraryStateAssembler = libraryStateAssembler,
            readerSessionStore = readerSessionStore,
            sourceProgressSyncs = sourceProgressSyncs,
        ),
        remoteSourcesCoordinator = AppRemoteSourcesCoordinator(
            kavitaCoordinator = AppKavitaCoordinator(
                kavitaCatalogSyncer = kavitaCatalogSyncer,
                kavitaCredentialStore = kavitaCredentialStore,
            ),
            stumpCoordinator = AppStumpCoordinator(
                stumpCatalogSyncer = stumpCatalogSyncer,
                stumpCredentialStore = stumpCredentialStore,
                stumpCoverRepository = stumpCoverRepository,
            ),
            grimmoryCoordinator = AppGrimmoryCoordinator(
                grimmoryCatalogSyncer = grimmoryCatalogSyncer,
                grimmoryCredentialStore = grimmoryCredentialStore,
                grimmoryCoverRepository = grimmoryCoverRepository,
            ),
            kavitaCredentialStore = kavitaCredentialStore,
            stumpCredentialStore = stumpCredentialStore,
            grimmoryCredentialStore = grimmoryCredentialStore,
            stumpCoverCacheController = stumpCoverCacheController,
            grimmoryCoverCacheController = grimmoryCoverCacheController,
            comicPageSourceCacheController = comicPageSourceCacheController,
        ),
        comicPageSource = pageSource,
        comicReaderProgressStore = progressStore,
        comicReaderPreferencesStore = preferencesStore,
        readerSessionStore = readerSessionStore,
        readerFactories = readerFactories,
    )
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTextGone(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
}

private fun comicGestureSurfaceTag(
    spreadId: String,
): String = "comic-gesture-surface-$spreadId"

private class FakeLibraryRepository : LibraryRepository {
    private val librariesState = MutableStateFlow(
        listOf(
            Library(
                id = "k-comics",
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
            ),
        ),
    )

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState
}

private class FakeReadableItemRepository(
    items: List<ReadableItem> = emptyList(),
) : ReadableItemRepository {
    private val itemsState = MutableStateFlow(items)

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState
}

private class FakeGrimmoryCatalogSyncer : GrimmoryCatalogSyncer {
    override suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogSyncResult = GrimmoryCatalogSyncResult(
        libraryCount = 0,
        itemCount = 0,
        supportedFormatCounts = emptyMap(),
    )
}

private class FakeKavitaCatalogSyncer : KavitaCatalogSyncer {
    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult = KavitaCatalogConnectionResult(
        libraryCount = 0,
    )

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult = KavitaCatalogSyncResult(
        libraryCount = 0,
        itemCount = 0,
        supportedFormatCounts = emptyMap(),
    )
}

private class FakeRemoteCatalogReadiness : RemoteCatalogReadiness {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
}

private class FakeKavitaCredentialStore : KavitaCredentialStore {
    private val sessionState = MutableStateFlow<KavitaStoredSession?>(null)

    override val sessionFlow: StateFlow<KavitaStoredSession?> = sessionState

    override suspend fun save(
        session: KavitaStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

private class FakeGrimmoryCredentialStore : GrimmoryCredentialStore {
    private val sessionState = MutableStateFlow<GrimmoryStoredSession?>(null)

    override val sessionFlow: StateFlow<GrimmoryStoredSession?> = sessionState

    override suspend fun save(
        session: GrimmoryStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

private class FakeGrimmoryCoverRepository : GrimmoryCoverDataSource {
    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun clearCachedCovers() = Unit
}

private class FakeGrimmoryContentRepository : GrimmoryContentDataSource {
    override suspend fun getDocumentUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = emptySet()

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}

private class FakeAppPreferencesStore : AppPreferencesStore {
    private val state = MutableStateFlow(AppPreferences(themeMode = ThemeMode.Light))

    override val preferencesFlow: StateFlow<AppPreferences> = state

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        state.value = AppPreferences(themeMode = themeMode)
    }
}

private class FakeLocalDocumentRepository(
    private val importedItem: ReadableItem? = null,
    private val importUri: Uri? = null,
    private val pageCount: Int = 0,
) : LocalDocumentDataSource {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val importedItemsState = MutableStateFlow(emptyList<ReadableItem>())
    override val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState

    override fun getImportedItems(): List<ReadableItem> = importedItemsState.value

    override suspend fun import(uri: Uri): ReadableItem? {
        val item = importedItem ?: return null
        if (uri != importUri) return null
        importedItemsState.value = listOf(item)
        return item
    }

    override suspend fun removeById(itemId: String): Boolean {
        if (itemId != importedItem?.id) return false
        importedItemsState.value = emptyList()
        return true
    }

    override fun getPageCount(item: ReadableItem): Int = if (item.id == importedItem?.id) pageCount else 0

    override fun getDocumentUri(item: ReadableItem): Uri? = if (item.id == importedItem?.id) importUri else null
}

private class FakeComicPageSource(
    private val pageCount: Int,
) : ComicPageSource {
    private val imagesById = List(pageCount) { index ->
        "page-$index" to Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(
                when (index) {
                    0 -> android.graphics.Color.RED
                    1 -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.BLUE
                },
            )
        }.asImageBitmap()
    }.toMap()

    override fun getPages(item: ReadableItem): List<ComicPageSpec> = List(pageCount) { index ->
        ComicPageSpec(
            id = "page-$index",
            pageIndex = index,
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(
        image = imagesById.getValue(page.id),
    )
}

private class FakeReaderSessionStore(
    initialSessions: Map<String, ReaderSessionSnapshot> = emptyMap(),
) : ReaderSessionStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState
    private val sessionState = MutableStateFlow(initialSessions)
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressState.value = progressState.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override fun updateSession(
        item: ReadableItem,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionState.value[item.id] ?: ReaderSessionSnapshot(itemId = item.id)
        sessionState.value = sessionState.value + (
            item.id to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressState.value = progressState.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionState.value = sessionState.value - itemId
    }
}
