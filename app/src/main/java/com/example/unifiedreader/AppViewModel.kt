package com.example.unifiedreader

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.downloads.DownloadEntriesSnapshot
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val libraryBrowseController: AppLibraryBrowseController,
    private val settingsController: AppSettingsController,
    private val remoteDownloadController: AppRemoteDownloadController,
    private val appStateHydrator: AppStateHydrator,
    private val catalogStateCoordinator: AppCatalogStateCoordinator,
    private val libraryActionsCoordinator: AppLibraryActionsCoordinator,
    private val comicReadingDirectionController: AppComicReadingDirectionController,
    private val readerCoordinator: AppReaderCoordinator,
    private val remoteSourcesCoordinator: AppRemoteSourcesCoordinator,
    val comicPageSource: ComicPageSource,
    val comicReaderProgressStore: ComicReaderProgressStore,
    val comicReaderPreferencesStore: ComicReaderPreferencesStore,
    val readerSessionStore: ReaderSessionStore,
    val readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory>,
) : ViewModel() {
    var uiState by mutableStateOf(AppUiState().withDerivedLibraryState())
        private set
    var downloadsSnapshot by mutableStateOf(DownloadEntriesSnapshot(emptyList()))
        private set
    var isDownloadsSheetVisible by mutableStateOf(false)
        private set
    var isUnsupportedImportDialogVisible by mutableStateOf(false)
        private set
    private var lastDownloadedAvailabilitySignature: Set<String>? = null

    init {
        viewModelScope.launch {
            appStateHydrator.hydrate()
        }
        remoteSourcesCoordinator.initialize(::updateUiState)
        settingsController.restoreState(updateState = ::updateUiState)
        libraryBrowseController.restoreState(updateState = ::updateUiState)
        catalogStateCoordinator.updateLibraryState(
            currentState = uiState,
            updateState = ::updateUiState,
        )
        observeAppState()
    }

    fun onTopLevelSelected(destination: TopLevelDestination) {
        readerCoordinator.onTopLevelSelected(
            destination = destination,
            updateState = ::updateUiState,
            refreshProgress = ::refreshProgress,
        )
    }

    fun onSourceSelected(sourceType: SourceType) {
        readerCoordinator.onSourceSelected(
            sourceType = sourceType,
            updateState = ::updateUiState,
            refreshProgress = ::refreshProgress,
        )
    }

    fun onLibraryFiltersChanged(filters: LibraryFilterState) {
        libraryBrowseController.onLibraryFiltersChanged(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            filters = filters,
        )
    }

    fun onLibrarySearchQueryChanged(query: String) {
        libraryBrowseController.onLibrarySearchQueryChanged(
            currentState = uiState,
            updateState = ::updateUiState,
            query = query,
        )
    }

    fun clearLibraryFilters() {
        if (uiState.selectedLibraryFilters.isEmpty) return
        libraryBrowseController.clearLibraryFilters(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun onLibrarySortModeChanged(sortMode: LibrarySortMode) {
        libraryBrowseController.onLibrarySortModeChanged(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            sortMode = sortMode,
        )
    }

    fun onItemSelected(item: ReadableItem) {
        readerCoordinator.onItemSelected(
            item = item,
            updateState = ::updateUiState,
            scope = viewModelScope,
            refreshProgress = ::refreshProgress,
            selectedReaderItem = null,
        )
    }

    fun onLibraryDirectionSelected(
        libraryId: String,
        direction: ComicReadingDirection,
    ) {
        comicReadingDirectionController.onLibraryDirectionSelected(
            scope = viewModelScope,
            libraryId = libraryId,
            direction = direction,
        )
    }

    fun onSeriesDirectionSelected(
        item: ReadableItem,
        direction: ComicReadingDirection,
    ) {
        comicReadingDirectionController.onSeriesDirectionSelected(
            scope = viewModelScope,
            item = item,
            direction = direction,
        )
    }

    fun seriesReadingDirectionFor(
        item: ReadableItem,
    ): ComicReadingDirection? = comicReadingDirectionController.effectiveSeriesDirection(
        item = item,
        currentPreferences = uiState.comicReadingDirectionPreferences,
    )

    fun libraryScrollPositionFor(contextKey: String): LibraryScrollPosition {
        return readerCoordinator.libraryScrollPositionFor(contextKey)
    }

    fun onLibraryScrollPositionChanged(
        contextKey: String,
        position: LibraryScrollPosition,
    ) {
        readerCoordinator.onLibraryScrollPositionChanged(
            contextKey = contextKey,
            position = position,
        )
    }

    fun coverImageUrlFor(item: ReadableItem): String? {
        return libraryActionsCoordinator.coverImageUrlFor(item)
    }

    fun ensureCoverCached(item: ReadableItem) {
        libraryActionsCoordinator.ensureCoverCached(
            item = item,
            scope = viewModelScope,
            onError = {},
        )
    }

    fun onBackPressed() {
        readerCoordinator.onBackPressed(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            catalogItemsProvider = catalogStateCoordinator::catalogItems,
            refreshProgress = ::refreshProgress,
        )
    }

    fun refreshProgress() {
        readerCoordinator.refreshProgress(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            refreshDownloadedRemoteItemIds = { items ->
                catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
                    scope = viewModelScope,
                    stateProvider = { uiState },
                    updateState = ::updateUiState,
                    items = items,
                )
            },
        )
    }

    fun onDeleteImportedItem(item: ReadableItem) {
        viewModelScope.launch {
            deleteImportedItem(item)
        }
    }

    fun onDocumentImported(uri: Uri) {
        viewModelScope.launch {
            val imported = importDocument(uri)
            if (!imported) {
                isUnsupportedImportDialogVisible = true
            }
        }
    }

    fun dismissUnsupportedImportDialog() {
        isUnsupportedImportDialogVisible = false
    }

    fun onDownloadRemoteItem(item: ReadableItem) {
        remoteDownloadController.onDownloadRemoteItem(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            item = item,
        )
    }

    fun onDeleteRemoteLocalFile(item: ReadableItem) {
        remoteDownloadController.onDeleteRemoteLocalFile(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            item = item,
        )
    }

    fun showDownloadsSheet() {
        isDownloadsSheetVisible = true
    }

    fun hideDownloadsSheet() {
        isDownloadsSheetVisible = false
    }

    fun cancelDownload(entryId: String) {
        remoteDownloadController.cancelDownload(
            scope = viewModelScope,
            entryId = entryId,
        )
    }

    fun retryDownload(entryId: String) {
        remoteDownloadController.retryDownload(
            scope = viewModelScope,
            entryId = entryId,
        )
    }

    fun clearCompletedDownloads() {
        remoteDownloadController.clearCompletedDownloads(viewModelScope)
    }

    fun openDownloadedEntry(entryId: String) {
        viewModelScope.launch {
            val item = remoteDownloadController.resolveOpenItem(entryId) ?: return@launch
            val catalogItem = uiState.items.firstOrNull { catalogEntry -> catalogEntry.id == item.id }
            hideDownloadsSheet()
            readerCoordinator.onItemSelected(
                item = catalogItem ?: item,
                updateState = ::updateUiState,
                scope = viewModelScope,
                refreshProgress = ::refreshProgress,
                selectedReaderItem = item.takeIf { catalogItem == null },
            )
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        settingsController.onThemeModeSelected(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            themeMode = themeMode,
        )
    }

    fun onKavitaDisplayNameChanged(displayName: String) {
        remoteSourcesCoordinator.onKavitaDisplayNameChanged(
            displayName = displayName,
            updateState = ::updateUiState,
        )
    }

    fun onKavitaBaseUrlChanged(baseUrl: String) {
        remoteSourcesCoordinator.onKavitaBaseUrlChanged(
            baseUrl = baseUrl,
            updateState = ::updateUiState,
        )
    }

    fun onKavitaUsernameChanged(username: String) {
        remoteSourcesCoordinator.onKavitaUsernameChanged(
            username = username,
            updateState = ::updateUiState,
        )
    }

    fun onKavitaPasswordChanged(password: String) {
        remoteSourcesCoordinator.onKavitaPasswordChanged(
            password = password,
            updateState = ::updateUiState,
        )
    }

    fun onStumpDisplayNameChanged(displayName: String) {
        remoteSourcesCoordinator.onStumpDisplayNameChanged(
            displayName = displayName,
            updateState = ::updateUiState,
        )
    }

    fun onStumpBaseUrlChanged(baseUrl: String) {
        remoteSourcesCoordinator.onStumpBaseUrlChanged(
            baseUrl = baseUrl,
            updateState = ::updateUiState,
        )
    }

    fun onStumpUsernameChanged(username: String) {
        remoteSourcesCoordinator.onStumpUsernameChanged(
            username = username,
            updateState = ::updateUiState,
        )
    }

    fun onStumpPasswordChanged(password: String) {
        remoteSourcesCoordinator.onStumpPasswordChanged(
            password = password,
            updateState = ::updateUiState,
        )
    }

    fun onGrimmoryDisplayNameChanged(displayName: String) {
        remoteSourcesCoordinator.onGrimmoryDisplayNameChanged(
            displayName = displayName,
            updateState = ::updateUiState,
        )
    }

    fun onGrimmoryBaseUrlChanged(baseUrl: String) {
        remoteSourcesCoordinator.onGrimmoryBaseUrlChanged(
            baseUrl = baseUrl,
            updateState = ::updateUiState,
        )
    }

    fun onGrimmoryUsernameChanged(username: String) {
        remoteSourcesCoordinator.onGrimmoryUsernameChanged(
            username = username,
            updateState = ::updateUiState,
        )
    }

    fun onGrimmoryPasswordChanged(password: String) {
        remoteSourcesCoordinator.onGrimmoryPasswordChanged(
            password = password,
            updateState = ::updateUiState,
        )
    }

    fun connectKavitaServer() {
        remoteSourcesCoordinator.connectKavitaServer(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun connectStumpServer() {
        remoteSourcesCoordinator.connectStumpServer(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun connectGrimmoryServer() {
        remoteSourcesCoordinator.connectGrimmoryServer(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun refreshSelectedRemoteLibrary() {
        remoteSourcesCoordinator.refreshSelectedRemoteLibrary(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun refreshKavitaLibrary() {
        remoteSourcesCoordinator.refreshKavitaLibrary(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun refreshStumpLibrary() {
        remoteSourcesCoordinator.refreshStumpLibrary(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun refreshGrimmoryLibrary() {
        remoteSourcesCoordinator.refreshGrimmoryLibrary(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    internal suspend fun importDocument(uri: Uri): Boolean {
        val result = libraryActionsCoordinator.importDocument(uri)
        val importedItem = result.importedItem
        catalogStateCoordinator.updateLibraryState(
            currentState = uiState,
            updateState = ::updateUiState,
            topLevel = if (importedItem != null) TopLevelDestination.Library else uiState.topLevel,
            selectedSource = importedItem?.sourceType ?: uiState.selectedSource,
            selectedItemId = importedItem?.id,
            activeReader = importedItem?.format,
        )
        return importedItem != null
    }

    internal suspend fun deleteImportedItem(item: ReadableItem) {
        val result = libraryActionsCoordinator.deleteImportedItem(item)
        val removed = result.removed
        val isDeletedSelection = removed && uiState.selectedItemId == item.id
        catalogStateCoordinator.updateLibraryState(
            currentState = uiState,
            updateState = ::updateUiState,
            selectedSource = uiState.selectedSource,
            selectedItemId = if (isDeletedSelection) null else uiState.selectedItemId,
            activeReader = if (isDeletedSelection) null else uiState.activeReader,
        )
    }

    private fun observeAppState() {
        comicReadingDirectionController.observePreferences(
            scope = viewModelScope,
            updateState = ::updateUiState,
        )
        remoteSourcesCoordinator.observeStoredSessions(
            scope = viewModelScope,
            stateProvider = { uiState },
            updateState = ::updateUiState,
        )
        readerCoordinator.observeDirtyProgress(
            scope = viewModelScope,
            catalogItemsProvider = catalogStateCoordinator::catalogItems,
            stateProvider = { uiState },
        )
        catalogStateCoordinator.observeCatalog(
            scope = viewModelScope,
            stateProvider = { uiState },
            updateState = ::updateUiState,
        )
        viewModelScope.launch {
            remoteDownloadController.observeEntries().collect { snapshot ->
                downloadsSnapshot = snapshot
                updateUiState { state ->
                    state.copy(
                        downloadingRemoteItemIds = snapshot.activeTrackedItemIds,
                    )
                }
                val availabilitySignature = snapshot.entries
                    .flatMapTo(linkedSetOf()) { entry -> entry.completedItemIds }
                if (lastDownloadedAvailabilitySignature != availabilitySignature) {
                    lastDownloadedAvailabilitySignature = availabilitySignature
                    catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
                        scope = viewModelScope,
                        stateProvider = { uiState },
                        updateState = ::updateUiState,
                        items = uiState.items,
                    )
                }
            }
        }
    }

    private inline fun updateUiState(
        transform: (AppUiState) -> AppUiState,
    ) {
        val previousState = uiState
        uiState = transform(previousState).withDerivedLibraryState(previousState)
    }

}
