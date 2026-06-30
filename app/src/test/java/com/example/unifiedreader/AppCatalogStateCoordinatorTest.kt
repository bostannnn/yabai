package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.library.covers.VisibleLibraryCoverRegistry
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppCatalogStateCoordinatorTest {
    @Test
    fun `local filter is pending while remote local availability is unknown`() {
        val state = AppUiState(
            selectedSource = SourceType.KAVITA,
            libraryFiltersBySource = mapOf(
                SourceType.KAVITA to LibraryFilterState(
                    fileLocations = setOf(LibraryFileLocation.LOCAL),
                ),
            ),
        )

        assertTrue(state.isSelectedLibraryLocalAvailabilityPending)
        assertFalse(state.copy(isRemoteItemLocalStateKnown = true).isSelectedLibraryLocalAvailabilityPending)
        assertTrue(
            state.copy(
                isRemoteItemLocalStateKnown = true,
                isRemoteItemLocalStateRefreshing = true,
            ).isSelectedLibraryLocalAvailabilityPending,
        )
        assertFalse(state.copy(selectedSource = SourceType.LOCAL).isSelectedLibraryLocalAvailabilityPending)
    }

    @Test
    fun `catalog snapshot preserves known series container local availability`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val issue = ReadableItem(
            id = "issue-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "issue-1",
            remoteFileType = "series-7",
            title = "Akira 1",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Akira",
                issueTitle = "Issue 1",
            ),
        )
        val seriesContainerId = kavitaSeriesContainerId(requireNotNull(issue.kavitaSeriesGroupKey()))
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val readableItemRepository = FakeReadableItemRepository(items = listOf(issue))
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = emptySet(),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(libraries = libraries),
            readableItemRepository = readableItemRepository,
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(libraries = libraries),
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        var state = AppUiState(
            items = listOf(issue),
            downloadedRemoteItemIds = emptySet(),
            locallyAvailableRemoteItemIds = setOf(seriesContainerId),
            remoteItemLocalStateItemIds = setOf(issue.id),
            remoteItemLocalAvailabilityScopeIds = setOf(issue.id, seriesContainerId),
            isRemoteItemLocalStateKnown = true,
        )
        val publishedStates = mutableListOf<AppUiState>()
        val pendingAvailabilityRefresh = downloadDataSource.pauseNextStoredDocumentItemIds()
        val updateState: AppUiStateUpdater = { reducer ->
            state = reducer(state)
            publishedStates += state
        }

        catalogStateCoordinator.observeCatalog(
            scope = backgroundScope,
            stateProvider = { state },
            updateState = updateState,
        )
        runCurrent()

        val quickSnapshotState = publishedStates.first()
        assertEquals(emptySet<String>(), quickSnapshotState.downloadedRemoteItemIds)
        assertEquals(setOf(seriesContainerId), quickSnapshotState.locallyAvailableRemoteItemIds)
        assertTrue(quickSnapshotState.isRemoteItemLocalStateKnown)
        assertTrue(quickSnapshotState.isRemoteItemLocalStateRefreshing)

        pendingAvailabilityRefresh.complete(Unit)
        runCurrent()
        advanceUntilIdle()
    }

    @Test
    fun `catalog scope change without local state marks local filter availability pending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val oldItem = sampleItem(
            id = "old-book",
            title = "Old Book",
            format = FormatType.EPUB,
        )
        val newItem = sampleItem(
            id = "new-book",
            title = "New Book",
            format = FormatType.EPUB,
        )
        val libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.BOOKS,
            ),
        )
        val readableItemRepository = FakeReadableItemRepository(items = listOf(newItem))
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = emptySet(),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(libraries = libraries),
            readableItemRepository = readableItemRepository,
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(libraries = libraries),
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                libraries = libraries,
                items = listOf(oldItem),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                remoteItemLocalStateItemIds = setOf(oldItem.id),
                remoteItemLocalAvailabilityScopeIds = setOf(oldItem.id),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()
        val pendingAvailabilityRefresh = downloadDataSource.pauseNextStoredDocumentItemIds()
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = backgroundScope,
                publishState = { nextState ->
                    state = nextState
                    publishedStates += nextState
                },
            )
        }

        catalogStateCoordinator.observeCatalog(
            scope = backgroundScope,
            stateProvider = { state },
            updateState = updateState,
        )
        runCurrent()

        val quickSnapshotState = publishedStates.first()
        assertEquals(setOf(newItem.id), quickSnapshotState.remoteItemLocalStateItemIds)
        assertFalse(quickSnapshotState.isRemoteItemLocalStateKnown)
        assertTrue(quickSnapshotState.isRemoteItemLocalStateRefreshing)
        assertTrue(quickSnapshotState.isSelectedLibraryLocalAvailabilityPending)
        assertTrue(quickSnapshotState.visibleItems.isEmpty())

        pendingAvailabilityRefresh.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `catalog snapshot preserves known local filter results until availability refresh completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val item = sampleItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.BOOKS,
            ),
        )
        val readableItemRepository = FakeReadableItemRepository(items = listOf(item))
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = setOf(item.id),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(libraries = libraries),
            readableItemRepository = readableItemRepository,
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(libraries = libraries),
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                libraries = libraries,
                items = listOf(item),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                downloadedRemoteItemIds = setOf(item.id),
                locallyAvailableRemoteItemIds = setOf(item.id),
                remoteItemLocalStateItemIds = setOf(item.id),
                remoteItemLocalAvailabilityScopeIds = setOf(item.id),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()
        val pendingAvailabilityRefresh = downloadDataSource.pauseNextStoredDocumentItemIds()
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = backgroundScope,
                publishState = { nextState ->
                    state = nextState
                    publishedStates += nextState
                },
            )
        }

        catalogStateCoordinator.observeCatalog(
            scope = backgroundScope,
            stateProvider = { state },
            updateState = updateState,
        )
        runCurrent()

        val quickSnapshotState = publishedStates.first()
        assertEquals(setOf(item.id), quickSnapshotState.downloadedRemoteItemIds)
        assertEquals(setOf(item.id), quickSnapshotState.locallyAvailableRemoteItemIds)
        assertEquals(listOf(item.id), quickSnapshotState.visibleItems.map(ReadableItem::id))

        pendingAvailabilityRefresh.complete(Unit)
        advanceUntilIdle()
        assertEquals(setOf(item.id), state.downloadedRemoteItemIds)
        assertEquals(setOf(item.id), state.locallyAvailableRemoteItemIds)

        downloadDataSource.replaceStoredItemIds(emptySet())
        catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
            scope = this,
            stateProvider = { state },
            updateState = updateState,
            items = state.items,
        )
        advanceUntilIdle()
        assertEquals(emptySet<String>(), state.downloadedRemoteItemIds)
        assertEquals(emptySet<String>(), state.locallyAvailableRemoteItemIds)
        assertTrue(state.isRemoteItemLocalStateKnown)
        assertFalse(state.isRemoteItemLocalStateRefreshing)
        assertTrue(state.visibleItems.isEmpty())
    }

    @Test
    fun `known empty local filter is pending while availability refresh discovers downloads`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val item = sampleItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = emptySet(),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(),
                readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                items = listOf(item),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                remoteItemLocalStateItemIds = setOf(item.id),
                remoteItemLocalAvailabilityScopeIds = setOf(item.id),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = this,
                publishState = { nextState ->
                    state = nextState
                },
            )
        }
        val pendingAvailabilityRefresh = downloadDataSource.pauseNextStoredDocumentItemIds(
            result = setOf(item.id),
        )

        catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
            scope = this,
            stateProvider = { state },
            updateState = updateState,
            items = state.items,
        )
        runCurrent()

        assertTrue(state.isRemoteItemLocalStateKnown)
        assertTrue(state.isRemoteItemLocalStateRefreshing)
        assertTrue(state.isSelectedLibraryLocalAvailabilityPending)
        assertTrue(state.visibleItems.isEmpty())

        pendingAvailabilityRefresh.complete(Unit)
        advanceUntilIdle()

        assertEquals(setOf(item.id), state.downloadedRemoteItemIds)
        assertEquals(setOf(item.id), state.locallyAvailableRemoteItemIds)
        assertTrue(state.isRemoteItemLocalStateKnown)
        assertFalse(state.isRemoteItemLocalStateRefreshing)
        assertEquals(listOf(item.id), state.visibleItems.map(ReadableItem::id))
    }

    @Test
    fun `catalog snapshot starts same scope availability refresh atomically`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val item = sampleItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.BOOKS,
            ),
        )
        val readableItemRepository = FakeReadableItemRepository(items = listOf(item))
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = emptySet(),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(libraries = libraries),
            readableItemRepository = readableItemRepository,
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(libraries = libraries),
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                libraries = libraries,
                items = listOf(item),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                remoteItemLocalStateItemIds = setOf(item.id),
                remoteItemLocalAvailabilityScopeIds = setOf(item.id),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()
        val pendingAvailabilityRefresh = downloadDataSource.pauseNextStoredDocumentItemIds()
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = backgroundScope,
                publishState = { nextState ->
                    state = nextState
                    publishedStates += nextState
                },
            )
        }

        catalogStateCoordinator.observeCatalog(
            scope = backgroundScope,
            stateProvider = { state },
            updateState = updateState,
        )
        runCurrent()

        val quickSnapshotState = publishedStates.first()
        assertTrue(quickSnapshotState.isRemoteItemLocalStateKnown)
        assertTrue(quickSnapshotState.isRemoteItemLocalStateRefreshing)
        assertTrue(quickSnapshotState.isSelectedLibraryLocalAvailabilityPending)
        assertTrue(quickSnapshotState.visibleItems.isEmpty())

        pendingAvailabilityRefresh.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `series container scope change marks local availability pending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val oldIssue = ReadableItem(
            id = "issue-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "issue-1",
            remoteFileType = "series-old",
            title = "Akira 1",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Akira",
                issueTitle = "Issue 1",
            ),
        )
        val newIssue = oldIssue.copy(remoteFileType = "series-new")
        val oldSeriesContainerId = kavitaSeriesContainerId(requireNotNull(oldIssue.kavitaSeriesGroupKey()))
        val newSeriesContainerId = kavitaSeriesContainerId(requireNotNull(newIssue.kavitaSeriesGroupKey()))
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val readableItemRepository = FakeReadableItemRepository(items = listOf(newIssue))
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = setOf(newIssue.id),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(libraries = libraries),
            readableItemRepository = readableItemRepository,
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(libraries = libraries),
                readableItemRepository = readableItemRepository,
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                libraries = libraries,
                items = listOf(oldIssue),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                downloadedRemoteItemIds = setOf(oldIssue.id),
                locallyAvailableRemoteItemIds = setOf(oldIssue.id, oldSeriesContainerId),
                remoteItemLocalStateItemIds = setOf(oldIssue.id),
                remoteItemLocalAvailabilityScopeIds = setOf(oldIssue.id, oldSeriesContainerId),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val publishedStates = mutableListOf<AppUiState>()
        val pendingAvailabilityRefresh = downloadDataSource.pauseNextStoredDocumentItemIds()
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = backgroundScope,
                publishState = { nextState ->
                    state = nextState
                    publishedStates += nextState
                },
            )
        }

        catalogStateCoordinator.observeCatalog(
            scope = backgroundScope,
            stateProvider = { state },
            updateState = updateState,
        )
        runCurrent()

        val quickSnapshotState = publishedStates.first()
        assertFalse(oldSeriesContainerId in quickSnapshotState.locallyAvailableRemoteItemIds)
        assertFalse(newSeriesContainerId in quickSnapshotState.locallyAvailableRemoteItemIds)
        assertFalse(quickSnapshotState.isRemoteItemLocalStateKnown)
        assertTrue(quickSnapshotState.isRemoteItemLocalStateRefreshing)
        assertTrue(quickSnapshotState.isSelectedLibraryLocalAvailabilityPending)
        assertTrue(quickSnapshotState.visibleItems.isEmpty())

        pendingAvailabilityRefresh.complete(Unit)
    }

    @Test
    fun `availability refresh derives series container local availability`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val issue = ReadableItem(
            id = "issue-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "issue-1",
            remoteFileType = "series-new",
            title = "Akira 1",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(
                series = "Akira",
                issueTitle = "Issue 1",
            ),
        )
        val seriesContainerId = kavitaSeriesContainerId(requireNotNull(issue.kavitaSeriesGroupKey()))
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = setOf(issue.id),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(libraries = libraries),
            readableItemRepository = FakeReadableItemRepository(items = listOf(issue)),
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(libraries = libraries),
                readableItemRepository = FakeReadableItemRepository(items = listOf(issue)),
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                libraries = libraries,
                items = listOf(issue),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                remoteItemLocalStateItemIds = setOf(issue.id),
                remoteItemLocalAvailabilityScopeIds = setOf(issue.id, seriesContainerId),
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = this,
                publishState = { nextState ->
                    state = nextState
                },
            )
        }

        catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
            scope = this,
            stateProvider = { state },
            updateState = updateState,
            items = state.items,
        )
        advanceUntilIdle()

        assertEquals(setOf(issue.id), state.downloadedRemoteItemIds)
        assertEquals(setOf(issue.id, seriesContainerId), state.locallyAvailableRemoteItemIds)
        assertTrue(state.isRemoteItemLocalStateKnown)
        assertFalse(state.isRemoteItemLocalStateRefreshing)
        assertEquals(listOf(seriesContainerId), state.visibleItems.map(ReadableItem::id))
    }

    @Test
    fun `availability refresh failure settles pending state with preserved known ids`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val item = sampleItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = setOf(item.id),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(),
                readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                items = listOf(item),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                downloadedRemoteItemIds = setOf(item.id),
                locallyAvailableRemoteItemIds = setOf(item.id),
                remoteItemLocalStateItemIds = setOf(item.id),
                remoteItemLocalAvailabilityScopeIds = setOf(item.id),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = this,
                publishState = { nextState ->
                    state = nextState
                },
            )
        }
        downloadDataSource.failNextStoredDocumentItemIds()

        catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
            scope = this,
            stateProvider = { state },
            updateState = updateState,
            items = state.items,
        )
        advanceUntilIdle()

        assertEquals(setOf(item.id), state.downloadedRemoteItemIds)
        assertEquals(setOf(item.id), state.locallyAvailableRemoteItemIds)
        assertTrue(state.isRemoteItemLocalStateKnown)
        assertFalse(state.isRemoteItemLocalStateRefreshing)
        assertEquals(listOf(item.id), state.visibleItems.map(ReadableItem::id))
    }

    @Test
    fun `availability refresh failure keeps unknown availability pending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val item = sampleItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = setOf(item.id),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(),
                readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        var state = AppUiState(
            items = listOf(item),
            selectedSource = SourceType.KAVITA,
            libraryFiltersBySource = mapOf(
                SourceType.KAVITA to LibraryFilterState(
                    fileLocations = setOf(LibraryFileLocation.LOCAL),
                ),
            ),
            remoteItemLocalStateItemIds = setOf(item.id),
            remoteItemLocalAvailabilityScopeIds = setOf(item.id),
            hydratedSources = setOf(SourceType.KAVITA),
        )
        val updateState: AppUiStateUpdater = { reducer ->
            state = reducer(state)
        }
        downloadDataSource.failNextStoredDocumentItemIds()

        catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
            scope = this,
            stateProvider = { state },
            updateState = updateState,
            items = state.items,
        )
        advanceUntilIdle()

        assertFalse(state.isRemoteItemLocalStateKnown)
        assertFalse(state.isRemoteItemLocalStateRefreshing)
        assertTrue(state.isSelectedLibraryLocalAvailabilityPending)
    }

    @Test
    fun `stale availability refresh does not overwrite newer local state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appDispatchers = AppDispatchers(
            io = dispatcher,
            default = dispatcher,
        )
        val item = sampleItem(
            id = "book-1",
            title = "Remote Book",
            format = FormatType.EPUB,
        )
        val localDocumentRepository = FakeLocalDocumentRepository()
        val readerSessionStore = FakeReaderSessionStore()
        val downloadDataSource = ControlledRemoteDownloadDataSource(
            sourceType = SourceType.KAVITA,
            initialStoredItemIds = setOf(item.id),
        )
        val libraryStateAssembler = AppLibraryStateAssembler(
            localDocumentRepository = localDocumentRepository,
            remoteDownloadDataSources = listOf(downloadDataSource),
        )
        val catalogStateCoordinator = AppCatalogStateCoordinator(
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            localDocumentRepository = localDocumentRepository,
            libraryStateAssembler = libraryStateAssembler,
            observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
                settingsPreferencesStore = FakeSettingsPreferencesStore(),
                libraryRepository = FakeLibraryRepository(),
                readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
                remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
                localDocumentRepository = localDocumentRepository,
                readerSessionStore = readerSessionStore,
                libraryStateAssembler = libraryStateAssembler,
                appDispatchers = appDispatchers,
            ),
            readerSessionStore = readerSessionStore,
            remoteCoverController = RemoteCoverController(
                kavitaCoverRepository = FakeKavitaCoverRepository(),
                stumpCoverRepository = FakeStumpCoverRepository(),
                grimmoryCoverRepository = FakeGrimmoryCoverRepository(),
                kavitaCredentialStore = FakeKavitaCredentialStore(),
                stumpCredentialStore = FakeStumpCredentialStore(),
                grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
                remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
                visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
                appDispatchers = appDispatchers,
            ),
        )
        val recomputeCoordinator = AppLibraryStateRecomputeCoordinator(
            libraryDerivedStateComputer = LibraryDerivedStateComputer(),
            libraryShelvesStateComputer = LibraryShelvesStateComputer(
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            appDispatchers = appDispatchers,
        )
        var state = recomputeCoordinator.initialize(
            AppUiState(
                items = listOf(item),
                selectedSource = SourceType.KAVITA,
                libraryFiltersBySource = mapOf(
                    SourceType.KAVITA to LibraryFilterState(
                        fileLocations = setOf(LibraryFileLocation.LOCAL),
                    ),
                ),
                downloadedRemoteItemIds = setOf(item.id),
                locallyAvailableRemoteItemIds = setOf(item.id),
                remoteItemLocalStateItemIds = setOf(item.id),
                remoteItemLocalAvailabilityScopeIds = setOf(item.id),
                isRemoteItemLocalStateKnown = true,
                hydratedSources = setOf(SourceType.KAVITA),
            ),
        )
        val updateState: AppUiStateUpdater = { reducer ->
            recomputeCoordinator.updateState(
                currentState = state,
                transform = reducer,
                scope = this,
                publishState = { nextState ->
                    state = nextState
                },
            )
        }
        val staleScan = downloadDataSource.pauseNextStoredDocumentItemIds(
            result = setOf(item.id),
        )

        catalogStateCoordinator.refreshDownloadedRemoteItemIdsAsync(
            scope = this,
            stateProvider = { state },
            updateState = updateState,
            items = state.items,
        )
        runCurrent()
        updateState { currentState ->
            currentState.withRemoteItemLocalState(
                downloadedRemoteItemIds = emptySet(),
                locallyAvailableRemoteItemIds = emptySet(),
                isKnown = true,
            )
        }
        staleScan.complete(Unit)
        advanceUntilIdle()

        assertEquals(emptySet<String>(), state.downloadedRemoteItemIds)
        assertEquals(emptySet<String>(), state.locallyAvailableRemoteItemIds)
        assertTrue(state.visibleItems.isEmpty())
    }
}

private class ControlledRemoteDownloadDataSource(
    override val sourceType: SourceType,
    initialStoredItemIds: Set<String>,
) : RemoteItemDownloadDataSource {
    private val storedItemIds = initialStoredItemIds.toMutableSet()
    private var nextStoredDocumentItemIdsGate: CompletableDeferred<Unit>? = null

    private var nextStoredDocumentItemIdsResult: Set<String>? = null
    private var nextStoredDocumentItemIdsError: Throwable? = null

    fun pauseNextStoredDocumentItemIds(
        result: Set<String>? = null,
    ): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { gate ->
            nextStoredDocumentItemIdsGate = gate
            nextStoredDocumentItemIdsResult = result
        }

    fun replaceStoredItemIds(
        itemIds: Set<String>,
    ) {
        storedItemIds.clear()
        storedItemIds += itemIds
    }

    fun failNextStoredDocumentItemIds(
        error: Throwable = IllegalStateException("Unable to scan local availability."),
    ) {
        nextStoredDocumentItemIdsError = error
    }

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = if (item.id in storedItemIds) {
        Uri.parse("file:///downloads/${item.id}.${item.format.name.lowercase()}")
    } else {
        null
    }

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> {
        nextStoredDocumentItemIdsError?.let { error ->
            nextStoredDocumentItemIdsError = null
            throw error
        }
        val result = nextStoredDocumentItemIdsResult
        nextStoredDocumentItemIdsGate?.let { gate ->
            nextStoredDocumentItemIdsGate = null
            nextStoredDocumentItemIdsResult = null
            gate.await()
        }
        result?.let { return it }
        return items
            .filter { item -> item.id in storedItemIds }
            .mapTo(mutableSetOf(), ReadableItem::id)
    }

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}
