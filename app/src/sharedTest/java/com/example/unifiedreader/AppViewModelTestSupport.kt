package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.repository.JournalEntrySaveUseCase
import com.example.unifiedreader.data.repository.FlushPendingRemoteArtifactsUseCase
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.RemoteJournalPusherRegistry
import com.example.unifiedreader.feature.downloads.DownloadCompletionSideEffect
import com.example.unifiedreader.feature.downloads.DownloadEntriesSnapshot
import com.example.unifiedreader.feature.downloads.DownloadEntry
import com.example.unifiedreader.feature.downloads.DownloadEntryStatus
import com.example.unifiedreader.feature.downloads.DownloadQueueStore
import com.example.unifiedreader.feature.downloads.DownloadWorkScheduler
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import com.example.unifiedreader.feature.downloads.RemoteDownloadWorkerRunner
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryBrowsePreferences
import com.example.unifiedreader.feature.library.LibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.LibraryBrowseStateCoordinator
import com.example.unifiedreader.feature.library.LibraryDerivedStateComputer
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import com.example.unifiedreader.feature.library.covers.VisibleLibraryCoverRegistry
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesStateComputer
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferences
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferencesStore
import com.example.unifiedreader.feature.navigation.SourceNavigationStateCoordinator
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesSnapshot
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.SettingsPreferences
import com.example.unifiedreader.feature.settings.SettingsPreferencesStore
import com.example.unifiedreader.feature.settings.SettingsStateCoordinator
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

