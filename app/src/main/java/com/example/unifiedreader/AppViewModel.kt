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
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appPreferencesStore: AppPreferencesStore,
    private val catalogStateCoordinator: AppCatalogStateCoordinator,
    private val libraryActionsCoordinator: AppLibraryActionsCoordinator,
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

    init {
        remoteSourcesCoordinator.initialize(::updateUiState)
        updateUiState { state ->
            state.copy(
                themeMode = appPreferencesStore.preferencesFlow.value.themeMode,
            )
        }
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
        updateUiState { state ->
            state.copy(
                libraryFiltersBySource = state.libraryFiltersBySource + (state.selectedSource to filters),
            )
        }
    }

    fun onLibrarySearchQueryChanged(query: String) {
        updateUiState { state ->
            state.copy(
                librarySearchQueriesBySource = state.librarySearchQueriesBySource + (state.selectedSource to query),
            )
        }
    }

    fun clearLibraryFilters() {
        if (uiState.selectedLibraryFilters.isEmpty) return
        updateUiState { state ->
            state.copy(
                libraryFiltersBySource = state.libraryFiltersBySource - state.selectedSource,
            )
        }
    }

    fun onLibrarySortModeChanged(sortMode: LibrarySortMode) {
        updateUiState { state ->
            state.copy(
                librarySortsBySource = state.librarySortsBySource + (state.selectedSource to sortMode),
            )
        }
    }

    fun onItemSelected(item: ReadableItem) {
        readerCoordinator.onItemSelected(
            item = item,
            updateState = ::updateUiState,
            scope = viewModelScope,
            refreshProgress = ::refreshProgress,
        )
    }

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
            onError = { message ->
                updateLibraryMessage(
                    message = message,
                    severity = LibraryMessageSeverity.Error,
                )
            },
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

    fun onDownloadRemoteItem(item: ReadableItem) {
        if (item.id in uiState.downloadingRemoteItemIds) return
        updateUiState { state ->
            state.copy(
                downloadingRemoteItemIds = state.downloadingRemoteItemIds + item.id,
                libraryMessage = "Downloading ${item.libraryDisplayLabel()}.",
                libraryMessageSeverity = LibraryMessageSeverity.Info,
            )
        }
        viewModelScope.launch {
            runCatching {
                libraryActionsCoordinator.downloadRemoteItem(item, uiState.items)
            }.onSuccess { result ->
                updateUiState { state ->
                    state.copy(
                        downloadedRemoteItemIds = result.downloadedRemoteItemIds,
                        libraryMessage = result.libraryMessage,
                        libraryMessageSeverity = result.libraryMessageSeverity,
                    )
                }
            }.onFailure { throwable ->
                updateLibraryMessage(
                    message = "Unable to download ${item.libraryDisplayLabel()}: ${throwable.userFacingMessage()}",
                    severity = LibraryMessageSeverity.Error,
                )
            }
            updateUiState { state ->
                state.copy(
                    downloadingRemoteItemIds = state.downloadingRemoteItemIds - item.id,
                )
            }
        }
    }

    fun onDeleteRemoteLocalFile(item: ReadableItem) {
        viewModelScope.launch {
            val result = libraryActionsCoordinator.deleteRemoteLocalFile(item, uiState.items)
            updateUiState { state ->
                state.copy(
                    downloadedRemoteItemIds = result.downloadedRemoteItemIds,
                    libraryMessage = result.libraryMessage,
                    libraryMessageSeverity = result.libraryMessageSeverity,
                )
            }
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        if (uiState.themeMode == themeMode) return
        updateUiState { state -> state.copy(themeMode = themeMode) }
        viewModelScope.launch {
            appPreferencesStore.setThemeMode(themeMode)
        }
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

    internal suspend fun importDocument(uri: Uri) {
        val result = libraryActionsCoordinator.importDocument(uri)
        catalogStateCoordinator.updateLibraryState(
            currentState = uiState,
            updateState = ::updateUiState,
            topLevel = TopLevelDestination.Library,
            selectedSource = result.importedItem?.sourceType ?: uiState.selectedSource,
            selectedItemId = result.importedItem?.id,
            activeReader = result.importedItem?.format,
            libraryMessage = result.libraryMessage,
            libraryMessageSeverity = result.libraryMessageSeverity,
        )
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
            libraryMessage = deletionMessage(item, result.outcome),
            libraryMessageSeverity = if (removed) {
                LibraryMessageSeverity.Info
            } else {
                LibraryMessageSeverity.Error
            },
        )
    }

    fun clearLibraryMessage() {
        if (uiState.libraryMessage != null) {
            updateUiState { state ->
                state.copy(
                    libraryMessage = null,
                    libraryMessageSeverity = LibraryMessageSeverity.Info,
                )
            }
        }
    }

    private fun observeAppState() {
        remoteSourcesCoordinator.observeStoredSessions(
            scope = viewModelScope,
            stateProvider = { uiState },
            updateState = ::updateUiState,
        )
        readerCoordinator.observeDirtyProgress(
            scope = viewModelScope,
            catalogItemsProvider = catalogStateCoordinator::catalogItems,
        )
        catalogStateCoordinator.observeCatalog(
            scope = viewModelScope,
            stateProvider = { uiState },
            updateState = ::updateUiState,
        )
    }

    private inline fun updateUiState(
        transform: (AppUiState) -> AppUiState,
    ) {
        val previousState = uiState
        uiState = transform(previousState).withDerivedLibraryState(previousState)
    }

    private fun updateLibraryMessage(
        message: String?,
        severity: LibraryMessageSeverity,
    ) {
        updateUiState { state ->
            state.copy(
                libraryMessage = message,
                libraryMessageSeverity = severity,
            )
        }
    }
}
