package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.applyLibrarySort
import com.example.unifiedreader.core.model.buildLibraryFilterFacets
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
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
    private val grimmoryCatalogSyncer: GrimmoryCatalogSyncer,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val grimmoryCoverRepository: GrimmoryCoverDataSource,
    private val grimmoryContentRepository: GrimmoryContentDataSource,
    val comicPageSource: ComicPageSource,
    val comicReaderProgressStore: ComicReaderProgressStore,
    val comicReaderPreferencesStore: ComicReaderPreferencesStore,
    val readerSessionStore: ReaderSessionStore,
    val readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory>,
) : AndroidViewModel(application) {
    private var catalogLibraries: List<Library> = libraryRepository.getLibraries()
    private var catalogItems: List<ReadableItem> = readableItemRepository.getItems()
    private val grimmoryCoverUrisByItemId = mutableStateMapOf<String, String>()
    private val pendingGrimmoryCoverIds = mutableSetOf<String>()
    private var lastGrimmoryCoverErrorMessage: String? = null

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
        uiState = uiState.copy(
            topLevel = destination,
        )
        refreshProgress()
    }

    fun onSourceSelected(sourceType: SourceType) {
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
        uiState = uiState.copy(
            selectedItemId = item.id,
            activeReader = item.format,
        )
        refreshProgress()
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
                    uiState = uiState.copy(libraryMessage = message)
                }
            }.also {
                pendingGrimmoryCoverIds -= item.id
            }
        }
    }

    fun onBackPressed() {
        uiState = when {
            uiState.activeReader != null -> uiState.copy(
                activeReader = null,
                selectedItemId = null,
            )
            uiState.selectedItemId != null -> uiState.copy(selectedItemId = null)
            else -> uiState
        }
        refreshProgress()
    }

    fun refreshProgress() {
        uiState = uiState.copy(
            progressByItemId = loadProgressSummaries(
                items = uiState.items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
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
        )
        viewModelScope.launch {
            runCatching {
                grimmoryContentRepository.downloadToLocalStorage(item)
            }.onSuccess { downloadedUri ->
                val downloadedIds = loadDownloadedGrimmoryItemIds(uiState.items)
                uiState = uiState.copy(
                    downloadedGrimmoryItemIds = downloadedIds,
                    libraryMessage = if (downloadedUri != null) {
                        "Downloaded ${item.libraryDisplayLabel()} for local reading."
                    } else {
                        "Unable to download ${item.libraryDisplayLabel()}."
                    },
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    libraryMessage = "Unable to download ${item.libraryDisplayLabel()}: ${throwable.userFacingMessage()}",
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
            grimmoryConnection = refreshedGrimmoryConnectionState(
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
            grimmoryConnection = refreshedGrimmoryConnectionState(
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
            grimmoryConnection = refreshedGrimmoryConnectionState(
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
        val connection = uiState.grimmoryConnection
        val normalizedBaseUrl = normalizeServerBaseUrl(connection.baseUrl)
        if (normalizedBaseUrl == null) {
            uiState = uiState.copy(
                grimmoryConnection = connection.copy(
                    message = "Enter a server URL that starts with http:// or https://.",
                    isError = true,
                ),
            )
            return
        }
        val displayName = connection.displayName.trim()
        val username = connection.username.trim()
        if (displayName.isBlank() || username.isBlank()) {
            uiState = uiState.copy(
                grimmoryConnection = connection.copy(
                    message = "Fill in the Grimmory server name and username.",
                    isError = true,
                ),
            )
            return
        }
        val serverId = serverIdForBaseUrl(normalizedBaseUrl)
        val savedSession = matchingStoredGrimmorySession(
            serverId = serverId,
            baseUrl = normalizedBaseUrl,
            username = username,
        ).takeIf { connection.password.isBlank() }
        val password = connection.password.takeIf(String::isNotBlank)
        if (password == null && savedSession == null) {
            uiState = uiState.copy(
                grimmoryConnection = connection.copy(
                    message = "Enter the Grimmory password or use the saved server details.",
                    isError = true,
                ),
            )
            return
        }
        val request = GrimmoryCatalogSyncRequest(
            serverId = serverId,
            displayName = displayName,
            baseUrl = normalizedBaseUrl,
            username = username,
            password = password,
            accessToken = savedSession?.accessToken,
            refreshToken = savedSession?.refreshToken,
        )

        uiState = uiState.copy(
            grimmoryConnection = connection.copy(
                isConnecting = true,
                message = "Connecting to Grimmory.",
                isError = false,
            ),
        )
        startGrimmorySync(
            request = request,
            activeConnection = connection.copy(
                baseUrl = normalizedBaseUrl,
            ),
            fallbackSession = savedSession?.copy(
                displayName = displayName,
                baseUrl = normalizedBaseUrl,
                username = username,
            ),
            successConnectionMessage = "Connected to Grimmory.",
            failurePrefix = "Unable to connect to Grimmory",
        )
    }

    fun refreshGrimmoryLibrary() {
        val savedSession = grimmoryCredentialStore.sessionFlow.value
        if (savedSession == null) {
            uiState = uiState.copy(
                libraryMessage = "Connect to Grimmory before syncing the library.",
                grimmoryConnection = uiState.grimmoryConnection.copy(
                    message = "Connect to Grimmory before syncing the library.",
                    isError = true,
                ),
            )
            return
        }
        val request = GrimmoryCatalogSyncRequest(
            serverId = savedSession.serverId,
            displayName = savedSession.displayName,
            baseUrl = savedSession.baseUrl,
            username = savedSession.username,
            accessToken = savedSession.accessToken,
            refreshToken = savedSession.refreshToken,
        )
        val connection = refreshedGrimmoryConnectionState(
            uiState.grimmoryConnection.withStoredSession(savedSession),
        )
        uiState = uiState.copy(
            grimmoryConnection = connection.copy(
                isConnecting = true,
                message = "Syncing Grimmory library.",
                isError = false,
            ),
            libraryMessage = "Syncing Grimmory library.",
        )
        startGrimmorySync(
            request = request,
            activeConnection = connection,
            fallbackSession = savedSession,
            successConnectionMessage = "Grimmory library synced.",
            failurePrefix = "Unable to sync Grimmory",
        )
    }

    private fun startGrimmorySync(
        request: GrimmoryCatalogSyncRequest,
        activeConnection: GrimmoryConnectionUiState,
        fallbackSession: GrimmoryStoredSession?,
        successConnectionMessage: String,
        failurePrefix: String,
    ) {
        viewModelScope.launch {
            runCatching {
                val result = grimmoryCatalogSyncer.sync(request)
                val storedSession = result.session?.toStoredSession(
                    serverId = request.serverId,
                    displayName = request.displayName,
                    baseUrl = request.baseUrl,
                    username = request.username,
                ) ?: fallbackSession?.copy(
                    displayName = request.displayName,
                    baseUrl = request.baseUrl,
                    username = request.username,
                )
                if (storedSession != null) {
                    grimmoryCredentialStore.save(storedSession)
                }
                clearGrimmoryCoverState()
                grimmoryCoverRepository.clearCachedCovers(request.serverId)
                result
            }.onSuccess { result ->
                uiState = uiState.copy(
                    topLevel = TopLevelDestination.Library,
                    selectedSource = SourceType.GRIMMORY,
                    selectedItemId = null,
                    activeReader = null,
                    libraryMessage = "Synced ${result.itemCount} Grimmory books from ${result.libraryCount} libraries.",
                    grimmoryConnection = activeConnection.copy(
                        password = "",
                        hasStoredSession = true,
                        isConnecting = false,
                        message = successConnectionMessage,
                        isError = false,
                    ),
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    libraryMessage = "$failurePrefix: ${throwable.userFacingMessage()}",
                    grimmoryConnection = activeConnection.copy(
                        isConnecting = false,
                        message = "$failurePrefix: ${throwable.userFacingMessage()}",
                        isError = true,
                    ),
                )
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
        )
    }

    fun clearLibraryMessage() {
        if (uiState.libraryMessage != null) {
            uiState = uiState.copy(libraryMessage = null)
        }
    }

    private fun refreshLibraryState(
        topLevel: TopLevelDestination = uiState.topLevel,
        selectedSource: SourceType = uiState.selectedSource,
        selectedItemId: String? = uiState.selectedItemId,
        activeReader: FormatType? = uiState.activeReader,
        libraryMessage: String? = uiState.libraryMessage,
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
            progressByItemId = loadProgressSummaries(
                items = items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
            ),
            libraryMessage = libraryMessage,
            themeMode = themeMode,
        )
        refreshDownloadedGrimmoryItemIdsAsync(items)
    }

    private fun observeAppState() {
        viewModelScope.launch {
            grimmoryCredentialStore.sessionFlow.collect { session ->
                if (!uiState.grimmoryConnection.isConnecting) {
                    uiState = uiState.copy(
                        grimmoryConnection = refreshedGrimmoryConnectionState(
                            uiState.grimmoryConnection.withStoredSession(session),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                appPreferencesStore.preferencesFlow,
                combine(
                    libraryRepository.observeLibraries(),
                    readableItemRepository.observeItems(),
                    localDocumentRepository.importedItemsFlow,
                    readerSessionStore.progressByItemIdFlow,
                    readerSessionStore.sessionByItemIdFlow,
                ) { libraries, catalogItems, importedItems, progressByItemId, sessionByItemId ->
                    val items = catalogItems + importedItems
                    AppCatalogSnapshot(
                        libraries = libraries,
                        catalogItems = catalogItems,
                        items = items,
                        downloadedGrimmoryItemIds = loadDownloadedGrimmoryItemIds(items),
                        progressByItemId = loadProgressSummaries(
                            items = items,
                            progressByItemId = progressByItemId,
                            sessionByItemId = sessionByItemId,
                        ),
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

    private fun matchingStoredGrimmorySession(
        serverId: String,
        baseUrl: String,
        username: String,
    ): GrimmoryStoredSession? = grimmoryCredentialStore.sessionFlow.value?.takeIf { session ->
        session.serverId == serverId &&
            session.baseUrl == baseUrl &&
            session.username == username
    }

    private fun refreshedGrimmoryConnectionState(
        connection: GrimmoryConnectionUiState,
    ): GrimmoryConnectionUiState {
        val normalizedBaseUrl = normalizeServerBaseUrl(connection.baseUrl) ?: return connection.copy(
            hasStoredSession = false,
        )
        val username = connection.username.trim()
        if (username.isBlank()) {
            return connection.copy(hasStoredSession = false)
        }
        val matchingSession = matchingStoredGrimmorySession(
            serverId = serverIdForBaseUrl(normalizedBaseUrl),
            baseUrl = normalizedBaseUrl,
            username = username,
        )
        return connection.copy(hasStoredSession = matchingSession != null)
    }

    private fun clearGrimmoryCoverState() {
        grimmoryCoverUrisByItemId.clear()
        pendingGrimmoryCoverIds.clear()
        lastGrimmoryCoverErrorMessage = null
    }

    private fun loadProgressSummaries(
        items: List<ReadableItem>,
        progressByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>,
        sessionByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>,
    ): Map<String, ItemProgressSummary> = items.associateNotNull { item ->
        val progress = progressByItemId[item.id] ?: return@associateNotNull null
        val sessionPageCount = sessionByItemId[item.id]?.pageCount?.takeIf { it > 0 }
        val summary = when (progress.locatorType) {
            LocatorType.PAGE -> {
                val pageIndex = progress.page ?: return@associateNotNull null
                val pageCount = sessionPageCount ?: when (item.format) {
                    FormatType.CBZ,
                    FormatType.CBR -> localDocumentRepository.getPageCount(item)
                    FormatType.EPUB -> 0
                    FormatType.PDF -> return@associateNotNull null
                }
                buildItemProgressSummary(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    lastReadAtEpochMillis = progress.updatedAtEpochMillis,
                )
            }

            LocatorType.PERCENT -> buildPercentProgressSummary(
                percent = progress.percent,
                pageCount = sessionPageCount,
                lastReadAtEpochMillis = progress.updatedAtEpochMillis,
            )
            LocatorType.CFI -> buildSavedLocationProgressSummary(
                percent = progress.percent,
                page = progress.page,
                pageCount = sessionPageCount,
                lastReadAtEpochMillis = progress.updatedAtEpochMillis,
            )
        } ?: return@associateNotNull null
        item.id to summary
    }
}

private data class AppCatalogSnapshot(
    val libraries: List<Library>,
    val catalogItems: List<ReadableItem>,
    val items: List<ReadableItem>,
    val downloadedGrimmoryItemIds: Set<String>,
    val progressByItemId: Map<String, ItemProgressSummary>,
    val themeMode: ThemeMode,
)

data class AppUiState(
    val topLevel: TopLevelDestination = TopLevelDestination.Library,
    val selectedSource: SourceType = SourceType.KAVITA,
    val selectedItemId: String? = null,
    val activeReader: FormatType? = null,
    val libraries: List<Library> = emptyList(),
    val items: List<ReadableItem> = emptyList(),
    val libraryFiltersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val librarySortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val downloadedGrimmoryItemIds: Set<String> = emptySet(),
    val downloadingGrimmoryItemIds: Set<String> = emptySet(),
    val libraryMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val grimmoryConnection: GrimmoryConnectionUiState = GrimmoryConnectionUiState(),
) {
    val sourceItems: List<ReadableItem>
        get() = items.filter { it.sourceType == selectedSource }

    val selectedLibraryFilters: LibraryFilterState
        get() = libraryFiltersBySource[selectedSource] ?: LibraryFilterState()

    val selectedLibraryFilterFacets: LibraryFilterFacets
        get() = buildLibraryFilterFacets(sourceItems, downloadedGrimmoryItemIds)

    val selectedLibrarySortMode: LibrarySortMode
        get() = librarySortsBySource[selectedSource] ?: LibrarySortMode.DATE_ADDED

    val visibleItems: List<ReadableItem>
        get() = applyLibrarySort(
            items = applyLibraryFilters(sourceItems, selectedLibraryFilters, downloadedGrimmoryItemIds),
            sortMode = selectedLibrarySortMode,
            lastReadAtEpochMillisByItemId = progressByItemId.mapValues { (_, progress) ->
                progress.lastReadAtEpochMillis
            },
        )

    val selectedItem: ReadableItem?
        get() = items.firstOrNull { it.id == selectedItemId }
}

private fun deletionMessage(
    item: ReadableItem,
    outcome: ImportedItemDeletionOutcome,
): String = when (outcome) {
    ImportedItemDeletionOutcome.Removed -> "Removed ${item.libraryDisplayLabel()}."
    ImportedItemDeletionOutcome.NotImportedItem -> "Only imported files can be removed from the library."
    ImportedItemDeletionOutcome.NotFound -> "Unable to remove ${item.libraryDisplayLabel()}."
}

private fun normalizeServerBaseUrl(
    baseUrl: String,
): String? {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isBlank()) return null
    val lower = trimmed.lowercase(Locale.US)
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    return trimmed
}

private fun serverIdForBaseUrl(
    baseUrl: String,
): String = "grimmory-${Integer.toHexString(baseUrl.hashCode())}"

private fun GrimmoryConnectionUiState.withStoredSession(
    session: GrimmoryStoredSession?,
): GrimmoryConnectionUiState = if (session == null) {
    copy(hasStoredSession = false)
} else {
    copy(
        displayName = session.displayName,
        baseUrl = session.baseUrl,
        username = session.username,
        password = "",
        hasStoredSession = true,
        isError = false,
    )
}

private fun GrimmorySession.toStoredSession(
    serverId: String,
    displayName: String,
    baseUrl: String,
    username: String,
): GrimmoryStoredSession = GrimmoryStoredSession(
    serverId = serverId,
    displayName = displayName,
    baseUrl = baseUrl,
    username = username,
    accessToken = accessToken,
    refreshToken = refreshToken,
)

private fun Throwable.userFacingMessage(): String =
    message?.takeIf(String::isNotBlank) ?: this::class.simpleName ?: "unknown error"

private const val LOG_TAG = "UnifiedReader"

private inline fun <T, R> Iterable<T>.associateNotNull(
    transform: (T) -> Pair<String, R>?,
): Map<String, R> = buildMap {
    for (element in this@associateNotNull) {
        val pair = transform(element) ?: continue
        put(pair.first, pair.second)
    }
}

enum class TopLevelDestination {
    Library,
    Settings,
}

private fun buildItemProgressSummary(
    pageIndex: Int,
    pageCount: Int,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary? {
    if (pageCount <= 0) return null
    val clampedPageIndex = pageIndex.coerceIn(0, pageCount - 1)
    return ItemProgressSummary(
        label = "Resume on page ${clampedPageIndex + 1} of $pageCount",
        fraction = (clampedPageIndex + 1) / pageCount.toFloat(),
        currentPage = clampedPageIndex + 1,
        pageCount = pageCount,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
    )
}

private fun buildPercentProgressSummary(
    percent: Double?,
    pageCount: Int? = null,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary? {
    val normalizedPercent = percent?.coerceIn(0.0, 1.0) ?: return null
    val percentLabel = (normalizedPercent * 100).toInt()
    return ItemProgressSummary(
        label = "Resume at $percentLabel%",
        fraction = normalizedPercent.toFloat(),
        pageCount = pageCount,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
    )
}

private fun buildSavedLocationProgressSummary(
    percent: Double?,
    page: Int? = null,
    pageCount: Int? = null,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary = ItemProgressSummary(
    label = "Resume from saved location",
    fraction = (percent ?: 0.0).coerceIn(0.0, 1.0).toFloat(),
    currentPage = page?.takeIf { it > 0 },
    pageCount = pageCount,
    lastReadAtEpochMillis = lastReadAtEpochMillis,
)

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object AppViewModelTestAccess {
    fun buildItemProgressSummary(
        pageIndex: Int,
        pageCount: Int,
    ): ItemProgressSummary? = com.example.unifiedreader.buildItemProgressSummary(
        pageIndex = pageIndex,
        pageCount = pageCount,
    )
}
