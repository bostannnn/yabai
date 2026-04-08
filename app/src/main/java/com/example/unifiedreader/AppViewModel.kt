package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.jvm.JvmSuppressWildcards

@HiltViewModel
class AppViewModel @Inject constructor(
    application: Application,
    private val appPreferencesStore: AppPreferencesStore,
    private val libraryRepository: LibraryRepository,
    private val readableItemRepository: ReadableItemRepository,
    private val localDocumentRepository: LocalDocumentDataSource,
    private val importedItemDeletionService: ImportedItemDeletionService,
    private val grimmoryCoordinator: AppGrimmoryCoordinator,
    private val remoteCatalogReadiness: RemoteCatalogReadiness,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val grimmoryCoverRepository: GrimmoryCoverDataSource,
    private val grimmoryContentRepository: GrimmoryContentDataSource,
    val comicPageSource: ComicPageSource,
    val comicReaderProgressStore: ComicReaderProgressStore,
    val comicReaderPreferencesStore: ComicReaderPreferencesStore,
    val readerSessionStore: ReaderSessionStore,
    private val sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
    val readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory>,
) : AndroidViewModel(application) {
    private var catalogLibraries: List<Library> = libraryRepository.getLibraries()
    private var catalogItems: List<ReadableItem> = readableItemRepository.getItems()
    private val grimmoryCoverUrisByItemId = mutableStateMapOf<String, String>()
    private val pendingGrimmoryCoverIds = mutableSetOf<String>()
    private var lastGrimmoryCoverErrorMessage: String? = null
    private var readerSelectionRequestId: Int = 0

    var uiState by mutableStateOf(AppUiState())
        private set

    init {
        uiState = uiState.copy(
            themeMode = appPreferencesStore.preferencesFlow.value.themeMode,
            grimmoryConnection = uiState.grimmoryConnection.withStoredSession(
                grimmoryCredentialStore.sessionFlow.value,
            ),
        )
        refreshLibraryState()
        observeAppState()
    }

    fun onTopLevelSelected(destination: TopLevelDestination) {
        readerSelectionRequestId++
        uiState = uiState.copy(
            topLevel = destination,
        )
        refreshProgress()
    }

    fun onSourceSelected(sourceType: SourceType) {
        readerSelectionRequestId++
        uiState = uiState.copy(
            selectedSource = sourceType,
            selectedItemId = null,
            activeReader = null,
        )
        refreshProgress()
    }

    fun onLibraryFiltersChanged(filters: LibraryFilterState) {
        uiState = uiState.copy(
            libraryFiltersBySource = uiState.libraryFiltersBySource + (uiState.selectedSource to filters),
        )
    }

    fun onLibrarySearchQueryChanged(query: String) {
        uiState = uiState.copy(
            librarySearchQueriesBySource = uiState.librarySearchQueriesBySource + (uiState.selectedSource to query),
        )
    }

    fun clearLibraryFilters() {
        if (uiState.selectedLibraryFilters.isEmpty) return
        uiState = uiState.copy(
            libraryFiltersBySource = uiState.libraryFiltersBySource - uiState.selectedSource,
        )
    }

    fun onLibrarySortModeChanged(sortMode: LibrarySortMode) {
        uiState = uiState.copy(
            librarySortsBySource = uiState.librarySortsBySource + (uiState.selectedSource to sortMode),
        )
    }

    fun onItemSelected(item: ReadableItem) {
        val progressSync = sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
        val requestId = ++readerSelectionRequestId
        uiState = uiState.copy(
            selectedItemId = item.id,
            activeReader = item.format,
        )
        refreshProgress()
        if (progressSync != null) {
            viewModelScope.launch {
                runCatching {
                    progressSync.syncItem(item)
                }.onFailure { throwable ->
                    Log.w(LOG_TAG, "Unable to sync progress before opening ${item.id}.", throwable)
                }
                if (readerSelectionRequestId != requestId) return@launch
                refreshProgress()
            }
        }
    }

    fun coverImageUrlFor(item: ReadableItem): String? {
        return grimmoryCoverUrisByItemId[item.id] ?: item.coverUrl
    }

    fun ensureCoverCached(item: ReadableItem) {
        if (item.sourceType != SourceType.GRIMMORY) return
        if (item.id in pendingGrimmoryCoverIds) return
        val currentCoverUrl = grimmoryCoverUrisByItemId[item.id]
        if (currentCoverUrl?.startsWith("file:", ignoreCase = true) == true) return
        pendingGrimmoryCoverIds += item.id
        viewModelScope.launch {
            runCatching {
                grimmoryCoverRepository.getCoverUri(item)?.toString()
            }.onSuccess { cachedUri ->
                if (!cachedUri.isNullOrBlank()) {
                    Log.d(LOG_TAG, "Cached Grimmory cover for ${item.id}: $cachedUri")
                    grimmoryCoverUrisByItemId[item.id] = cachedUri
                    lastGrimmoryCoverErrorMessage = null
                } else {
                    Log.w(LOG_TAG, "Grimmory cover cache returned no URI for ${item.id}.")
                }
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Unable to cache Grimmory cover for ${item.id}.", throwable)
                val message = "Unable to load Grimmory covers: ${throwable.userFacingMessage()}"
                if (lastGrimmoryCoverErrorMessage != message) {
                    lastGrimmoryCoverErrorMessage = message
                    uiState = uiState.copy(
                        libraryMessage = message,
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                    )
                }
            }.also {
                pendingGrimmoryCoverIds -= item.id
            }
        }
    }

    fun onBackPressed() {
        readerSelectionRequestId++
        val wasReaderOpen = uiState.activeReader != null && uiState.selectedItem != null
        uiState = when {
            uiState.activeReader != null -> uiState.copy(
                activeReader = null,
                selectedItemId = null,
            )
            uiState.selectedItemId != null -> uiState.copy(selectedItemId = null)
            else -> uiState
        }
        if (wasReaderOpen) {
            viewModelScope.launch {
                flushPendingRemoteProgress()
            }
        }
        refreshProgress()
    }

    fun refreshProgress() {
        uiState = uiState.copy(
            progressByItemId = buildProgressSummaries(
                items = uiState.items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                localPageCountProvider = localDocumentRepository::getPageCount,
            ),
        )
        refreshDownloadedGrimmoryItemIdsAsync(uiState.items)
    }

    fun onDocumentImported(uri: Uri) {
        viewModelScope.launch {
            importDocument(uri)
        }
    }

    fun onDeleteImportedItem(item: ReadableItem) {
        viewModelScope.launch {
            deleteImportedItem(item)
        }
    }

    fun onDownloadGrimmoryItem(item: ReadableItem) {
        if (item.sourceType != SourceType.GRIMMORY) return
        if (item.id in uiState.downloadingGrimmoryItemIds) return
        uiState = uiState.copy(
            downloadingGrimmoryItemIds = uiState.downloadingGrimmoryItemIds + item.id,
            libraryMessage = "Downloading ${item.libraryDisplayLabel()}.",
            libraryMessageSeverity = LibraryMessageSeverity.Info,
        )
        viewModelScope.launch {
            runCatching {
                grimmoryContentRepository.downloadToLocalStorage(item)
            }.onSuccess { downloadedUri ->
                syncItemProgressIfSupported(item)
                val downloadedIds = loadDownloadedGrimmoryItemIds(uiState.items)
                uiState = uiState.copy(
                    downloadedGrimmoryItemIds = downloadedIds,
                    libraryMessage = if (downloadedUri != null) {
                        "Downloaded ${item.libraryDisplayLabel()} for local reading."
                    } else {
                        "Unable to download ${item.libraryDisplayLabel()}."
                    },
                    libraryMessageSeverity = if (downloadedUri != null) {
                        LibraryMessageSeverity.Info
                    } else {
                        LibraryMessageSeverity.Error
                    },
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    libraryMessage = "Unable to download ${item.libraryDisplayLabel()}: ${throwable.userFacingMessage()}",
                    libraryMessageSeverity = LibraryMessageSeverity.Error,
                )
            }
            uiState = uiState.copy(
                downloadingGrimmoryItemIds = uiState.downloadingGrimmoryItemIds - item.id,
            )
        }
    }

    fun onDeleteGrimmoryLocalFile(item: ReadableItem) {
        if (item.sourceType != SourceType.GRIMMORY) return
        viewModelScope.launch {
            val deleted = grimmoryContentRepository.deleteStoredDocument(item)
            val downloadedIds = loadDownloadedGrimmoryItemIds(uiState.items)
            uiState = uiState.copy(
                downloadedGrimmoryItemIds = downloadedIds,
                libraryMessage = if (deleted) {
                    "Deleted local file for ${item.libraryDisplayLabel()}."
                } else {
                    "No local file found for ${item.libraryDisplayLabel()}."
                },
                libraryMessageSeverity = if (deleted) {
                    LibraryMessageSeverity.Info
                } else {
                    LibraryMessageSeverity.Error
                },
            )
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        if (uiState.themeMode == themeMode) return
        uiState = uiState.copy(themeMode = themeMode)
        viewModelScope.launch {
            appPreferencesStore.setThemeMode(themeMode)
        }
    }

    fun onGrimmoryDisplayNameChanged(displayName: String) {
        uiState = uiState.copy(
            grimmoryConnection = grimmoryCoordinator.refreshedConnectionState(
                uiState.grimmoryConnection.copy(
                    displayName = displayName,
                    message = null,
                    isError = false,
                ),
            ),
        )
    }

    fun onGrimmoryBaseUrlChanged(baseUrl: String) {
        uiState = uiState.copy(
            grimmoryConnection = grimmoryCoordinator.refreshedConnectionState(
                uiState.grimmoryConnection.copy(
                    baseUrl = baseUrl,
                    message = null,
                    isError = false,
                ),
            ),
        )
    }

    fun onGrimmoryUsernameChanged(username: String) {
        uiState = uiState.copy(
            grimmoryConnection = grimmoryCoordinator.refreshedConnectionState(
                uiState.grimmoryConnection.copy(
                    username = username,
                    message = null,
                    isError = false,
                ),
            ),
        )
    }

    fun onGrimmoryPasswordChanged(password: String) {
        uiState = uiState.copy(
            grimmoryConnection = uiState.grimmoryConnection.copy(
                password = password,
                message = null,
                isError = false,
            ),
        )
    }

    fun connectGrimmoryServer() {
        when (val preparation = grimmoryCoordinator.prepareConnect(uiState.grimmoryConnection)) {
            is GrimmorySyncPreparation.Invalid -> {
                uiState = uiState.copy(grimmoryConnection = preparation.connection)
            }
            GrimmorySyncPreparation.MissingSession -> Unit
            is GrimmorySyncPreparation.Ready -> {
                uiState = uiState.copy(
                    grimmoryConnection = preparation.activeConnection.copy(
                        isConnecting = true,
                        message = "Connecting to Grimmory.",
                        isError = false,
                    ),
                )
                startGrimmorySync(preparation)
            }
        }
    }

    fun refreshGrimmoryLibrary() {
        when (val preparation = grimmoryCoordinator.prepareRefresh(uiState.grimmoryConnection)) {
            GrimmorySyncPreparation.MissingSession -> {
                uiState = uiState.copy(
                    libraryMessage = "Connect to Grimmory before syncing the library.",
                    libraryMessageSeverity = LibraryMessageSeverity.Error,
                    grimmoryConnection = uiState.grimmoryConnection.copy(
                        message = "Connect to Grimmory before syncing the library.",
                        isError = true,
                    ),
                )
            }
            is GrimmorySyncPreparation.Invalid -> Unit
            is GrimmorySyncPreparation.Ready -> {
                uiState = uiState.copy(
                    grimmoryConnection = preparation.activeConnection.copy(
                        isConnecting = true,
                        message = "Syncing Grimmory library.",
                        isError = false,
                    ),
                    libraryMessage = "Syncing Grimmory library.",
                    libraryMessageSeverity = LibraryMessageSeverity.Info,
                )
                startGrimmorySync(preparation)
            }
        }
    }

    private fun startGrimmorySync(
        preparation: GrimmorySyncPreparation.Ready,
    ) {
        viewModelScope.launch {
            when (val result = grimmoryCoordinator.executeSync(preparation, ::clearGrimmoryCoverState)) {
                is GrimmorySyncExecutionResult.Success -> {
                    uiState = uiState.copy(
                        topLevel = TopLevelDestination.Library,
                        selectedSource = SourceType.GRIMMORY,
                        selectedItemId = null,
                        activeReader = null,
                        libraryMessage = "Synced ${result.itemCount} Grimmory books from ${result.libraryCount} libraries.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                        grimmoryConnection = result.connection,
                    )
                }
                is GrimmorySyncExecutionResult.Failure -> {
                    uiState = uiState.copy(
                        libraryMessage = result.message,
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        grimmoryConnection = result.connection,
                    )
                }
            }
        }
    }

    internal suspend fun importDocument(uri: Uri) {
        val importedItem = localDocumentRepository.import(uri)
        refreshLibraryState(
            topLevel = TopLevelDestination.Library,
            selectedSource = importedItem?.sourceType ?: uiState.selectedSource,
            selectedItemId = importedItem?.id,
            activeReader = importedItem?.format,
            libraryMessage = if (importedItem != null) {
                "Imported ${importedItem.libraryDisplayLabel()} (${importedItem.format.name})"
            } else {
                "Unable to import that file as a supported local document."
            },
            libraryMessageSeverity = if (importedItem != null) {
                LibraryMessageSeverity.Info
            } else {
                LibraryMessageSeverity.Error
            },
        )
    }

    internal suspend fun deleteImportedItem(item: ReadableItem) {
        val outcome = importedItemDeletionService.delete(item)
        val removed = outcome == ImportedItemDeletionOutcome.Removed
        val isDeletedSelection = removed && uiState.selectedItemId == item.id
        refreshLibraryState(
            selectedSource = uiState.selectedSource,
            selectedItemId = if (isDeletedSelection) null else uiState.selectedItemId,
            activeReader = if (isDeletedSelection) null else uiState.activeReader,
            libraryMessage = deletionMessage(item, outcome),
            libraryMessageSeverity = if (removed) {
                LibraryMessageSeverity.Info
            } else {
                LibraryMessageSeverity.Error
            },
        )
    }

    fun clearLibraryMessage() {
        if (uiState.libraryMessage != null) {
            uiState = uiState.copy(
                libraryMessage = null,
                libraryMessageSeverity = LibraryMessageSeverity.Info,
            )
        }
    }

    private fun refreshLibraryState(
        topLevel: TopLevelDestination = uiState.topLevel,
        selectedSource: SourceType = uiState.selectedSource,
        selectedItemId: String? = uiState.selectedItemId,
        activeReader: FormatType? = uiState.activeReader,
        libraryMessage: String? = uiState.libraryMessage,
        libraryMessageSeverity: LibraryMessageSeverity = uiState.libraryMessageSeverity,
        themeMode: ThemeMode = uiState.themeMode,
    ) {
        val items = catalogItems + localDocumentRepository.getImportedItems()
        uiState = uiState.copy(
            topLevel = topLevel,
            selectedSource = selectedSource,
            libraries = catalogLibraries,
            items = items,
            selectedItemId = selectedItemId,
            activeReader = activeReader,
            progressByItemId = buildProgressSummaries(
                items = items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                localPageCountProvider = localDocumentRepository::getPageCount,
            ),
            libraryMessage = libraryMessage,
            libraryMessageSeverity = libraryMessageSeverity,
            themeMode = themeMode,
        )
        refreshDownloadedGrimmoryItemIdsAsync(items)
    }

    @OptIn(FlowPreview::class)
    private fun observeAppState() {
        viewModelScope.launch {
            grimmoryCredentialStore.sessionFlow.collect { session ->
                if (!uiState.grimmoryConnection.isConnecting) {
                    uiState = uiState.copy(
                        grimmoryConnection = grimmoryCoordinator.refreshedConnectionState(
                            uiState.grimmoryConnection.withStoredSession(session),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            readerSessionStore.progressByItemIdFlow
                .map { progressByItemId ->
                    progressByItemId.values
                        .filter { progress ->
                            progress.syncState == com.example.unifiedreader.core.model.SyncState.DIRTY
                        }
                        .sortedBy(com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot::itemId)
                        .map { progress ->
                            "${progress.itemId}:${progress.syncState.name}:${progress.updatedAtEpochMillis}"
                        }
                }
                .distinctUntilChanged()
                .debounce(1500L)
                .collect { dirtyProgressKeys ->
                    if (dirtyProgressKeys.isNotEmpty()) {
                        flushPendingRemoteProgress()
                    }
                }
        }
        viewModelScope.launch {
            combine(
                appPreferencesStore.preferencesFlow,
                combine(
                    libraryRepository.observeLibraries(),
                    readableItemRepository.observeItems(),
                    combine(
                        remoteCatalogReadiness.isHydratedFlow,
                        localDocumentRepository.isHydratedFlow,
                        localDocumentRepository.importedItemsFlow,
                        readerSessionStore.progressByItemIdFlow,
                        readerSessionStore.sessionByItemIdFlow,
                    ) { isRemoteCatalogHydrated, isLocalCatalogHydrated, importedItems, progressByItemId, sessionByItemId ->
                        AppCatalogObservation(
                            isRemoteCatalogHydrated = isRemoteCatalogHydrated,
                            isLocalCatalogHydrated = isLocalCatalogHydrated,
                            importedItems = importedItems,
                            progressByItemId = progressByItemId,
                            sessionByItemId = sessionByItemId,
                        )
                    },
                ) { libraries, catalogItems, observation ->
                    val items = catalogItems + observation.importedItems
                    AppCatalogSnapshot(
                        libraries = libraries,
                        catalogItems = catalogItems,
                        items = items,
                        downloadedGrimmoryItemIds = loadDownloadedGrimmoryItemIds(items),
                        progressByItemId = buildProgressSummaries(
                            items = items,
                            progressByItemId = observation.progressByItemId,
                            sessionByItemId = observation.sessionByItemId,
                            localPageCountProvider = localDocumentRepository::getPageCount,
                        ),
                        hydratedSources = buildSet {
                            add(SourceType.KAVITA)
                            if (observation.isRemoteCatalogHydrated) {
                                add(SourceType.GRIMMORY)
                            }
                            if (observation.isLocalCatalogHydrated) {
                                add(SourceType.LOCAL)
                            }
                        },
                        themeMode = uiState.themeMode,
                    )
                },
            ) { preferences, snapshot ->
                snapshot.copy(themeMode = preferences.themeMode)
            }.collect { snapshot ->
                catalogLibraries = snapshot.libraries
                catalogItems = snapshot.catalogItems
                val activeItemIds = snapshot.items.mapTo(mutableSetOf(), ReadableItem::id)
                grimmoryCoverUrisByItemId.keys
                    .filterNot(activeItemIds::contains)
                    .forEach(grimmoryCoverUrisByItemId::remove)
                uiState = uiState.copy(
                    libraries = snapshot.libraries,
                    items = snapshot.items,
                    progressByItemId = snapshot.progressByItemId,
                    downloadedGrimmoryItemIds = snapshot.downloadedGrimmoryItemIds,
                    hydratedSources = snapshot.hydratedSources,
                    themeMode = snapshot.themeMode,
                )
            }
        }
    }

    private fun refreshDownloadedGrimmoryItemIdsAsync(
        items: List<ReadableItem>,
    ) {
        val requestedItemIds = items.mapTo(mutableSetOf(), ReadableItem::id)
        viewModelScope.launch {
            val downloadedIds = loadDownloadedGrimmoryItemIds(items)
            val currentItemIds = uiState.items.mapTo(mutableSetOf(), ReadableItem::id)
            if (currentItemIds == requestedItemIds) {
                uiState = uiState.copy(
                    downloadedGrimmoryItemIds = downloadedIds,
                )
            }
        }
    }

    private suspend fun loadDownloadedGrimmoryItemIds(
        items: List<ReadableItem>,
    ): Set<String> = grimmoryContentRepository.storedDocumentItemIds(items)

    private suspend fun syncItemProgressIfSupported(
        item: ReadableItem,
    ) {
        sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
            ?.let { sync ->
                runCatching {
                    sync.syncItem(item)
                }.onFailure { throwable ->
                    Log.w(LOG_TAG, "Unable to sync progress for ${item.id}.", throwable)
                }
            }
    }

    private suspend fun flushPendingRemoteProgress() {
        val remoteItems = catalogItems
        sourceProgressSyncs.forEach { sync ->
            runCatching {
                sync.pushPendingProgress(remoteItems)
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Unable to push pending remote progress.", throwable)
            }
        }
    }

    private fun clearGrimmoryCoverState() {
        grimmoryCoverUrisByItemId.clear()
        pendingGrimmoryCoverIds.clear()
        lastGrimmoryCoverErrorMessage = null
    }
}

private data class AppCatalogObservation(
    val isRemoteCatalogHydrated: Boolean,
    val isLocalCatalogHydrated: Boolean,
    val importedItems: List<ReadableItem>,
    val progressByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>,
    val sessionByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>,
)

private const val LOG_TAG = "UnifiedReader"