internal fun createViewModel(
    sampleItems: List<ReadableItem> = emptyList(),
    importedRepository: FakeLocalDocumentRepository = FakeLocalDocumentRepository(),
    pageSource: ComicPageSource = FakeComicPageSource(),
    progressStore: ComicReaderProgressStore = FakeComicReaderProgressStore(),
    preferencesStore: ComicReaderPreferencesStore = FakeComicReaderPreferencesStore(),
    comicReadingDirectionPreferencesStore: ComicReadingDirectionPreferencesStore = FakeComicReadingDirectionPreferencesStore(),
    comicReaderModePreferencesStore: ComicReaderModePreferencesStore = FakeComicReaderModePreferencesStore(),
    settingsPreferencesStore: SettingsPreferencesStore = FakeSettingsPreferencesStore(),
    settingsStateCoordinator: SettingsStateCoordinator = SettingsStateCoordinator(settingsPreferencesStore),
    settingsController: AppSettingsController = AppSettingsController(settingsStateCoordinator),
    sourceNavigationPreferencesStore: SourceNavigationPreferencesStore = FakeSourceNavigationPreferencesStore(),
    libraryBrowsePreferencesStore: LibraryBrowsePreferencesStore = FakeLibraryBrowsePreferencesStore(),
    libraryBrowseStateCoordinator: LibraryBrowseStateCoordinator = LibraryBrowseStateCoordinator(libraryBrowsePreferencesStore),
    libraryBrowseController: AppLibraryBrowseController = AppLibraryBrowseController(libraryBrowseStateCoordinator),
    readerSessionStore: FakeReaderSessionStore = FakeReaderSessionStore(),
    kavitaCatalogSyncer: KavitaCatalogSyncer = FakeKavitaCatalogSyncer(),
    kavitaCredentialStore: KavitaCredentialStore = FakeKavitaCredentialStore(),
    kavitaCoverRepository: KavitaCoverDataSource = FakeKavitaCoverRepository(),
    stumpCatalogSyncer: StumpCatalogSyncer = FakeStumpCatalogSyncer(),
    stumpCredentialStore: StumpCredentialStore = FakeStumpCredentialStore(),
    stumpCoverRepository: StumpCoverDataSource = FakeStumpCoverRepository(),
    remoteLibraryCacheStore: RemoteLibraryCacheStore = FakeRemoteLibraryCacheStore(),
    grimmoryCatalogSyncer: GrimmoryCatalogSyncer = FakeGrimmoryCatalogSyncer(),
    grimmoryCredentialStore: GrimmoryCredentialStore = FakeGrimmoryCredentialStore(),
    grimmoryCoverRepository: GrimmoryCoverDataSource = FakeGrimmoryCoverRepository(),
    grimmoryContentRepository: GrimmoryContentDataSource = FakeGrimmoryContentRepository(),
    remoteDownloadDataSources: List<RemoteItemDownloadDataSource> = listOf(grimmoryContentRepository),
    comicPageSourceCacheController: ComicPageSourceCacheController = FakeComicPageSourceCacheController(),
    remoteCatalogReadiness: FakeRemoteCatalogReadiness = FakeRemoteCatalogReadiness(),
    sourceProgressSyncs: List<SourceProgressSync> = emptyList(),
    remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy = onlineRemoteSourceConnectivityPolicy(),
    remoteSourceHealthChecks: List<RemoteSourceHealthCheck> = emptyList(),
    appStartupTasks: AppStartupTasks = AppStartupTasks {},
    coverPreloadController: AppCoverPreloadController? = null,
    readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory> = emptyList(),
    progressSyncNoticeController: AppProgressSyncNoticeController = AppProgressSyncNoticeController(),
    progressSyncStartupRetryCoordinator: AppProgressSyncStartupRetryCoordinator? = null,
    journalRepository: JournalRepository = FakeJournalRepository(),
    libraryRepository: FakeLibraryRepository = FakeLibraryRepository(
        libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Comics",
                remoteId = "k-comics",
                contentType = LibraryContentType.COMICS,
            ),
        ),
    ),
    readableItemRepository: FakeReadableItemRepository = FakeReadableItemRepository(items = sampleItems),
): AppViewModel {
    val appDispatchers = AppDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
    )
    val libraryStateAssembler = AppLibraryStateAssembler(
        localDocumentRepository = importedRepository,
        remoteDownloadDataSources = remoteDownloadDataSources,
    )
    val libraryProgressRefreshCoordinator = AppLibraryProgressRefreshCoordinator(
        libraryStateAssembler = libraryStateAssembler,
        readerSessionStore = readerSessionStore,
        appDispatchers = appDispatchers,
    )
    val remoteCoverController = RemoteCoverController(
        kavitaCoverRepository = kavitaCoverRepository,
        stumpCoverRepository = stumpCoverRepository,
        grimmoryCoverRepository = grimmoryCoverRepository,
        kavitaCredentialStore = kavitaCredentialStore,
        stumpCredentialStore = stumpCredentialStore,
        grimmoryCredentialStore = grimmoryCredentialStore,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
        appDispatchers = appDispatchers,
    )
    val downloadQueueStore = TestDownloadQueueStore()
    val downloadScheduler = ImmediateDownloadWorkScheduler()
    val completionSideEffects: List<DownloadCompletionSideEffect> = listOf(
        SourceProgressSyncDownloadCompletionSideEffect(sourceProgressSyncs),
    )
    val remoteDownloadWorkerRunner = RemoteDownloadWorkerRunner(
        store = downloadQueueStore,
        remoteDownloadDataSources = remoteDownloadDataSources,
        completionSideEffects = completionSideEffects,
        canRunSource = remoteSourceConnectivityPolicy::canScheduleDownload,
    )
    downloadScheduler.runner = remoteDownloadWorkerRunner
    val remoteDownloadQueueController = RemoteDownloadQueueController(
        store = downloadQueueStore,
        scheduler = downloadScheduler,
        remoteDownloadDataSources = remoteDownloadDataSources,
    )
    val libraryActionsCoordinator = AppLibraryActionsCoordinator(
        importDocumentUseCase = ImportDocumentUseCase(
            localDocumentRepository = importedRepository,
        ),
        deleteImportedItemUseCase = DeleteImportedItemUseCase(
            importedItemDeletionService = DefaultImportedItemDeletionService(
                localDocumentDataSource = importedRepository,
                readerSessionStore = readerSessionStore,
            ),
        ),
        deleteRemoteLocalFileUseCase = DeleteRemoteLocalFileUseCase(
            remoteDownloadDataSources = remoteDownloadDataSources,
            libraryStateAssembler = libraryStateAssembler,
        ),
        remoteCoverController = remoteCoverController,
    )
    val catalogStateCoordinator = AppCatalogStateCoordinator(
        libraryRepository = libraryRepository,
        readableItemRepository = readableItemRepository,
        localDocumentRepository = importedRepository,
        libraryStateAssembler = libraryStateAssembler,
        observeAppCatalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
            settingsPreferencesStore = settingsPreferencesStore,
            libraryRepository = libraryRepository,
            readableItemRepository = readableItemRepository,
            remoteCatalogReadiness = remoteCatalogReadiness,
            localDocumentRepository = importedRepository,
            readerSessionStore = readerSessionStore,
            libraryStateAssembler = libraryStateAssembler,
            appDispatchers = appDispatchers,
        ),
        readerSessionStore = readerSessionStore,
        remoteCoverController = remoteCoverController,
    )
    val sourceProgressSyncRegistry = SourceProgressSyncRegistry(sourceProgressSyncs)
    val remoteItemLocalAvailability = RemoteItemLocalAvailability(remoteDownloadDataSources)
    val libraryStateRecomputeCoordinator = AppLibraryStateRecomputeCoordinator(
        libraryDerivedStateComputer = LibraryDerivedStateComputer(),
        libraryShelvesStateComputer = LibraryShelvesStateComputer(
            seriesContinuationResolver = LibrarySeriesContinuationResolver(),
        ),
        appDispatchers = appDispatchers,
    )
    val navigationController = AppNavigationController(
        sourceNavigationStateCoordinator = SourceNavigationStateCoordinator(
            preferencesStore = sourceNavigationPreferencesStore,
        ),
    )
    val remoteSourceConnectivityStartupCoordinator = RemoteSourceConnectivityStartupCoordinator(
        healthChecks = remoteSourceHealthChecks,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    )
    val appStateHydrator = AppStateHydrator(
        appStartupTasks = appStartupTasks,
        remoteCatalogHydrator = remoteCatalogReadiness,
        localDocumentHydrator = importedRepository,
        readerSessionHydrator = readerSessionStore,
    )
    val catalogSnapshotsUseCase = ObserveAppCatalogSnapshotsUseCase(
        settingsPreferencesStore = settingsPreferencesStore,
        libraryRepository = libraryRepository,
        readableItemRepository = readableItemRepository,
        remoteCatalogReadiness = remoteCatalogReadiness,
        localDocumentRepository = importedRepository,
        readerSessionStore = readerSessionStore,
        libraryStateAssembler = libraryStateAssembler,
        appDispatchers = appDispatchers,
    )
    val initialUiStateFactory = AppInitialUiStateFactory(
        navigationController = navigationController,
        appStateHydrator = appStateHydrator,
        catalogSnapshotsUseCase = catalogSnapshotsUseCase,
        libraryStateRecomputeCoordinator = libraryStateRecomputeCoordinator,
    )
    val remoteJournalPusher = RemoteJournalPusherRegistry(sourcePushers = emptyList())

    return AppViewModel(
        navigationController = navigationController,
        initialUiStateFactory = initialUiStateFactory,
        libraryBrowseController = libraryBrowseController,
        settingsController = settingsController,
        remoteDownloadController = AppRemoteDownloadController(
            libraryActionsCoordinator = libraryActionsCoordinator,
            remoteDownloadQueueController = remoteDownloadQueueController,
        ),
        catalogStateCoordinator = catalogStateCoordinator,
        libraryActionsCoordinator = libraryActionsCoordinator,
        libraryReadStatusController = AppLibraryReadStatusController(
            applyReadStatusChangeUseCase = ApplyReadStatusChangeUseCase(
                progressSyncStore = ReaderSessionProgressStoreAdapter(readerSessionStore),
                flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                    sourceProgressSyncRegistry = sourceProgressSyncRegistry,
                ),
            ),
        ),
        comicReadingDirectionController = AppComicReadingDirectionController(
            preferencesStore = comicReadingDirectionPreferencesStore,
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
        ),
        comicReaderModeController = AppComicReaderModeController(
            preferencesStore = comicReaderModePreferencesStore,
            readerSessionStore = readerSessionStore,
            readableItemRepository = readableItemRepository,
        ),
        readerCoordinator = AppReaderCoordinator(
            libraryProgressRefreshCoordinator = libraryProgressRefreshCoordinator,
            syncItemProgressUseCase = SyncItemProgressUseCase(
                sourceProgressSyncRegistry = sourceProgressSyncRegistry,
            ),
            pendingProgressFlushCoordinator = AppPendingProgressFlushCoordinator(
                readerSessionStore = readerSessionStore,
                readableItemRepository = readableItemRepository,
                journalRepository = journalRepository,
                flushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
                    flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                        sourceProgressSyncRegistry = sourceProgressSyncRegistry,
                    ),
                    remoteJournalPusher = remoteJournalPusher,
                    sourceJournalPushers = emptyList(),
                ),
                remoteItemLocalAvailability = remoteItemLocalAvailability,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            ),
            nextItemResolver = AppReaderNextItemResolver(
                libraryStateAssembler = libraryStateAssembler,
                readerSessionStore = readerSessionStore,
                seriesContinuationResolver = LibrarySeriesContinuationResolver(),
            ),
            readableAccessResolver = ReadableAccessResolver(
                remoteItemLocalAvailability = remoteItemLocalAvailability,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            ),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            remoteReaderContentPreparer = RemoteReaderContentPreparer(
                remoteDownloadDataSources = remoteDownloadDataSources,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            ),
        ),
        remoteSourcesCoordinator = AppRemoteSourcesCoordinator(
            kavitaController = AppKavitaRemoteSourceController(
                kavitaCoordinator = AppKavitaCoordinator(
                    kavitaCatalogSyncer = kavitaCatalogSyncer,
                    kavitaCredentialStore = kavitaCredentialStore,
                    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                ),
                kavitaCredentialStore = kavitaCredentialStore,
                comicPageSourceCacheController = comicPageSourceCacheController,
                remoteLibraryCacheStore = remoteLibraryCacheStore,
                remoteCoverController = remoteCoverController,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            ),
            stumpController = AppStumpRemoteSourceController(
                stumpCoordinator = AppStumpCoordinator(
                    stumpCatalogSyncer = stumpCatalogSyncer,
                    stumpCredentialStore = stumpCredentialStore,
                    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                ),
                stumpCredentialStore = stumpCredentialStore,
                remoteCoverController = remoteCoverController,
                comicPageSourceCacheController = comicPageSourceCacheController,
                remoteLibraryCacheStore = remoteLibraryCacheStore,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            ),
            grimmoryController = AppGrimmoryRemoteSourceController(
                grimmoryCoordinator = AppGrimmoryCoordinator(
                    grimmoryCatalogSyncer = grimmoryCatalogSyncer,
                    grimmoryCredentialStore = grimmoryCredentialStore,
                    remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                ),
                grimmoryCredentialStore = grimmoryCredentialStore,
                remoteCoverController = remoteCoverController,
            ),
        ),
        remoteSourceConnectivityStartupCoordinator = remoteSourceConnectivityStartupCoordinator,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        progressSyncNoticeController = progressSyncNoticeController,
        journalRepository = journalRepository,
        journalEntrySaveUseCase = JournalEntrySaveUseCase(
            journalRepository = journalRepository,
            flushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
                flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                    sourceProgressSyncRegistry = sourceProgressSyncRegistry,
                ),
                remoteJournalPusher = remoteJournalPusher,
                sourceJournalPushers = emptyList(),
            ),
            readableItemRepository = readableItemRepository,
        ),
        progressSyncStartupRetryCoordinator = progressSyncStartupRetryCoordinator ?: AppProgressSyncStartupRetryCoordinator(
            readableItemRepository = readableItemRepository,
            readerSessionStore = readerSessionStore,
            remoteCatalogReadiness = remoteCatalogReadiness,
            flushPendingRemoteArtifactsUseCase = FlushPendingRemoteArtifactsUseCase(
                flushPendingSourceProgressUseCase = FlushPendingSourceProgressUseCase(
                    sourceProgressSyncRegistry = sourceProgressSyncRegistry,
                ),
                remoteJournalPusher = remoteJournalPusher,
                sourceJournalPushers = emptyList(),
            ),
            remoteItemLocalAvailability = remoteItemLocalAvailability,
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        ),
        coverPreloadController = coverPreloadController ?: AppCoverPreloadController(
            remoteCoverPreloader = RemoteCoverPreloader(
                kavitaCoverRepository = kavitaCoverRepository,
                stumpCoverRepository = stumpCoverRepository,
                grimmoryCoverRepository = grimmoryCoverRepository,
                remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
                appDispatchers = appDispatchers,
            ),
            remoteCoverController = remoteCoverController,
        ),
        comicPageSource = pageSource,
        comicReaderProgressStore = progressStore,
        comicReaderPreferencesStore = preferencesStore,
        readerSessionStore = readerSessionStore,
        readerFactories = readerFactories,
        libraryStateRecomputeCoordinator = libraryStateRecomputeCoordinator,
    )
}

