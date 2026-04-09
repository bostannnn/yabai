package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
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
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val observeAppCatalogSnapshotsUseCase: ObserveAppCatalogSnapshotsUseCase,
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val deleteImportedItemUseCase: DeleteImportedItemUseCase,
    private val downloadRemoteItemUseCase: DownloadRemoteItemUseCase,
    private val deleteRemoteLocalFileUseCase: DeleteRemoteLocalFileUseCase,
    private val kavitaCoordinator: AppKavitaCoordinator,
    private val stumpCoordinator: AppStumpCoordinator,
    private val grimmoryCoordinator: AppGrimmoryCoordinator,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val stumpCredentialStore: StumpCredentialStore,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val stumpCoverCacheController: StumpCoverCacheController,
    private val grimmoryCoverCacheController: GrimmoryCoverCacheController,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
    val comicPageSource: ComicPageSource,
    val comicReaderProgressStore: ComicReaderProgressStore,
    val comicReaderPreferencesStore: ComicReaderPreferencesStore,
    val readerSessionStore: ReaderSessionStore,
    private val sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
    val readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory>,
) : AndroidViewModel(application) {
    private var catalogLibraries: List<Library> = libraryRepository.getLibraries()
    private var catalogItems: List<ReadableItem> = readableItemRepository.getItems()
    private var readerSelectionRequestId: Int = 0
    private val libraryScrollPositionByContext =
        object : LinkedHashMap<String, LibraryScrollPosition>(LIBRARY_SCROLL_POSITION_CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, LibraryScrollPosition>,
            ): Boolean = size > LIBRARY_SCROLL_POSITION_CACHE_LIMIT
        }

    var uiState by mutableStateOf(AppUiState())
        private set

    init {
        updateKavitaConnection { connection ->
            connection.withStoredSession(kavitaCredentialStore.sessionFlow.value)
        }
        updateStumpConnection { connection ->
            connection.withStoredSession(stumpCredentialStore.sessionFlow.value)
        }
        updateGrimmoryConnection { connection ->
            connection.withStoredSession(grimmoryCredentialStore.sessionFlow.value)
        }
        updateUiState { state ->
            state.copy(
                themeMode = appPreferencesStore.preferencesFlow.value.themeMode,
            )
        }
        updateLibraryState()
        observeAppState()
    }

    fun onTopLevelSelected(destination: TopLevelDestination) {
        readerSelectionRequestId++
        updateUiState { state -> state.copy(topLevel = destination) }
        refreshProgress()
    }

    fun onSourceSelected(sourceType: SourceType) {
        readerSelectionRequestId++
        updateUiState { state ->
            state.copy(
                selectedSource = sourceType,
                selectedItemId = null,
                activeReader = null,
                selectedKavitaSeriesKey = null,
                selectedStumpSeriesKey = null,
            )
        }
        refreshProgress()
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
        if (item.isKavitaSeriesContainer()) {
            updateUiState { state ->
                state.copy(
                    selectedKavitaSeriesKey = item.remoteId,
                    selectedStumpSeriesKey = null,
                    selectedItemId = null,
                    activeReader = null,
                )
            }
            refreshProgress()
            return
        }
        if (item.isStumpSeriesContainer()) {
            updateUiState { state ->
                state.copy(
                    selectedStumpSeriesKey = item.remoteId,
                    selectedKavitaSeriesKey = null,
                    selectedItemId = null,
                    activeReader = null,
                )
            }
            refreshProgress()
            return
        }
        val progressSync = sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
        val requestId = ++readerSelectionRequestId
        updateUiState { state ->
            state.copy(
                selectedItemId = item.id,
                activeReader = item.format,
            )
        }
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

    fun libraryScrollPositionFor(contextKey: String): LibraryScrollPosition {
        return libraryScrollPositionByContext[contextKey] ?: LibraryScrollPosition()
    }

    fun onLibraryScrollPositionChanged(
        contextKey: String,
        position: LibraryScrollPosition,
    ) {
        if (libraryScrollPositionByContext[contextKey] == position) return
        libraryScrollPositionByContext[contextKey] = position
    }

    fun coverImageUrlFor(item: ReadableItem): String? {
        return when (item.sourceType) {
            SourceType.STUMP -> stumpCoverCacheController.coverImageUrlFor(item)
            SourceType.GRIMMORY -> grimmoryCoverCacheController.coverImageUrlFor(item)
            SourceType.KAVITA,
            SourceType.LOCAL -> item.coverUrl
        }
    }

    fun ensureCoverCached(item: ReadableItem) {
        when (item.sourceType) {
            SourceType.STUMP -> stumpCoverCacheController.ensureCoverCached(
                item = item,
                scope = viewModelScope,
                onError = { message ->
                    updateLibraryMessage(
                        message = message,
                        severity = LibraryMessageSeverity.Error,
                    )
                },
            )
            SourceType.GRIMMORY -> grimmoryCoverCacheController.ensureCoverCached(
                item = item,
                scope = viewModelScope,
                onError = { message ->
                    updateLibraryMessage(
                        message = message,
                        severity = LibraryMessageSeverity.Error,
                    )
                },
            )
            SourceType.KAVITA,
            SourceType.LOCAL -> Unit
        }
    }

    fun onBackPressed() {
        readerSelectionRequestId++
        val wasReaderOpen = uiState.activeReader != null && uiState.selectedItem != null
        updateUiState { state ->
            when {
                state.activeReader != null -> state.copy(
                    activeReader = null,
                    selectedItemId = null,
                )
                state.selectedKavitaSeriesKey != null -> state.copy(
                    selectedKavitaSeriesKey = null,
                )
                state.selectedStumpSeriesKey != null -> state.copy(
                    selectedStumpSeriesKey = null,
                )
                state.selectedItemId != null -> state.copy(selectedItemId = null)
                else -> state
            }
        }
        if (wasReaderOpen) {
            viewModelScope.launch {
                flushPendingRemoteProgress()
            }
        }
        refreshProgress()
    }

    fun refreshProgress() {
        updateUiState { state ->
            state.copy(
                progressByItemId = libraryStateAssembler.buildProgressSummaries(
                    items = state.items,
                    progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                    sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                ),
            )
        }
        refreshDownloadedRemoteItemIdsAsync(uiState.items)
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
                downloadRemoteItemUseCase(item, uiState.items)
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
            val result = deleteRemoteLocalFileUseCase(item, uiState.items)
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
        updateKavitaConnectionInput { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onKavitaBaseUrlChanged(baseUrl: String) {
        updateKavitaConnectionInput { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onKavitaUsernameChanged(username: String) {
        updateKavitaConnectionInput { connection ->
            connection.copy(username = username)
        }
    }

    fun onKavitaPasswordChanged(password: String) {
        updateKavitaConnection { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun onStumpDisplayNameChanged(displayName: String) {
        updateStumpConnectionInput { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onStumpBaseUrlChanged(baseUrl: String) {
        updateStumpConnectionInput { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onStumpUsernameChanged(username: String) {
        updateStumpConnectionInput { connection ->
            connection.copy(username = username)
        }
    }

    fun onStumpPasswordChanged(password: String) {
        updateStumpConnection { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun onGrimmoryDisplayNameChanged(displayName: String) {
        updateGrimmoryConnectionInput { connection ->
            connection.copy(displayName = displayName)
        }
    }

    fun onGrimmoryBaseUrlChanged(baseUrl: String) {
        updateGrimmoryConnectionInput { connection ->
            connection.copy(baseUrl = baseUrl)
        }
    }

    fun onGrimmoryUsernameChanged(username: String) {
        updateGrimmoryConnectionInput { connection ->
            connection.copy(username = username)
        }
    }

    fun onGrimmoryPasswordChanged(password: String) {
        updateGrimmoryConnection { connection ->
            connection.copy(
                password = password,
                message = null,
                isError = false,
            )
        }
    }

    fun connectKavitaServer() {
        if (uiState.kavitaConnection.hasStoredSession && uiState.kavitaConnection.password.isBlank()) {
            refreshKavitaLibrary()
            return
        }
        when (val preparation = kavitaCoordinator.prepareConnect(uiState.kavitaConnection)) {
            is KavitaSyncPreparation.Invalid -> {
                updateUiState { state -> state.copy(kavitaConnection = preparation.connection) }
            }
            KavitaSyncPreparation.MissingSession -> Unit
            is KavitaSyncPreparation.Ready -> {
                updateUiState { state ->
                    state.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = null,
                            syncProgressLabel = null,
                            message = "Connecting to Kavita.",
                            isError = false,
                        ),
                    )
                }
                startKavitaConnect(preparation)
            }
        }
    }

    fun connectStumpServer() {
        if (uiState.stumpConnection.hasStoredSession && uiState.stumpConnection.password.isBlank()) {
            refreshStumpLibrary()
            return
        }
        when (val preparation = stumpCoordinator.prepareConnect(uiState.stumpConnection)) {
            is StumpSyncPreparation.Invalid -> {
                updateUiState { state -> state.copy(stumpConnection = preparation.connection) }
            }
            StumpSyncPreparation.MissingSession -> Unit
            is StumpSyncPreparation.Ready -> {
                updateUiState { state ->
                    state.copy(
                        stumpConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = null,
                            syncProgressLabel = null,
                            message = "Connecting to Stump.",
                            isError = false,
                        ),
                    )
                }
                startStumpConnect(preparation)
            }
        }
    }

    fun connectGrimmoryServer() {
        when (val preparation = grimmoryCoordinator.prepareConnect(uiState.grimmoryConnection)) {
            is GrimmorySyncPreparation.Invalid -> {
                updateUiState { state -> state.copy(grimmoryConnection = preparation.connection) }
            }
            GrimmorySyncPreparation.MissingSession -> Unit
            is GrimmorySyncPreparation.Ready -> {
                updateUiState { state ->
                    state.copy(
                        grimmoryConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            message = "Connecting to Grimmory.",
                            isError = false,
                        ),
                    )
                }
                startGrimmorySync(preparation)
            }
        }
    }

    fun refreshSelectedRemoteLibrary() {
        when (uiState.selectedSource) {
            SourceType.KAVITA -> refreshKavitaLibrary()
            SourceType.STUMP -> refreshStumpLibrary()
            SourceType.GRIMMORY -> refreshGrimmoryLibrary()
            SourceType.LOCAL -> Unit
        }
    }

    fun refreshKavitaLibrary() {
        when (val preparation = kavitaCoordinator.prepareRefresh(uiState.kavitaConnection)) {
            KavitaSyncPreparation.MissingSession -> {
                updateUiState { state ->
                    state.copy(
                        libraryMessage = "Connect to Kavita before syncing the library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        kavitaConnection = state.kavitaConnection.copy(
                            message = "Connect to Kavita before syncing the library.",
                            isError = true,
                        ),
                    )
                }
            }
            is KavitaSyncPreparation.Invalid -> {
                updateUiState { state ->
                    state.copy(
                        libraryMessage = preparation.connection.message,
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        kavitaConnection = preparation.connection,
                    )
                }
            }
            is KavitaSyncPreparation.Ready -> {
                updateUiState { state ->
                    state.copy(
                        kavitaConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = 0f,
                            syncProgressLabel = "Preparing Kavita sync...",
                            message = "Syncing Kavita library.",
                            isError = false,
                        ),
                        libraryMessage = "Syncing Kavita library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                    )
                }
                startKavitaSync(preparation)
            }
        }
    }

    fun refreshStumpLibrary() {
        when (val preparation = stumpCoordinator.prepareRefresh(uiState.stumpConnection)) {
            StumpSyncPreparation.MissingSession -> {
                updateUiState { state ->
                    state.copy(
                        libraryMessage = "Connect to Stump before syncing the library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        stumpConnection = state.stumpConnection.copy(
                            message = "Connect to Stump before syncing the library.",
                            isError = true,
                        ),
                    )
                }
            }
            is StumpSyncPreparation.Invalid -> {
                updateUiState { state ->
                    state.copy(
                        libraryMessage = preparation.connection.message,
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        stumpConnection = preparation.connection,
                    )
                }
            }
            is StumpSyncPreparation.Ready -> {
                updateUiState { state ->
                    state.copy(
                        stumpConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            syncProgressFraction = 0f,
                            syncProgressLabel = "Preparing Stump sync...",
                            message = "Syncing Stump library.",
                            isError = false,
                        ),
                        libraryMessage = "Syncing Stump library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                    )
                }
                startStumpSync(preparation)
            }
        }
    }

    fun refreshGrimmoryLibrary() {
        when (val preparation = grimmoryCoordinator.prepareRefresh(uiState.grimmoryConnection)) {
            GrimmorySyncPreparation.MissingSession -> {
                updateUiState { state ->
                    state.copy(
                        libraryMessage = "Connect to Grimmory before syncing the library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                        grimmoryConnection = state.grimmoryConnection.copy(
                            message = "Connect to Grimmory before syncing the library.",
                            isError = true,
                        ),
                    )
                }
            }
            is GrimmorySyncPreparation.Invalid -> Unit
            is GrimmorySyncPreparation.Ready -> {
                updateUiState { state ->
                    state.copy(
                        grimmoryConnection = preparation.activeConnection.copy(
                            isConnecting = true,
                            message = "Syncing Grimmory library.",
                            isError = false,
                        ),
                        libraryMessage = "Syncing Grimmory library.",
                        libraryMessageSeverity = LibraryMessageSeverity.Info,
                    )
                }
                startGrimmorySync(preparation)
            }
        }
    }

    private fun startKavitaSync(
        preparation: KavitaSyncPreparation.Ready,
    ) {
        viewModelScope.launch {
            when (
                val result = kavitaCoordinator.executeSync(
                    preparation = preparation,
                    onProgress = { progress ->
                        updateUiState { state ->
                            state.copy(
                                kavitaConnection = state.kavitaConnection.copy(
                                    isConnecting = true,
                                    syncProgressFraction = progress.fraction.coerceIn(0f, 1f),
                                    syncProgressLabel = progress.message,
                                    message = "Syncing Kavita library.",
                                    isError = false,
                                ),
                                libraryMessage = progress.message,
                                libraryMessageSeverity = LibraryMessageSeverity.Info,
                            )
                        }
                    },
                )
            ) {
                is KavitaSyncExecutionResult.Success -> {
                    clearKavitaPageCache()
                    updateUiState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.KAVITA,
                            selectedItemId = null,
                            activeReader = null,
                            selectedKavitaSeriesKey = null,
                            libraryMessage = "Synced ${result.itemCount} Kavita items from ${result.libraryCount} libraries.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            kavitaConnection = result.connection,
                        )
                    }
                }
                is KavitaSyncExecutionResult.Failure -> {
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            kavitaConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startKavitaConnect(
        preparation: KavitaSyncPreparation.Ready,
    ) {
        viewModelScope.launch {
            when (val result = kavitaCoordinator.executeConnect(preparation)) {
                is KavitaConnectExecutionResult.Success -> {
                    clearKavitaPageCache()
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = "Connected to Kavita. ${result.libraryCount} libraries available to sync.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            kavitaConnection = result.connection,
                        )
                    }
                }
                is KavitaConnectExecutionResult.Failure -> {
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            kavitaConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startStumpSync(
        preparation: StumpSyncPreparation.Ready,
    ) {
        viewModelScope.launch {
            when (
                val result = stumpCoordinator.executeSync(
                    preparation = preparation,
                    clearCoverState = ::clearStumpCoverState,
                    onProgress = { progress ->
                        updateUiState { state ->
                            state.copy(
                                stumpConnection = state.stumpConnection.copy(
                                    isConnecting = true,
                                    syncProgressFraction = progress.fraction.coerceIn(0f, 1f),
                                    syncProgressLabel = progress.message,
                                    message = "Syncing Stump library.",
                                    isError = false,
                                ),
                                libraryMessage = progress.message,
                                libraryMessageSeverity = LibraryMessageSeverity.Info,
                            )
                        }
                    },
                )
            ) {
                is StumpSyncExecutionResult.Success -> {
                    clearStumpPageCache()
                    updateUiState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.STUMP,
                            selectedItemId = null,
                            activeReader = null,
                            selectedKavitaSeriesKey = null,
                            selectedStumpSeriesKey = null,
                            libraryMessage = "Synced ${result.itemCount} Stump items from ${result.libraryCount} libraries.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            stumpConnection = result.connection,
                        )
                    }
                }
                is StumpSyncExecutionResult.Failure -> {
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            stumpConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startStumpConnect(
        preparation: StumpSyncPreparation.Ready,
    ) {
        viewModelScope.launch {
            when (val result = stumpCoordinator.executeConnect(preparation)) {
                is StumpConnectExecutionResult.Success -> {
                    clearStumpPageCache()
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = "Connected to Stump. ${result.libraryCount} libraries available to sync.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            stumpConnection = result.connection,
                        )
                    }
                }
                is StumpConnectExecutionResult.Failure -> {
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            stumpConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    private fun startGrimmorySync(
        preparation: GrimmorySyncPreparation.Ready,
    ) {
        viewModelScope.launch {
            when (val result = grimmoryCoordinator.executeSync(preparation, ::clearGrimmoryCoverState)) {
                is GrimmorySyncExecutionResult.Success -> {
                    updateUiState { state ->
                        state.copy(
                            topLevel = TopLevelDestination.Library,
                            selectedSource = SourceType.GRIMMORY,
                            selectedItemId = null,
                            activeReader = null,
                            libraryMessage = "Synced ${result.itemCount} Grimmory books from ${result.libraryCount} libraries.",
                            libraryMessageSeverity = LibraryMessageSeverity.Info,
                            grimmoryConnection = result.connection,
                        )
                    }
                }
                is GrimmorySyncExecutionResult.Failure -> {
                    updateUiState { state ->
                        state.copy(
                            libraryMessage = result.message,
                            libraryMessageSeverity = LibraryMessageSeverity.Error,
                            grimmoryConnection = result.connection,
                        )
                    }
                }
            }
        }
    }

    internal suspend fun importDocument(uri: Uri) {
        val result = importDocumentUseCase(uri)
        updateLibraryState(
            topLevel = TopLevelDestination.Library,
            selectedSource = result.importedItem?.sourceType ?: uiState.selectedSource,
            selectedItemId = result.importedItem?.id,
            activeReader = result.importedItem?.format,
            libraryMessage = result.libraryMessage,
            libraryMessageSeverity = result.libraryMessageSeverity,
        )
    }

    internal suspend fun deleteImportedItem(item: ReadableItem) {
        val result = deleteImportedItemUseCase(item)
        val removed = result.removed
        val isDeletedSelection = removed && uiState.selectedItemId == item.id
        updateLibraryState(
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

    private fun updateLibraryState(
        topLevel: TopLevelDestination = uiState.topLevel,
        selectedSource: SourceType = uiState.selectedSource,
        selectedItemId: String? = uiState.selectedItemId,
        activeReader: FormatType? = uiState.activeReader,
        libraryMessage: String? = uiState.libraryMessage,
        libraryMessageSeverity: LibraryMessageSeverity = uiState.libraryMessageSeverity,
        themeMode: ThemeMode = uiState.themeMode,
        selectedKavitaSeriesKey: String? = uiState.selectedKavitaSeriesKey,
        selectedStumpSeriesKey: String? = uiState.selectedStumpSeriesKey,
    ) {
        val items = catalogItems + localDocumentRepository.getImportedItems()
        updateUiState { state ->
            state.copy(
                topLevel = topLevel,
                selectedSource = selectedSource,
                libraries = catalogLibraries,
                items = items,
                selectedItemId = selectedItemId,
                activeReader = activeReader,
                selectedKavitaSeriesKey = selectedKavitaSeriesKey?.takeIf { seriesKey ->
                    selectedSource == SourceType.KAVITA && items.any { item -> item.belongsToKavitaSeries(seriesKey) }
                },
                selectedStumpSeriesKey = selectedStumpSeriesKey?.takeIf { seriesKey ->
                    selectedSource == SourceType.STUMP && items.any { item -> item.belongsToStumpSeries(seriesKey) }
                },
                progressByItemId = libraryStateAssembler.buildProgressSummaries(
                    items = items,
                    progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                    sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                ),
                libraryMessage = libraryMessage,
                libraryMessageSeverity = libraryMessageSeverity,
                themeMode = themeMode,
            )
        }
        refreshDownloadedRemoteItemIdsAsync(items)
    }

    @OptIn(FlowPreview::class)
    private fun observeAppState() {
        viewModelScope.launch {
            kavitaCredentialStore.sessionFlow.collect { session ->
                if (!uiState.kavitaConnection.isConnecting) {
                    updateKavitaConnection { connection ->
                        kavitaCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            stumpCredentialStore.sessionFlow.collect { session ->
                if (!uiState.stumpConnection.isConnecting) {
                    updateStumpConnection { connection ->
                        stumpCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            grimmoryCredentialStore.sessionFlow.collect { session ->
                if (!uiState.grimmoryConnection.isConnecting) {
                    updateGrimmoryConnection { connection ->
                        grimmoryCoordinator.refreshedConnectionState(
                            connection.withStoredSession(session),
                        )
                    }
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
            observeAppCatalogSnapshotsUseCase.observe().collect { snapshot ->
                catalogLibraries = snapshot.libraries
                catalogItems = snapshot.catalogItems
                val activeItemIds = snapshot.items.mapTo(mutableSetOf(), ReadableItem::id)
                stumpCoverCacheController.removeMissingItems(activeItemIds)
                grimmoryCoverCacheController.removeMissingItems(activeItemIds)
                updateUiState { state ->
                    state.copy(
                        libraries = snapshot.libraries,
                        items = snapshot.items,
                        progressByItemId = snapshot.progressByItemId,
                        downloadedRemoteItemIds = snapshot.downloadedRemoteItemIds,
                        hydratedSources = snapshot.hydratedSources,
                        themeMode = snapshot.themeMode,
                        selectedKavitaSeriesKey = state.selectedKavitaSeriesKey?.takeIf { seriesKey ->
                            state.selectedSource == SourceType.KAVITA &&
                                snapshot.items.any { item -> item.belongsToKavitaSeries(seriesKey) }
                        },
                        selectedStumpSeriesKey = state.selectedStumpSeriesKey?.takeIf { seriesKey ->
                            state.selectedSource == SourceType.STUMP &&
                                snapshot.items.any { item -> item.belongsToStumpSeries(seriesKey) }
                        },
                    )
                }
            }
        }
    }

    private fun refreshDownloadedRemoteItemIdsAsync(
        items: List<ReadableItem>,
    ) {
        val requestedItemIds = items.mapTo(mutableSetOf(), ReadableItem::id)
        viewModelScope.launch {
            val downloadedIds = libraryStateAssembler.loadDownloadedRemoteItemIds(items)
            val currentItemIds = uiState.items.mapTo(mutableSetOf(), ReadableItem::id)
            if (currentItemIds == requestedItemIds) {
                updateUiState { state ->
                    state.copy(
                        downloadedRemoteItemIds = downloadedIds,
                    )
                }
            }
        }
    }

    private inline fun updateUiState(
        transform: (AppUiState) -> AppUiState,
    ) {
        uiState = transform(uiState)
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

    private inline fun updateKavitaConnection(
        transform: (KavitaConnectionUiState) -> KavitaConnectionUiState,
    ) {
        updateUiState { state ->
            state.copy(
                kavitaConnection = transform(state.kavitaConnection),
            )
        }
    }

    private inline fun updateKavitaConnectionInput(
        transform: (KavitaConnectionUiState) -> KavitaConnectionUiState,
    ) {
        updateKavitaConnection { connection ->
            kavitaCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }

    private inline fun updateGrimmoryConnection(
        transform: (GrimmoryConnectionUiState) -> GrimmoryConnectionUiState,
    ) {
        updateUiState { state ->
            state.copy(
                grimmoryConnection = transform(state.grimmoryConnection),
            )
        }
    }

    private inline fun updateGrimmoryConnectionInput(
        transform: (GrimmoryConnectionUiState) -> GrimmoryConnectionUiState,
    ) {
        updateGrimmoryConnection { connection ->
            grimmoryCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
        }
    }

    private inline fun updateStumpConnection(
        transform: (StumpConnectionUiState) -> StumpConnectionUiState,
    ) {
        updateUiState { state ->
            state.copy(
                stumpConnection = transform(state.stumpConnection),
            )
        }
    }

    private inline fun updateStumpConnectionInput(
        transform: (StumpConnectionUiState) -> StumpConnectionUiState,
    ) {
        updateStumpConnection { connection ->
            stumpCoordinator.refreshedConnectionState(
                transform(connection).copy(
                    message = null,
                    isError = false,
                ),
            )
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

    private fun clearStumpCoverState() {
        stumpCoverCacheController.clear()
    }

    private fun clearGrimmoryCoverState() {
        grimmoryCoverCacheController.clear()
    }

    private fun clearKavitaPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.KAVITA)
    }

    private fun clearStumpPageCache() {
        comicPageSourceCacheController.clearSourcePageCache(SourceType.STUMP)
    }
}

private const val LIBRARY_SCROLL_POSITION_CACHE_LIMIT = 64
private const val LOG_TAG = "UnifiedReader"
