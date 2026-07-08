package com.example.unifiedreader

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.JournalEntrySaveUseCase
import com.example.unifiedreader.feature.downloads.DownloadEntriesSnapshot
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.navigation.SourceOrderMoveDirection
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class AppViewModel @Inject constructor(
    private val navigationController: AppNavigationController,
    private val activeRouteStore: AppActiveRouteStore,
    private val initialUiStateFactory: AppInitialUiStateFactory,
    private val libraryBrowseController: AppLibraryBrowseController,
    private val settingsController: AppSettingsController,
    private val remoteDownloadController: AppRemoteDownloadController,
    private val catalogStateCoordinator: AppCatalogStateCoordinator,
    private val libraryActionsCoordinator: AppLibraryActionsCoordinator,
    private val libraryReadStatusController: AppLibraryReadStatusController,
    private val comicReadingDirectionController: AppComicReadingDirectionController,
    private val comicReaderModeController: AppComicReaderModeController,
    private val readerCoordinator: AppReaderCoordinator,
    private val remoteSourcesCoordinator: AppRemoteSourcesCoordinator,
    private val kavitaReadingListController: AppKavitaReadingListController,
    private val remoteSourceConnectivityStartupCoordinator: RemoteSourceConnectivityStartupCoordinator,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    private val appNetworkConnectivityMonitor: AppNetworkConnectivityMonitor,
    private val progressSyncNoticeController: AppProgressSyncNoticeController,
    private val journalEntrySaveUseCase: JournalEntrySaveUseCase,
    private val journalRepository: JournalRepository,
    private val progressSyncStartupRetryCoordinator: AppProgressSyncStartupRetryCoordinator,
    private val coverPreloadController: AppCoverPreloadController,
    private val comicPageSourceCacheController: ComicPageSourceCacheController,
    val comicPageSource: ComicPageSource,
    val comicReaderProgressStore: ComicReaderProgressStore,
    val comicReaderPreferencesStore: ComicReaderPreferencesStore,
    val readerSessionStore: ReaderSessionStore,
    val readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory>,
    val readerWorkCoordinator: ReaderWorkCoordinator,
    private val libraryStateRecomputeCoordinator: AppLibraryStateRecomputeCoordinator,
    private val coldStartLibraryViewCoordinator: AppColdStartLibraryViewCoordinator,
    private val libraryGridPager: LibraryGridPager,
) : ViewModel() {
    var uiState by mutableStateOf(initialUiStateFactory.create())
        private set
    var downloadsSnapshot by mutableStateOf(DownloadEntriesSnapshot(emptyList()))
        private set
    var isDownloadsSheetVisible by mutableStateOf(false)
        private set
    var isUnsupportedImportDialogVisible by mutableStateOf(false)
        private set
    private var lastDownloadedAvailabilitySignature: Set<String>? = null
    private var lastNetworkPendingRetryFingerprint: String? = null
    private var lastNetworkAvailability: Boolean? = null

    init {
        viewModelScope.launch { remoteSourceConnectivityStartupCoordinator.checkConfiguredSources() }
        remoteSourcesCoordinator.initialize(::updateUiState)
        settingsController.restoreState(updateState = ::updateUiState)
        libraryBrowseController.restoreState(updateState = ::updateUiState)
        coldStartLibraryViewCoordinator.restore(viewModelScope, uiState.selectedSource, ::updateUiState)
        observeAppState()
        coldStartLibraryViewCoordinator.startCapture(viewModelScope, snapshotFlow { uiState })
        viewModelScope.launch { initialUiStateFactory.hydrateAfterInitialState() }
        if (LibraryPagingFeature.ENABLED) {
            viewModelScope.launch { libraryGridPager.rebuildGridRootsIfEmpty() }
        }
    }

    /**
     * Paged library grid for [source] in a column [sortMode], cached in the ViewModel scope. Thin
     * delegation to the owning repository; only consumed while [LibraryPagingFeature] is enabled.
     */
    fun libraryGridPagingData(
        source: SourceType,
        sortMode: LibrarySortMode,
    ): Flow<PagingData<ReadableItem>> =
        libraryGridPager.gridRootsPagingData(source, sortMode).cachedIn(viewModelScope)

    fun onTopLevelSelected(destination: TopLevelDestination) {
        readerCoordinator.onTopLevelSelected(
            destination = destination,
            updateState = ::updateUiState,
        )
    }

    fun openLibrarySource(sourceType: SourceType) {
        readerCoordinator.onLibrarySourceSelected(
            sourceType = sourceType,
            updateState = ::updateUiState,
        )
    }

    fun openKavitaLibrarySection(section: KavitaLibrarySection) {
        readerCoordinator.onKavitaLibrarySectionSelected(
            section = section,
            updateState = ::updateUiState,
        )
    }

    fun onSourceSelected(sourceType: SourceType) {
        openLibrarySource(sourceType)
    }

    fun moveSourceUp(sourceType: SourceType) {
        navigationController.moveSource(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            sourceType = sourceType,
            direction = SourceOrderMoveDirection.Up,
        )
    }

    fun moveSourceDown(sourceType: SourceType) {
        navigationController.moveSource(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            sourceType = sourceType,
            direction = SourceOrderMoveDirection.Down,
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

    fun onKavitaReadingListSelected(readingListId: String?) {
        kavitaReadingListController.onReadingListSelected(
            readingListId = readingListId,
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
        readerCoordinator.clearLibraryScrollPosition(uiState.libraryBrowseContextKey)
    }

    fun onLibrarySortModeChanged(sortMode: LibrarySortMode) {
        libraryBrowseController.onLibrarySortModeChanged(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            sortMode = sortMode,
        )
        readerCoordinator.clearLibraryScrollPosition(uiState.libraryBrowseContextKey)
        updateUiState { state ->
            state.copy(libraryScrollToTopRequest = state.libraryScrollToTopRequest + 1L)
        }
    }

    fun onLibraryGridCellSizeChanged(gridCellSizeDp: Int) {
        libraryBrowseController.onLibraryGridCellSizeChanged(
            updateState = ::updateUiState,
            scope = viewModelScope,
            gridCellSizeDp = gridCellSizeDp,
        )
    }

    fun onLibraryFadeReadItemsChanged(fadeReadItems: Boolean) {
        libraryBrowseController.onLibraryFadeReadItemsChanged(
            updateState = ::updateUiState,
            scope = viewModelScope,
            fadeReadItems = fadeReadItems,
        )
    }

    fun onLibraryFilterPanelsChanged(
        sourceType: SourceType,
        panels: List<LibraryFilterPanel>,
    ) {
        libraryBrowseController.onLibraryFilterPanelsChanged(
            currentState = uiState,
            updateState = ::updateUiState,
            scope = viewModelScope,
            sourceType = sourceType,
            panels = panels,
        )
    }

    fun onItemSelected(item: ReadableItem) {
        readerCoordinator.onItemSelected(
            item = item,
            updateState = ::updateUiState,
            scope = viewModelScope,
            refreshProgress = ::refreshProgress,
            onSyncFailures = ::reportProgressSyncFailures,
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

    fun onLibraryReaderModeSelected(
        libraryId: String,
        mode: ComicReaderMode,
    ) {
        comicReaderModeController.onLibraryModeSelected(
            scope = viewModelScope,
            libraryId = libraryId,
            mode = mode,
        )
    }

    fun onSeriesReaderModeSelected(
        item: ReadableItem,
        mode: ComicReaderMode,
    ) {
        comicReaderModeController.onSeriesModeSelected(
            scope = viewModelScope,
            item = item,
            mode = mode,
        )
    }

    fun seriesReaderModeFor(
        item: ReadableItem,
    ): ComicReaderMode? = comicReaderModeController.effectiveSeriesMode(
        item = item,
        currentPreferences = uiState.comicReaderModePreferences,
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

    fun coverImageUrlFor(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): String? {
        return libraryActionsCoordinator.coverImageUrlFor(item, variant)
    }

    fun prepareCoverSource(
        sourceType: SourceType,
    ) {
        libraryActionsCoordinator.prepareCoverSource(sourceType)
    }

    fun coverPrefetchKeyFor(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): String? {
        return libraryActionsCoordinator.coverPrefetchKeyFor(item, variant)
    }

    fun ensureCoverCached(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ) {
        libraryActionsCoordinator.ensureCoverCached(
            item = item,
            variant = variant,
            scope = viewModelScope,
            onError = {},
        )
    }

    fun onLibraryViewportScrollingChanged(
        isScrolling: Boolean,
    ) {
        libraryActionsCoordinator.onLibraryViewportScrollingChanged(isScrolling)
    }

    fun onBackPressed() {
        trimReaderCachesBeforeLeavingReader()
        readerCoordinator.onBackPressed(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            catalogItemsProvider = catalogStateCoordinator::catalogItems,
            refreshProgress = ::refreshProgress,
            onSyncFailures = ::reportProgressSyncFailures,
        )
    }

    fun onReaderNextRequested() {
        readerCoordinator.onReaderNextRequested(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            refreshProgress = ::refreshProgress,
            onSyncFailures = ::reportProgressSyncFailures,
        )
    }

    fun onReaderCompletionRequested() {
        readerCoordinator.onReaderCompletionRequested(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            refreshProgress = ::refreshProgress,
        )
    }

    fun dismissReaderCompletion() {
        readerCoordinator.dismissReaderCompletion(updateState = ::updateUiState)
    }

    fun startReaderCompletionNextItem() {
        readerCoordinator.startReaderCompletionNextItem(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            refreshProgress = ::refreshProgress,
            onSyncFailures = ::reportProgressSyncFailures,
        )
    }

    fun refreshProgress() {
        readerCoordinator.refreshProgress(
            scope = viewModelScope,
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

    fun flushPendingRemoteProgress() {
        readerCoordinator.flushPendingProgress(
            catalogItems = catalogStateCoordinator.catalogItems(),
            selectedItem = uiState.selectedItem,
            scope = viewModelScope,
            onSyncFailures = ::reportProgressSyncFailures,
        )
    }

    fun onAppStopped() {
        activeRouteStore.save(uiState.toActiveRouteSnapshot())
        flushPendingRemoteProgress()
        runBlocking {
            readerSessionStore.flushPendingWrites()
        }
        comicPageSourceCacheController.clearReaderMemoryCaches()
    }

    fun onTrimMemory(
        level: Int,
    ) {
        val request = readerMemoryTrimRequestForAndroid(
            level = level,
            retention = activeReaderPageCacheRetention(),
        ) ?: return
        comicPageSourceCacheController.trimReaderMemoryCaches(request)
    }

    private fun activeReaderPageCacheRetention(): ReaderPageCacheRetention? {
        val item = uiState.selectedItem?.takeIf { uiState.activeReader != null } ?: return null
        val pages = comicPageSource.getPages(item)
        return readerPageCacheRetentionFor(
            item = item,
            pages = pages,
            currentPageIndex = comicReaderProgressStore.loadPage(item),
        )
    }

    private fun trimReaderCachesBeforeLeavingReader() {
        if (uiState.activeReader == null || uiState.selectedItem == null) return
        val request = ReaderMemoryTrimRequest.dropPreviewsAndDistantPages(
            retention = activeReaderPageCacheRetention(),
        )
        comicPageSourceCacheController.trimReaderMemoryCaches(request)
    }

    fun retryPendingRemoteSync() {
        retryPendingRemoteSync(uiState.pendingRemoteSyncSummary)
    }

    private fun retryPendingRemoteSync(
        pendingRemoteSyncSummary: AppPendingRemoteSyncSummary,
        extraSourceTypes: Set<SourceType> = emptySet(),
        flushAfterProbe: Boolean = true,
    ) {
        val pendingSourceTypes = pendingRemoteSyncSummary.groups
            .mapTo(linkedSetOf(), AppPendingRemoteSyncSourceGroup::sourceType)
            .also { sourceTypes -> sourceTypes += extraSourceTypes }
            .filter { sourceType -> sourceType != SourceType.LOCAL }
            .toSet()
        if (pendingSourceTypes.isEmpty()) return
        viewModelScope.launch {
            val probeFailures = pendingSourceTypes.mapNotNull { sourceType ->
                when (val result = remoteSourceConnectivityStartupCoordinator.retrySource(sourceType)) {
                    RemoteSourceConnectivityProbeResult.NoHealthCheck,
                    RemoteSourceConnectivityProbeResult.NotConfigured,
                    RemoteSourceConnectivityProbeResult.Online -> null
                    is RemoteSourceConnectivityProbeResult.Offline -> SourceProgressSyncFailure(
                        sourceType = sourceType,
                        throwable = result.throwable,
                    )
                    is RemoteSourceConnectivityProbeResult.Failed -> SourceProgressSyncFailure(
                        sourceType = sourceType,
                        throwable = result.throwable,
                    )
                }
            }
            if (probeFailures.isNotEmpty()) {
                readerCoordinator.recordRemoteSyncAttempt(
                    attemptedSourceTypes = probeFailures.mapTo(mutableSetOf(), SourceProgressSyncFailure::sourceType),
                    failures = probeFailures,
                )
            }
            if (flushAfterProbe) {
                readerCoordinator.flushPendingProgress(
                    catalogItems = catalogStateCoordinator.catalogItems(),
                    selectedItem = uiState.selectedItem,
                    scope = viewModelScope,
                    onSyncFailures = ::reportProgressSyncFailures,
                )
            }
        }
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
            canScheduleSource = remoteSourceConnectivityPolicy::canScheduleDownload,
        )
    }

    fun onDownloadRemoteItems(items: List<ReadableItem>) {
        remoteDownloadController.onDownloadRemoteItems(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            items = items,
            canScheduleSource = remoteSourceConnectivityPolicy::canScheduleDownload,
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

    fun onSetReadStatus(item: ReadableItem, markAsRead: Boolean) {
        viewModelScope.launch {
            libraryReadStatusController.setReadStatus(
                item = item,
                markAsRead = markAsRead,
                onProgressChanged = ::refreshProgress,
            )
        }
    }

    fun dismissProgressSyncNotice(noticeId: Long) {
        progressSyncNoticeController.dismissNotice(
            noticeId = noticeId,
            stateProvider = { uiState },
            updateState = ::updateUiState,
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
            canScheduleSource = remoteSourceConnectivityPolicy::canScheduleDownload,
        )
    }

    fun removeDownload(entryId: String) {
        remoteDownloadController.removeDownload(
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
                onSyncFailures = ::reportProgressSyncFailures,
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

    fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    ) {
        viewModelScope.launch {
            journalEntrySaveUseCase(
                item = item,
                rating = rating,
                reviewText = reviewText,
            )
        }
    }

    fun deleteItemJournal(itemId: String) {
        viewModelScope.launch {
            journalRepository.deleteItemJournal(itemId)
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

    fun onKavitaBackupBaseUrlChanged(backupBaseUrl: String) {
        remoteSourcesCoordinator.onKavitaBackupBaseUrlChanged(
            backupBaseUrl = backupBaseUrl,
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

    fun onStumpBackupBaseUrlChanged(backupBaseUrl: String) {
        remoteSourcesCoordinator.onStumpBackupBaseUrlChanged(
            backupBaseUrl = backupBaseUrl,
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

    fun onGrimmoryBackupBaseUrlChanged(backupBaseUrl: String) {
        remoteSourcesCoordinator.onGrimmoryBackupBaseUrlChanged(
            backupBaseUrl = backupBaseUrl,
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

    fun retrySelectedSourceConnection() {
        val sourceType = uiState.selectedSource
        if (!remoteSourceConnectivityPolicy.canRunExplicitProbe(sourceType)) return
        viewModelScope.launch {
            remoteSourceConnectivityStartupCoordinator.retrySource(sourceType)
        }
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

    fun fastSyncKavitaLibrary() {
        remoteSourcesCoordinator.fastSyncKavitaLibrary(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun fastSyncKavitaLibraryInPlace() {
        remoteSourcesCoordinator.fastSyncKavitaLibrary(
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
            preserveLibraryContext = true,
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

    fun preloadCovers(sourceType: SourceType) {
        coverPreloadController.preloadCovers(
            sourceType = sourceType,
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun retryFailedCovers(sourceType: SourceType) {
        coverPreloadController.retryFailedCovers(
            sourceType = sourceType,
            stateProvider = { uiState },
            updateState = ::updateUiState,
            scope = viewModelScope,
        )
    }

    fun cancelCoverPreload(sourceType: SourceType) {
        coverPreloadController.cancelPreload(
            sourceType = sourceType,
            updateState = ::updateUiState,
        )
    }

    fun clearCachedCovers(sourceType: SourceType) {
        coverPreloadController.clearCachedCovers(
            sourceType = sourceType,
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
        comicReaderModeController.observePreferences(
            scope = viewModelScope,
            updateState = ::updateUiState,
        )
        remoteSourcesCoordinator.observeStoredSessions(
            scope = viewModelScope,
            stateProvider = { uiState },
            updateState = ::updateUiState,
        )
        kavitaReadingListController.observeStoredSession(
            scope = viewModelScope,
            updateState = ::updateUiState,
        )
        viewModelScope.launch {
            remoteSourceConnectivityPolicy.reachabilityFlow.collect { connectivityBySource ->
                updateUiState { state ->
                    state.copy(sourceConnectivityBySource = connectivityBySource)
                }
                remoteDownloadController.scheduleQueuedDownloads(
                    scope = viewModelScope,
                    canScheduleSource = remoteSourceConnectivityPolicy::canScheduleDownload,
                )
            }
        }
        viewModelScope.launch {
            remoteSourceConnectivityStartupCoordinator.sourceErrorsFlow.collect { errorsBySource ->
                if (errorsBySource.isEmpty()) return@collect
                updateUiState { state ->
                    state.withStartupSourceErrors(errorsBySource)
                }
            }
        }
        readerCoordinator.observeDirtyProgress(
            scope = viewModelScope,
            catalogItemsProvider = catalogStateCoordinator::catalogItems,
            stateProvider = { uiState },
            onSyncFailures = ::reportProgressSyncFailures,
            onPendingRemoteSyncSummaryChanged = { summary ->
                updateUiState { state ->
                    state.copy(pendingRemoteSyncSummary = summary)
                }
            },
        )
        viewModelScope.launch {
            appNetworkConnectivityMonitor.networkAvailableFlow.collect { isNetworkAvailable ->
                val previousAvailability = lastNetworkAvailability
                lastNetworkAvailability = isNetworkAvailable
                if (!isNetworkAvailable) {
                    lastNetworkPendingRetryFingerprint = null
                    return@collect
                }
                if (previousAvailability != false) return@collect
                val unavailableSourceTypes = remoteSourceConnectivityPolicy.reachabilityFlow.value
                    .unavailableRemoteSourceTypes()
                if (unavailableSourceTypes.isEmpty()) return@collect
                val retryFingerprint = unavailableSourceTypes.networkRetryFingerprint()
                if (retryFingerprint == lastNetworkPendingRetryFingerprint) return@collect
                lastNetworkPendingRetryFingerprint = retryFingerprint
                retryPendingRemoteSync(
                    pendingRemoteSyncSummary = uiState.pendingRemoteSyncSummary,
                    extraSourceTypes = unavailableSourceTypes,
                    flushAfterProbe = true,
                )
            }
        }
        progressSyncStartupRetryCoordinator.retryAfterHydration(
            scope = viewModelScope,
            onSyncFailures = ::reportProgressSyncFailures,
            onSyncAttempt = readerCoordinator::recordRemoteSyncAttempt,
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
        viewModelScope.launch {
            journalRepository.observeWriteState().collect { snapshot ->
                updateUiState { state ->
                    state.copy(journalSnapshot = snapshot)
                }
            }
        }
    }

    /**
     * The full journal snapshot (stats/timelines/summaries + catalog join). This is expensive, so
     * the shell only wires the lightweight [JournalRepository.observeWriteState] into [uiState];
     * the Journal tab collects this flow lazily, i.e. only while it is on screen.
     */
    val journalFullSnapshotFlow: kotlinx.coroutines.flow.Flow<com.example.unifiedreader.journal.JournalSnapshot>
        get() = journalRepository.observeSnapshot()

    private fun updateUiState(
        transform: (AppUiState) -> AppUiState,
    ) {
        libraryStateRecomputeCoordinator.updateState(
            currentState = uiState,
            transform = transform,
            scope = viewModelScope,
            publishState = { nextState ->
                uiState = nextState
                activeRouteStore.save(nextState.toActiveRouteSnapshot())
            },
        )
    }

    private fun reportProgressSyncFailures(
        failures: List<SourceProgressSyncFailure>,
    ) {
        progressSyncNoticeController.reportFailures(
            failures = failures,
            updateState = ::updateUiState,
        )
    }
}

private fun Set<SourceType>.networkRetryFingerprint(): String {
    return filter { sourceType -> sourceType != SourceType.LOCAL }
        .distinct()
        .sortedBy(SourceType::ordinal)
        .joinToString(":") { sourceType -> sourceType.name }
}

private fun Map<SourceType, SourceConnectivityState>.unavailableRemoteSourceTypes(): Set<SourceType> {
    return entries.mapNotNullTo(linkedSetOf()) { (sourceType, state) ->
        sourceType.takeIf {
            sourceType != SourceType.LOCAL && state == SourceConnectivityState.Offline
        }
    }
}

private fun AppUiState.withStartupSourceErrors(
    errorsBySource: Map<SourceType, Throwable>,
): AppUiState {
    var next = this
    errorsBySource[SourceType.KAVITA]?.let { throwable ->
        next = next.copy(
            kavitaConnection = next.kavitaConnection.copy(
                message = "Unable to check Kavita: ${throwable.userFacingMessage()}",
                isError = true,
            ),
        )
    }
    errorsBySource[SourceType.STUMP]?.let { throwable ->
        next = next.copy(
            stumpConnection = next.stumpConnection.copy(
                message = "Unable to check Stump: ${throwable.userFacingMessage()}",
                isError = true,
            ),
        )
    }
    errorsBySource[SourceType.GRIMMORY]?.let { throwable ->
        next = next.copy(
            grimmoryConnection = next.grimmoryConnection.copy(
                message = "Unable to check Grimmory: ${throwable.userFacingMessage()}",
                isError = true,
            ),
        )
    }
    return next
}