private fun onlineRemoteSourceConnectivityPolicy(): RemoteSourceConnectivityPolicy =
    RemoteSourceConnectivityPolicy().apply {
        markReachable(SourceType.KAVITA)
        markReachable(SourceType.STUMP)
        markReachable(SourceType.GRIMMORY)
    }

class FakeComicPageSourceCacheController : ComicPageSourceCacheController {
    val clearedSourceTypes = mutableListOf<SourceType>()

    override fun clearSourcePageCache(
        sourceType: SourceType,
    ) {
        clearedSourceTypes += sourceType
    }
}

class TestDownloadQueueStore(
    initialEntries: List<DownloadEntry> = emptyList(),
) : DownloadQueueStore {
    private val entriesFlow = MutableStateFlow(initialEntries)

    val entries: List<DownloadEntry>
        get() = entriesFlow.value

    override fun observeEntries(): Flow<DownloadEntriesSnapshot> = entriesFlow.map(::DownloadEntriesSnapshot)

    override suspend fun getEntries(): List<DownloadEntry> = entriesFlow.value

    override suspend fun getEntry(entryId: String): DownloadEntry? = entriesFlow.value.firstOrNull { it.id == entryId }

    override suspend fun putEntry(entry: DownloadEntry) {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun putEntryIfNoActiveTrackedItems(entry: DownloadEntry): Boolean {
        val hasOverlap = entriesFlow.value.any { existingEntry ->
            (existingEntry.status == DownloadEntryStatus.Queued ||
                existingEntry.status == DownloadEntryStatus.PausedConnectivity ||
                existingEntry.status == DownloadEntryStatus.Running ||
                existingEntry.status == DownloadEntryStatus.Canceling) &&
                existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
        }
        if (hasOverlap) return false
        putEntry(entry)
        return true
    }

    override suspend fun retryEntryIfNoOtherActiveTrackedItems(entryId: String): Boolean {
        val entry = entriesFlow.value.firstOrNull { it.id == entryId } ?: return false
        val hasOverlap = entriesFlow.value.any { existingEntry ->
            existingEntry.id != entryId &&
                (existingEntry.status == DownloadEntryStatus.Queued ||
                    existingEntry.status == DownloadEntryStatus.PausedConnectivity ||
                    existingEntry.status == DownloadEntryStatus.Running ||
                    existingEntry.status == DownloadEntryStatus.Canceling) &&
                existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
        }
        if (hasOverlap) return false
        putEntry(
            entry.copy(
                status = DownloadEntryStatus.Queued,
                errorMessage = null,
                currentItemId = null,
                currentItemTitle = null,
                completedAtEpochMillis = null,
            ),
        )
        return true
    }

    override suspend fun updateStatus(
        entryId: String,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ) {
        entriesFlow.value = entriesFlow.value.map { entry ->
            if (entry.id != entryId) return@map entry
            entry.copy(
                status = status,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
    }

    override suspend fun updateStatusIfCurrentStatusIn(
        entryId: String,
        expectedStatuses: Set<DownloadEntryStatus>,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ): Boolean {
        var updated = false
        entriesFlow.value = entriesFlow.value.map { entry ->
            if (entry.id != entryId || entry.status !in expectedStatuses) return@map entry
            updated = true
            entry.copy(
                status = status,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
        return updated
    }

    override suspend fun updateProgress(
        entryId: String,
        completedItemIds: Set<String>,
        currentItemId: String?,
        currentItemTitle: String?,
    ) {
        entriesFlow.value = entriesFlow.value.map { entry ->
            if (entry.id != entryId) return@map entry
            entry.copy(
                completedItemIds = completedItemIds,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
            )
        }
    }

    override suspend fun deleteEntry(entryId: String): Int {
        val previousSize = entriesFlow.value.size
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entryId }
        return previousSize - entriesFlow.value.size
    }
}

class ImmediateDownloadWorkScheduler(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) : DownloadWorkScheduler {
    lateinit var runner: RemoteDownloadWorkerRunner
    private val jobsByEntryId = mutableMapOf<String, kotlinx.coroutines.Job>()

    override fun scheduleEntry(entryId: String) {
        jobsByEntryId[entryId]?.cancel()
        jobsByEntryId[entryId] = scope.launch {
            runner.runEntry(entryId)
        }.also { job ->
            job.invokeOnCompletion {
                jobsByEntryId.remove(entryId)
            }
        }
    }

    override fun cancelEntry(entryId: String) {
        jobsByEntryId.remove(entryId)?.cancel(CancellationException("Cancelled for test"))
    }
}

class FakeSourceNavigationPreferencesStore(
    initialPreferences: SourceNavigationPreferences = SourceNavigationPreferences(),
) : SourceNavigationPreferencesStore {
    private val preferencesState = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<SourceNavigationPreferences> = preferencesState

    override suspend fun setSourceOrder(sourceOrder: List<SourceType>) {
        preferencesState.value = SourceNavigationPreferences(sourceOrder = sourceOrder)
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
    private val connectionResult: GrimmoryCatalogConnectionResult = GrimmoryCatalogConnectionResult(
        libraryCount = 0,
    ),
    private val result: GrimmoryCatalogSyncResult = GrimmoryCatalogSyncResult(
        libraryCount = 0,
        itemCount = 0,
        supportedFormatCounts = emptyMap(),
    ),
    private val failure: Throwable? = null,
) : GrimmoryCatalogSyncer {
    var lastRequest: GrimmoryCatalogSyncRequest? = null
    var lastConnectRequest: GrimmoryCatalogSyncRequest? = null
    var lastSyncRequest: GrimmoryCatalogSyncRequest? = null

    override suspend fun connect(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogConnectionResult {
        lastRequest = request
        lastConnectRequest = request
        failure?.let { throw it }
        return connectionResult
    }

    override suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit,
    ): GrimmoryCatalogSyncResult {
        lastRequest = request
        lastSyncRequest = request
        failure?.let { throw it }
        return result
    }
}

class FakeRemoteCatalogReadiness(
    initialHydrated: Boolean = true,
    private val hydrateOnBootstrap: Boolean = true,
) : RemoteCatalogReadiness, RemoteCatalogHydrator {
    private val hydrationState = MutableStateFlow(initialHydrated)
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState

    override suspend fun hydrate() {
        if (hydrateOnBootstrap) {
            hydrationState.value = true
        }
    }

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
    override val sourceType: SourceType = SourceType.STUMP
    var clearCachedCoversCalls: Int = 0

    constructor(
        coverUri: Uri? = null,
        isPersistent: Boolean = true,
    ) {
        this.coverUri = coverUri
        this.isPersistent = isPersistent
    }

    private var coverUri: Uri? = null
    private var isPersistent: Boolean = true

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = coverUri?.let { uri ->
        CachedRemoteCover(
            uri = uri,
            isPersistent = isPersistent,
        )
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        isPersistent = true
        return coverUri
    }

    override suspend fun clearCachedCovers() {
        clearCachedCoversCalls += 1
        coverUri = null
    }
}

class FakeKavitaCoverRepository : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA
    var clearCachedCoversCalls: Int = 0

    constructor(
        coverUri: Uri? = null,
        isPersistent: Boolean = true,
    ) {
        this.coverUri = coverUri
        this.isPersistent = isPersistent
    }

    private var coverUri: Uri? = null
    private var isPersistent: Boolean = true

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = coverUri?.let { uri ->
        CachedRemoteCover(
            uri = uri,
            isPersistent = isPersistent,
        )
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        isPersistent = true
        return coverUri
    }

    override suspend fun clearCachedCovers() {
        clearCachedCoversCalls += 1
        coverUri = null
    }
}

class FakeRemoteLibraryCacheStore : RemoteLibraryCacheStore {
    private val librariesByKey = mutableMapOf<Triple<SourceType, String, String>, List<Library>>()

    override suspend fun getCachedLibraries(
        sourceType: SourceType,
        baseUrl: String,
        username: String,
    ): List<Library> = librariesByKey[Triple(sourceType, baseUrl, username)].orEmpty()

    override suspend fun replaceCachedLibraries(
        sourceType: SourceType,
        baseUrl: String,
        username: String,
        libraries: List<Library>,
    ) {
        librariesByKey[Triple(sourceType, baseUrl, username)] = libraries
    }
}

class FakeGrimmoryCoverRepository : GrimmoryCoverDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY
    var clearCachedCoversCalls: Int = 0
    private var coverUri: Uri? = null
    private var isPersistent: Boolean = true

    constructor(
        coverUri: Uri? = null,
        isPersistent: Boolean = true,
    ) {
        this.coverUri = coverUri
        this.isPersistent = isPersistent
    }

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = coverUri?.let { uri ->
        CachedRemoteCover(
            uri = uri,
            isPersistent = isPersistent,
        )
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        isPersistent = true
        return coverUri
    }

    override suspend fun clearCachedCovers() {
        clearCachedCoversCalls += 1
        coverUri = null
    }
}

class FakeGrimmoryContentRepository(
    private val storedItemIds: MutableSet<String> = mutableSetOf(),
    private val downloadFailure: Throwable? = null,
) : GrimmoryContentDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = if (item.id in storedItemIds) {
        Uri.parse("file:///downloads/${item.id}.${item.format.name.lowercase()}")
    } else {
        null
    }

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

class FakeRemoteDownloadDataSource(
    override val sourceType: SourceType,
    private val storedItemIds: MutableSet<String> = mutableSetOf(),
    private val downloadFailure: Throwable? = null,
    private val failingItemIds: Set<String> = emptySet(),
) : RemoteItemDownloadDataSource {
    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = if (item.id in storedItemIds) {
        Uri.parse("file:///downloads/${item.id}.${item.format.name.lowercase()}")
    } else {
        null
    }

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? {
        downloadFailure?.let { throw it }
        if (!supports(item)) return null
        if (item.id in failingItemIds) return null
        storedItemIds += item.id
        return storedDocumentUri(item)
    }

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = items
        .filter { item -> item.id in storedItemIds }
        .mapTo(mutableSetOf(), ReadableItem::id)

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = supports(item) && storedItemIds.remove(item.id)
}

class BlockingRemoteDownloadDataSource(
    override val sourceType: SourceType,
) : RemoteItemDownloadDataSource {
    private val startedItemIds = mutableListOf<String>()
    private val startedFirstDownload = CompletableDeferred<Unit>()
    private val releaseFirstDownload = CompletableDeferred<Unit>()

    fun startedItemIds(): List<String> = startedItemIds.toList()

    suspend fun awaitFirstDownloadStarted() {
        startedFirstDownload.await()
    }

    fun cancelFirstDownload() {
        releaseFirstDownload.cancel(CancellationException("Cancelled for test"))
    }

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = null

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? {
        if (!supports(item)) return null
        startedItemIds += item.id
        if (!startedFirstDownload.isCompleted) {
            startedFirstDownload.complete(Unit)
            releaseFirstDownload.await()
        }
        return Uri.parse("file:///downloads/${item.id}.${item.format.name.lowercase()}")
    }

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = emptySet()

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
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
    private val expectedImportUri: Uri? = null,
) : LocalDocumentDataSource, LocalDocumentHydrator {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val importedItemsState = MutableStateFlow(importedItems.toList())
    override val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState

    override fun getImportedItems(): List<ReadableItem> = importedItems.toList()

    override suspend fun hydrate() = Unit

    override suspend fun import(uri: Uri): ReadableItem? {
        if (expectedImportUri != null && uri != expectedImportUri) return null
        return importResult?.also { item ->
            importedItems.removeAll { existing -> existing.id == item.id }
            importedItems += item
            importedDocumentUris[item.id] = uri
            importedItemsState.value = importedItems.toList()
        }
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

class FakeJournalRepository : JournalRepository {
    private val snapshotFlow = MutableStateFlow(JournalSnapshot())

    override fun observeSnapshot(): Flow<JournalSnapshot> = snapshotFlow

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(
        item: ReadableItem,
        rating: Int?,
        reviewText: String,
    ) = Unit

    override suspend fun saveJournalEntry(
        item: JournalItemRef,
        rating: Int?,
        reviewText: String,
    ) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit

}

class FakeComicReaderPreferencesStore : ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        MutableStateFlow(emptyMap())
}

class FakeComicReadingDirectionPreferencesStore : ComicReadingDirectionPreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(
        ComicReadingDirectionPreferences(),
    )
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val preferencesFlow: StateFlow<ComicReadingDirectionPreferences> = mutablePreferencesFlow

    override suspend fun setLibraryDirection(
        libraryId: String,
        direction: ComicReadingDirection,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            libraryDirectionsById = mutablePreferencesFlow.value.libraryDirectionsById + (libraryId to direction),
        )
    }

    override suspend fun setSeriesDirectionOverride(
        seriesKey: String,
        direction: ComicReadingDirection?,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            seriesDirectionsByKey = if (direction == null) {
                mutablePreferencesFlow.value.seriesDirectionsByKey - seriesKey
            } else {
                mutablePreferencesFlow.value.seriesDirectionsByKey + (seriesKey to direction)
            },
        )
    }
}

class FakeComicReaderModePreferencesStore : ComicReaderModePreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(
        ComicReaderModePreferences(),
    )
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val preferencesFlow: StateFlow<ComicReaderModePreferences> = mutablePreferencesFlow

    override suspend fun setLibraryMode(
        libraryId: String,
        mode: ComicReaderMode,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            libraryModesById = mutablePreferencesFlow.value.libraryModesById + (libraryId to mode),
        )
    }

    override suspend fun setSeriesModeOverride(
        seriesKey: String,
        mode: ComicReaderMode?,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            seriesModesByKey = if (mode == null) {
                mutablePreferencesFlow.value.seriesModesByKey - seriesKey
            } else {
                mutablePreferencesFlow.value.seriesModesByKey + (seriesKey to mode)
            },
        )
    }
}

