package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesSnapshot
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun createViewModel(
    sampleItems: List<ReadableItem> = emptyList(),
    importedRepository: FakeLocalDocumentRepository = FakeLocalDocumentRepository(),
    pageSource: ComicPageSource = FakeComicPageSource(),
    progressStore: ComicReaderProgressStore = FakeComicReaderProgressStore(),
    preferencesStore: ComicReaderPreferencesStore = FakeComicReaderPreferencesStore(),
    appPreferencesStore: AppPreferencesStore = FakeAppPreferencesStore(),
    readerSessionStore: FakeReaderSessionStore = FakeReaderSessionStore(),
    kavitaCatalogSyncer: KavitaCatalogSyncer = FakeKavitaCatalogSyncer(),
    kavitaCredentialStore: KavitaCredentialStore = FakeKavitaCredentialStore(),
    stumpCatalogSyncer: StumpCatalogSyncer = FakeStumpCatalogSyncer(),
    stumpCredentialStore: StumpCredentialStore = FakeStumpCredentialStore(),
    stumpCoverRepository: StumpCoverDataSource = FakeStumpCoverRepository(),
    grimmoryCatalogSyncer: GrimmoryCatalogSyncer = FakeGrimmoryCatalogSyncer(),
    grimmoryCredentialStore: GrimmoryCredentialStore = FakeGrimmoryCredentialStore(),
    grimmoryCoverRepository: GrimmoryCoverDataSource = FakeGrimmoryCoverRepository(),
    grimmoryContentRepository: GrimmoryContentDataSource = FakeGrimmoryContentRepository(),
    comicPageSourceCacheController: ComicPageSourceCacheController = FakeComicPageSourceCacheController(),
    remoteCatalogReadiness: RemoteCatalogReadiness = FakeRemoteCatalogReadiness(),
    sourceProgressSyncs: List<SourceProgressSync> = emptyList(),
    libraryRepository: FakeLibraryRepository = FakeLibraryRepository(
        libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
            ),
        ),
    ),
    readableItemRepository: FakeReadableItemRepository = FakeReadableItemRepository(items = sampleItems),
): AppViewModel {
    val libraryStateAssembler = AppLibraryStateAssembler(
        localDocumentRepository = importedRepository,
        grimmoryContentRepository = grimmoryContentRepository,
    )
    val stumpCoverCacheController = StumpCoverCacheController(
        stumpCoverRepository = stumpCoverRepository,
    )
    val grimmoryCoverCacheController = GrimmoryCoverCacheController(
        grimmoryCoverRepository = grimmoryCoverRepository,
    )
    return AppViewModel(
        appPreferencesStore = appPreferencesStore,
        appStateHydrator = AppStateHydrator(
            localDocumentHydrator = importedRepository,
            readerSessionHydrator = readerSessionStore,
        ),
        catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = libraryRepository,
            readableItemRepository = readableItemRepository,
            localDocumentRepository = importedRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                appPreferencesStore = appPreferencesStore,
                libraryRepository = libraryRepository,
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = remoteCatalogReadiness,
                localDocumentRepository = importedRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
            ),
            readerSessionStore = readerSessionStore,
            stumpCoverCacheController = stumpCoverCacheController,
            grimmoryCoverCacheController = grimmoryCoverCacheController,
        ),
        libraryActionsCoordinator = AppLibraryActionsCoordinator(
            importDocumentUseCase = ImportDocumentUseCase(
                localDocumentRepository = importedRepository,
            ),
            deleteImportedItemUseCase = DeleteImportedItemUseCase(
                importedItemDeletionService = DefaultImportedItemDeletionService(
                    localDocumentDataSource = importedRepository,
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
            kavitaController = AppKavitaRemoteSourceController(
                kavitaCoordinator = AppKavitaCoordinator(
                    kavitaCatalogSyncer = kavitaCatalogSyncer,
                    kavitaCredentialStore = kavitaCredentialStore,
                ),
                kavitaCredentialStore = kavitaCredentialStore,
                comicPageSourceCacheController = comicPageSourceCacheController,
            ),
            stumpController = AppStumpRemoteSourceController(
                stumpCoordinator = AppStumpCoordinator(
                    stumpCatalogSyncer = stumpCatalogSyncer,
                    stumpCredentialStore = stumpCredentialStore,
                    stumpCoverRepository = stumpCoverRepository,
                ),
                stumpCredentialStore = stumpCredentialStore,
                stumpCoverCacheController = stumpCoverCacheController,
                comicPageSourceCacheController = comicPageSourceCacheController,
            ),
            grimmoryController = AppGrimmoryRemoteSourceController(
                grimmoryCoordinator = AppGrimmoryCoordinator(
                    grimmoryCatalogSyncer = grimmoryCatalogSyncer,
                    grimmoryCredentialStore = grimmoryCredentialStore,
                    grimmoryCoverRepository = grimmoryCoverRepository,
                ),
                grimmoryCredentialStore = grimmoryCredentialStore,
                grimmoryCoverCacheController = grimmoryCoverCacheController,
            ),
        ),
        comicPageSource = pageSource,
        comicReaderProgressStore = progressStore,
        comicReaderPreferencesStore = preferencesStore,
        readerSessionStore = readerSessionStore,
        readerFactories = emptyList(),
    )
}