class FakeSettingsPreferencesStore(
    initialPreferences: SettingsPreferences = SettingsPreferences(),
) : SettingsPreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<SettingsPreferences> = mutablePreferencesFlow

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        mutablePreferencesFlow.value = SettingsPreferences(themeMode = themeMode)
    }
}

class FakeLibraryBrowsePreferencesStore(
    initialPreferences: LibraryBrowsePreferences = LibraryBrowsePreferences(),
) : LibraryBrowsePreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<LibraryBrowsePreferences> = mutablePreferencesFlow

    override suspend fun setFilters(
        sourceType: SourceType,
        filters: com.example.unifiedreader.core.model.LibraryFilterState,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            filtersBySource = if (filters.isEmpty) {
                mutablePreferencesFlow.value.filtersBySource - sourceType
            } else {
                mutablePreferencesFlow.value.filtersBySource + (sourceType to filters)
            },
        )
    }

    override suspend fun setSortMode(
        sourceType: SourceType,
        sortMode: com.example.unifiedreader.core.model.LibrarySortMode,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            sortsBySource = if (sortMode == com.example.unifiedreader.feature.library.defaultLibrarySortMode) {
                mutablePreferencesFlow.value.sortsBySource - sourceType
            } else {
                mutablePreferencesFlow.value.sortsBySource + (sourceType to sortMode)
            },
        )
    }

    override suspend fun setFilterPanels(
        sourceType: SourceType,
        panels: List<com.example.unifiedreader.core.model.LibraryFilterPanel>,
    ) {
        val resolvedPanels = com.example.unifiedreader.core.model.resolveLibraryFilterPanels(
            sourceType = sourceType,
            preferredPanels = panels,
        )
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            filterPanelsBySource = if (
                resolvedPanels == com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels(sourceType)
            ) {
                mutablePreferencesFlow.value.filterPanelsBySource - sourceType
            } else {
                mutablePreferencesFlow.value.filterPanelsBySource + (sourceType to resolvedPanels)
            },
        )
    }

    override suspend fun setGridCellSizeDp(
        gridCellSizeDp: Int,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            gridCellSizeDp = com.example.unifiedreader.feature.library.sanitizeLibraryGridCellSizeDp(
                gridCellSizeDp,
            ),
        )
    }

    override suspend fun setFadeReadItems(
        fadeReadItems: Boolean,
    ) {
        mutablePreferencesFlow.value = mutablePreferencesFlow.value.copy(
            fadeReadItems = fadeReadItems,
        )
    }
}