class FakeComicPageSourceCacheController : ComicPageSourceCacheController {
    val clearedSourceTypes = mutableListOf<SourceType>()

    override fun clearSourcePageCache(
        sourceType: SourceType,
    ) {
        clearedSourceTypes += sourceType
    }
}

class FakeKavitaCatalogSyncer(
    private val connectionResult: KavitaCatalogConnectionResult = KavitaCatalogConnectionResult(
        libraryCount = 0,
    ),
    private val result: KavitaCatalogSyncResult = KavitaCatalogSyncResult(
        libraryCount = 0,
        itemCount = 0,
        supportedFormatCounts = emptyMap(),
    ),
    private val failure: Throwable? = null,
) : KavitaCatalogSyncer {
    var lastConnectRequest: KavitaCatalogSyncRequest? = null
    var lastSyncRequest: KavitaCatalogSyncRequest? = null

    override suspend fun connect(
        request: KavitaCatalogSyncRequest,
    ): KavitaCatalogConnectionResult {
        lastConnectRequest = request
        failure?.let { throw it }
        return connectionResult
    }

    override suspend fun sync(
        request: KavitaCatalogSyncRequest,
        onProgress: (KavitaSyncProgress) -> Unit,
    ): KavitaCatalogSyncResult {
        lastSyncRequest = request
        failure?.let { throw it }
        return result
    }
}

class FakeStumpCatalogSyncer(
    private val connectionResult: StumpCatalogConnectionResult = StumpCatalogConnectionResult(
        libraryCount = 0,
    ),
    private val result: StumpCatalogSyncResult = StumpCatalogSyncResult(
        libraryCount = 0,
        itemCount = 0,
        supportedFormatCounts = emptyMap(),
    ),
    private val failure: Throwable? = null,
) : StumpCatalogSyncer {
    var lastConnectRequest: StumpCatalogSyncRequest? = null
    var lastSyncRequest: StumpCatalogSyncRequest? = null

    override suspend fun connect(
        request: StumpCatalogSyncRequest,
    ): StumpCatalogConnectionResult {
        lastConnectRequest = request
        failure?.let { throw it }
        return connectionResult
    }

    override suspend fun sync(
        request: StumpCatalogSyncRequest,
        onProgress: (StumpSyncProgress) -> Unit,
    ): StumpCatalogSyncResult {
        lastSyncRequest = request
        failure?.let { throw it }
        return result
    }
}

class FakeGrimmoryCatalogSyncer(
    private val result: GrimmoryCatalogSyncResult = GrimmoryCatalogSyncResult(
        libraryCount = 0,
        itemCount = 0,
        supportedFormatCounts = emptyMap(),
    ),
    private val failure: Throwable? = null,
) : GrimmoryCatalogSyncer {
    var lastRequest: GrimmoryCatalogSyncRequest? = null

    override suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogSyncResult {
        lastRequest = request
        failure?.let { throw it }
        return result
    }
}

class FakeRemoteCatalogReadiness(
    initialHydrated: Boolean = true,
) : RemoteCatalogReadiness {
    private val hydrationState = MutableStateFlow(initialHydrated)
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState

    fun emit(
        hydrated: Boolean,
    ) {
        hydrationState.value = hydrated
    }
}

class FakeGrimmoryCredentialStore(
    initialSession: GrimmoryStoredSession? = null,
) : GrimmoryCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

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

class FakeKavitaCredentialStore(
    initialSession: KavitaStoredSession? = null,
) : KavitaCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

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

class FakeStumpCredentialStore(
    initialSession: StumpStoredSession? = null,
) : StumpCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<StumpStoredSession?> = sessionState

    override suspend fun save(
        session: StumpStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}

class FakeStumpCoverRepository : StumpCoverDataSource {
    var clearCachedCoversCalls: Int = 0

    constructor(
        coverUri: Uri? = null,
    ) {
        this.coverUri = coverUri
    }

    private var coverUri: Uri? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? = coverUri

    override suspend fun clearCachedCovers() {
        clearCachedCoversCalls += 1
    }
}

class FakeGrimmoryCoverRepository : GrimmoryCoverDataSource {
    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun clearCachedCovers() = Unit
}

class FakeGrimmoryContentRepository(
    private val storedItemIds: MutableSet<String> = mutableSetOf(),
    private val downloadFailure: Throwable? = null,
) : GrimmoryContentDataSource {
    override suspend fun getDocumentUri(
        item: ReadableItem,
    ): Uri? = downloadToLocalStorage(item)

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? {
        downloadFailure?.let { throw it }
        if (item.sourceType != SourceType.GRIMMORY) return null
        storedItemIds += item.id
        return Uri.parse("file:///downloads/${item.id}.${item.format.name.lowercase()}")
    }

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = items
        .filter { item -> item.id in storedItemIds }
        .mapTo(mutableSetOf(), ReadableItem::id)

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = storedItemIds.remove(item.id)
}

class FakeLibraryRepository(
    private val libraries: List<Library> = emptyList(),
) : LibraryRepository {
    private val librariesState = MutableStateFlow(libraries)

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState

    fun emitLibraries(libraries: List<Library>) {
        librariesState.value = libraries
    }
}

class FakeReadableItemRepository(
    private val items: List<ReadableItem> = emptyList(),
) : ReadableItemRepository {
    private val itemsState = MutableStateFlow(items)

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState

    fun emitItems(items: List<ReadableItem>) {
        itemsState.value = items
    }
}

class FakeLocalDocumentRepository(
    private var importResult: ReadableItem? = null,
    private val importedItems: MutableList<ReadableItem> = mutableListOf(),
    val pageCounts: MutableMap<String, Int> = mutableMapOf(),
    val importedDocumentUris: MutableMap<String, Uri> = mutableMapOf(),
) : LocalDocumentDataSource, LocalDocumentHydrator {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val importedItemsState = MutableStateFlow(importedItems.toList())
    override val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState

    override fun getImportedItems(): List<ReadableItem> = importedItems.toList()

    override suspend fun hydrate() = Unit

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

class FakeComicPageSource(
    private val pageCounts: Map<String, Int> = emptyMap(),
) : ComicPageSource {
    override fun getPages(item: ReadableItem): List<ComicPageSpec> = List(pageCounts[item.id] ?: 0) { index ->
        ComicPageSpec(
            id = "${item.id}-$index",
            pageIndex = index,
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = ComicPageLoadResult.Failure("Not needed for AppViewModel tests.")
}

class FakeComicReaderProgressStore : ComicReaderProgressStore {
    private val pagesByItemId = mutableMapOf<String, Int>()
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPagesFlow: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap())

    override fun loadPage(item: ReadableItem): Int? = pagesByItemId[item.id]

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean,
    ) {
        val safePage = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pagesByItemId[item.id] = safePage
        (savedPagesFlow as MutableStateFlow).value = pagesByItemId.toMap()
    }
}

class FakeComicReaderPreferencesStore : ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        MutableStateFlow(emptyMap())
}

class FakeAppPreferencesStore(
    initialPreferences: AppPreferences = AppPreferences(),
) : AppPreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<AppPreferences> = mutablePreferencesFlow

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        mutablePreferencesFlow.value = AppPreferences(themeMode = themeMode)
    }
}

class FakeReaderSessionStore : ReaderSessionStore, ReaderSessionHydrator {
    private val progressByItemId = MutableStateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>>(emptyMap())
    private val sessionByItemId = MutableStateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>>(emptyMap())
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>> =
        progressByItemId
    override val sessionByItemIdFlow: StateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>> =
        sessionByItemId

    override fun getProgress(item: ReadableItem) = progressByItemId.value[item.id]

    override suspend fun hydrate() = Unit

    override fun saveProgress(
        item: ReadableItem,
        locator: com.example.unifiedreader.feature.reader.core.ReaderLocator,
        isFinished: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
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
                updatedAtEpochMillis = updatedAtEpochMillis,
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

    override fun clearProgress(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
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

class BlockingSourceProgressSync(
    override val sourceType: SourceType,
) : SourceProgressSync {
    private val completionGate = CompletableDeferred<Unit>()
    var syncItemCalls: Int = 0
        private set

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        syncItemCalls += 1
        completionGate.await()
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ) = Unit

    fun allowCompletion() {
        completionGate.complete(Unit)
    }
}

class FailingSourceProgressSync(
    override val sourceType: SourceType,
) : SourceProgressSync {
    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        error("Sync unavailable")
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ) = Unit
}

internal fun sampleItem(
    id: String = "sample-item",
    title: String = "Sample Item",
    format: FormatType,
    description: String? = null,
    isLocalImport: Boolean = false,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = if (isLocalImport) SourceType.LOCAL else SourceType.KAVITA,
    libraryId = if (isLocalImport) {
        "local-imports"
    } else {
        SourceType.KAVITA.remoteLibraryId("k-comics")
    },
    remoteId = if (isLocalImport) null else id,
    isLocalImport = isLocalImport,
    title = title,
    format = format,
    description = description,
    coverUrl = null,
)