class FakeReaderSessionStore(
    initialProgress: Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot> = emptyMap(),
    initialSessions: Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot> = emptyMap(),
) : ReaderSessionStore, ReaderSessionHydrator {
    private val progressByItemId =
        MutableStateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>>(initialProgress)
    private val sessionByItemId =
        MutableStateFlow<Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>>(initialSessions)
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
        isExplicitUnreadReset: Boolean,
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
                isExplicitUnreadReset = isExplicitUnreadReset,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem) = sessionByItemId.value[item.id]

    override suspend fun replaceProgressIfCurrent(
        expected: com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot,
        replacement: com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot?,
    ): Boolean {
        val current = progressByItemId.value[expected.itemId] ?: return false
        if (current != expected) return false
        progressByItemId.value = if (replacement == null) {
            progressByItemId.value - expected.itemId
        } else {
            progressByItemId.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionByItemId.value[itemId]
            ?: com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(itemId = itemId)
        sessionByItemId.value = sessionByItemId.value + (
            itemId to current.copy(
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

    override fun restoreItem(itemId: String) = Unit

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
    var pushPendingProgressCalls: Int = 0
        private set

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        syncItemCalls += 1
        completionGate.await()
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        pushPendingProgressCalls += 1
        return emptyList()
    }

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
    ): List<SourceProgressSyncFailure> = emptyList()
}

class NetworkFailingSourceProgressSync(
    override val sourceType: SourceType,
) : SourceProgressSync {
    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        throw IOException("Network unavailable")
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> = emptyList()
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
