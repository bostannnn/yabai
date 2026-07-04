package com.example.unifiedreader

import android.content.Context
import androidx.work.WorkManager
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.UnifiedReaderDatabaseFactory
import com.example.unifiedreader.core.database.dao.DownloadQueueDao
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.JournalDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.network.buildHttpClient
import com.example.unifiedreader.data.grimmory.GrimmoryRemoteJournalPusher
import com.example.unifiedreader.data.grimmory.KtorGrimmoryApi
import com.example.unifiedreader.data.kavita.KavitaRemoteJournalPusher
import com.example.unifiedreader.data.kavita.KtorKavitaApi
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.data.repository.RemoteJournalPusher
import com.example.unifiedreader.data.repository.RemoteJournalPusherRegistry
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.data.stump.KtorStumpApi
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.SampleCatalogRepository
import com.example.unifiedreader.data.repository.SourceRemoteJournalPusher
import com.example.unifiedreader.feature.downloads.DownloadCompletionSideEffect
import com.example.unifiedreader.feature.downloads.DownloadQueueStore
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import com.example.unifiedreader.feature.downloads.RemoteDownloadWorkerRunner
import com.example.unifiedreader.feature.downloads.RoomDownloadQueueStore
import com.example.unifiedreader.journal.JournalEventRecorder
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import com.example.unifiedreader.feature.journal.RoomJournalRepository
import com.example.unifiedreader.feature.library.DataStoreLibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.LibraryBrowseStateCoordinator
import com.example.unifiedreader.feature.library.LibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.navigation.DataStoreSourceNavigationPreferencesStore
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferencesStore
import com.example.unifiedreader.feature.navigation.SourceNavigationStateCoordinator
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.comics.ComicsReaderSessionAdapter
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.comics.comicsReaderFactory
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import com.example.unifiedreader.feature.reader.epub.epubReaderFactory
import com.example.unifiedreader.feature.reader.pdf.pdfReaderFactory
import com.example.unifiedreader.feature.settings.DataStoreSettingsPreferencesStore
import com.example.unifiedreader.feature.settings.SettingsPreferencesStore
import com.example.unifiedreader.feature.settings.SettingsStateCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import java.io.File
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers()

    @Provides
    @Singleton
    fun provideReaderWorkCoordinator(): ReaderWorkCoordinator = ReaderWorkCoordinator()

    @Provides
    @Singleton
    fun provideAppActiveRouteStore(
        store: SharedPreferencesAppActiveRouteStore,
    ): AppActiveRouteStore = store

    @Provides
    @Singleton
    fun provideAppNetworkConnectivityMonitor(
        monitor: AndroidAppNetworkConnectivityMonitor,
    ): AppNetworkConnectivityMonitor = monitor

    @Provides
    @Singleton
    fun provideSourceNavigationPreferencesStore(
        @ApplicationContext context: Context,
    ): SourceNavigationPreferencesStore = DataStoreSourceNavigationPreferencesStore(context)

    @Provides
    @Singleton
    fun provideSourceNavigationStateCoordinator(
        sourceNavigationPreferencesStore: SourceNavigationPreferencesStore,
    ): SourceNavigationStateCoordinator = SourceNavigationStateCoordinator(sourceNavigationPreferencesStore)

    @Provides
    @Singleton
    fun provideSettingsPreferencesStore(
        @ApplicationContext context: Context,
    ): SettingsPreferencesStore = DataStoreSettingsPreferencesStore(context)

    @Provides
    @Singleton
    fun provideSettingsStateCoordinator(
        settingsPreferencesStore: SettingsPreferencesStore,
    ): SettingsStateCoordinator = SettingsStateCoordinator(settingsPreferencesStore)

    @Provides
    @Singleton
    fun provideLibraryBrowsePreferencesStore(
        @ApplicationContext context: Context,
    ): LibraryBrowsePreferencesStore = DataStoreLibraryBrowsePreferencesStore(context)

    @Provides
    @Singleton
    fun provideLibraryBrowseStateCoordinator(
        libraryBrowsePreferencesStore: LibraryBrowsePreferencesStore,
    ): LibraryBrowseStateCoordinator = LibraryBrowseStateCoordinator(libraryBrowsePreferencesStore)

    @Provides
    @Singleton
    fun provideRemoteItemLocalStateCache(
        @ApplicationContext context: Context,
    ): RemoteItemLocalStateCache = SharedPreferencesRemoteItemLocalStateCache(context)

    @Provides
    @Singleton
    fun provideComicReadingDirectionPreferencesStore(
        @ApplicationContext context: Context,
    ): ComicReadingDirectionPreferencesStore = DataStoreComicReadingDirectionPreferencesStore(context)

    @Provides
    @Singleton
    fun provideComicReaderModePreferencesStore(
        @ApplicationContext context: Context,
    ): ComicReaderModePreferencesStore = DataStoreComicReaderModePreferencesStore(context)

    @Provides
    @Singleton
    fun provideGrimmoryCredentialStore(
        @ApplicationContext context: Context,
    ): GrimmoryCredentialStore = SharedPreferencesGrimmoryCredentialStore(context)

    @Provides
    @Singleton
    fun provideKavitaCredentialStore(
        @ApplicationContext context: Context,
    ): KavitaCredentialStore = SharedPreferencesKavitaCredentialStore(context)

    @Provides
    @Singleton
    fun provideStumpCredentialStore(
        @ApplicationContext context: Context,
    ): StumpCredentialStore = SharedPreferencesStumpCredentialStore(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): UnifiedReaderDatabase = UnifiedReaderDatabaseFactory.create(context)

    @Provides
    fun provideImportedComicArchiveDao(
        database: UnifiedReaderDatabase,
    ): ImportedComicArchiveDao = database.importedComicArchiveDao()

    @Provides
    fun provideDownloadQueueDao(
        database: UnifiedReaderDatabase,
    ): DownloadQueueDao = database.downloadQueueDao()

    @Provides
    fun provideJournalDao(
        database: UnifiedReaderDatabase,
    ): JournalDao = database.journalDao()

    @Provides
    fun provideReadingProgressDao(
        database: UnifiedReaderDatabase,
    ): ReadingProgressDao = database.readingProgressDao()

    @Provides
    fun provideReaderSessionStateDao(
        database: UnifiedReaderDatabase,
    ): ReaderSessionStateDao = database.readerSessionStateDao()

    @Provides
    @Singleton
    fun provideAppStartupInitializer(
        @ApplicationContext context: Context,
        importedComicArchiveDao: ImportedComicArchiveDao,
        remoteDownloadQueueController: RemoteDownloadQueueController,
        readingProgressDao: ReadingProgressDao,
        readerSessionStateDao: ReaderSessionStateDao,
        remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    ): AppStartupTasks = AppStartupInitializer(
        context = context,
        importedComicArchiveDao = importedComicArchiveDao,
        remoteDownloadQueueController = remoteDownloadQueueController,
        readingProgressDao = readingProgressDao,
        readerSessionStateDao = readerSessionStateDao,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    )

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideDownloadQueueStore(
        downloadQueueDao: DownloadQueueDao,
    ): DownloadQueueStore = RoomDownloadQueueStore(downloadQueueDao)

    @Provides
    @Singleton
    fun provideJournalRepository(
        journalDao: JournalDao,
        database: UnifiedReaderDatabase,
    ): RoomJournalRepository = RoomJournalRepository(
        journalDao = journalDao,
        remoteCatalogDao = database.remoteCatalogDao(),
    )

    @Provides
    fun provideJournalRepositoryContract(
        repository: RoomJournalRepository,
    ): JournalRepository = repository

    @Provides
    fun provideJournalEventRecorder(
        repository: RoomJournalRepository,
    ): JournalEventRecorder = repository

    @Provides
    fun provideJournalReadingSessionRecorder(
        repository: RoomJournalRepository,
        remoteItemLocalAvailability: RemoteItemLocalAvailability,
        remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    ): JournalReadingSessionRecorder = RemoteAwareJournalReadingSessionRecorder(
        delegate = repository,
        remoteItemLocalAvailability = remoteItemLocalAvailability,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
    )

    @Provides
    fun provideRemoteJournalPusher(
        registry: RemoteJournalPusherRegistry,
    ): RemoteJournalPusher = registry

    @Provides
    fun provideSourceRemoteJournalPushers(
        kavitaSessionClient: KavitaJournalSessionClientAdapter,
        grimmorySessionClient: GrimmoryJournalSessionClientAdapter,
        journalRepository: JournalRepository,
    ): List<@JvmSuppressWildcards SourceRemoteJournalPusher> {
        val kavitaPusher = KavitaRemoteJournalPusher(
            sessionClient = kavitaSessionClient,
            journalRepository = journalRepository,
        )
        val grimmoryPusher = GrimmoryRemoteJournalPusher(
            sessionClient = grimmorySessionClient,
            journalRepository = journalRepository,
        )
        return listOf(
            object : SourceRemoteJournalPusher {
                override val sourceType = SourceType.KAVITA
                override val supportedArtifacts = setOf(RemoteSyncArtifact.JOURNAL_ENTRY)

                override suspend fun pushPendingJournalEntries(
                    items: List<ReadableItem>,
                    artifacts: Set<RemoteSyncArtifact>,
                ) = kavitaPusher.pushPendingJournalEntries(
                    items = items,
                    pushJournalEntries = RemoteSyncArtifact.JOURNAL_ENTRY in artifacts,
                )
            },
            object : SourceRemoteJournalPusher {
                override val sourceType = SourceType.GRIMMORY
                override val supportedArtifacts = setOf(
                    RemoteSyncArtifact.JOURNAL_ENTRY,
                    RemoteSyncArtifact.READING_SESSION,
                )

                override suspend fun pushPendingJournalEntries(
                    items: List<ReadableItem>,
                    artifacts: Set<RemoteSyncArtifact>,
                ) = grimmoryPusher.pushPendingJournalEntries(
                    items = items,
                    pushJournalEntries = RemoteSyncArtifact.JOURNAL_ENTRY in artifacts,
                    pushReadingSessions = RemoteSyncArtifact.READING_SESSION in artifacts,
                )
            },
        )
    }

    @Provides
    @Singleton
    fun provideDownloadWorkScheduler(
        workManager: WorkManager,
    ): com.example.unifiedreader.feature.downloads.DownloadWorkScheduler = WorkManagerDownloadScheduler(workManager)

    @Provides
    fun provideDownloadCompletionSideEffects(
        sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
        downloadedRemoteCoverCacheBackfillSideEffect: DownloadedRemoteCoverCacheBackfillSideEffect,
    ): List<@JvmSuppressWildcards DownloadCompletionSideEffect> = listOf(
        downloadedRemoteCoverCacheBackfillSideEffect,
        SourceProgressSyncDownloadCompletionSideEffect(sourceProgressSyncs),
    )

    @Provides
    @Singleton
    fun provideDownloadedRemoteCoverBackfiller(
        backfiller: DownloadedRemoteCoverCacheBackfiller,
    ): DownloadedRemoteCoverBackfiller = backfiller

    @Provides
    @Singleton
    fun provideDownloadedPdfCoverRenderer(
        renderer: AndroidDownloadedPdfCoverRenderer,
    ): DownloadedPdfCoverRenderer = renderer

    @Provides
    @Singleton
    fun provideRemoteDownloadQueueController(
        store: DownloadQueueStore,
        scheduler: com.example.unifiedreader.feature.downloads.DownloadWorkScheduler,
        remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    ): RemoteDownloadQueueController = RemoteDownloadQueueController(
        store = store,
        scheduler = scheduler,
        remoteDownloadDataSources = remoteDownloadDataSources,
    )

    @Provides
    @Singleton
    fun provideRemoteDownloadWorkerRunner(
        store: DownloadQueueStore,
        remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
        completionSideEffects: List<@JvmSuppressWildcards DownloadCompletionSideEffect>,
        remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    ): RemoteDownloadWorkerRunner = RemoteDownloadWorkerRunner(
        store = store,
        remoteDownloadDataSources = remoteDownloadDataSources,
        completionSideEffects = completionSideEffects,
        canRunSource = remoteSourceConnectivityPolicy::canScheduleDownload,
    )

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = buildHttpClient()

    @Provides
    @Singleton
    fun provideSampleCatalogRepository(): SampleCatalogRepository = SampleCatalogRepository()

    @Provides
    @Singleton
    fun provideRoomRemoteCatalogRepository(
        database: UnifiedReaderDatabase,
    ): RoomRemoteCatalogRepository = RoomRemoteCatalogRepository(
        remoteCatalogDao = database.remoteCatalogDao(),
    )

    @Provides
    fun provideRemoteCatalogWriter(
        repository: RoomRemoteCatalogRepository,
    ): RemoteCatalogWriter = repository

    @Provides
    fun provideRemoteCatalogReadiness(
        repository: RoomRemoteCatalogRepository,
    ): RemoteCatalogReadiness = repository

    @Provides
    fun provideRemoteCatalogHydrator(
        repository: RoomRemoteCatalogRepository,
    ): RemoteCatalogHydrator = repository

    @Provides
    fun provideRemoteLibraryCacheStore(
        repository: RoomRemoteCatalogRepository,
    ): RemoteLibraryCacheStore = repository

    @Provides
    fun provideGrimmoryApiFactory(
        httpClient: HttpClient,
    ): GrimmoryApiFactory = GrimmoryApiFactory { baseUrl ->
        KtorGrimmoryApi(
            httpClient = httpClient,
            baseUrl = baseUrl,
        )
    }

    @Provides
    fun provideKavitaApiFactory(
        httpClient: HttpClient,
    ): KavitaApiFactory = KavitaApiFactory { baseUrl ->
        KtorKavitaApi(
            httpClient = httpClient,
            baseUrl = baseUrl,
        )
    }

    @Provides
    fun provideStumpApiFactory(
        httpClient: HttpClient,
    ): StumpApiFactory = StumpApiFactory { baseUrl ->
        KtorStumpApi(
            httpClient = httpClient,
            baseUrl = baseUrl,
        )
    }

    @Provides
    @Singleton
    fun provideProgressSyncStore(
        readerSessionStore: ReaderSessionStore,
    ): ProgressSyncStore = ReaderSessionProgressStoreAdapter(readerSessionStore)

    @Provides
    @Singleton
    fun provideKavitaCatalogSyncService(
        kavitaApiFactory: KavitaApiFactory,
        remoteCatalogWriter: RemoteCatalogWriter,
        readerSessionStore: ReaderSessionStore,
        journalRepository: JournalRepository,
    ): KavitaCatalogSyncer = KavitaCatalogSyncService(
        kavitaApiFactory = kavitaApiFactory,
        remoteCatalogWriter = remoteCatalogWriter,
        readerSessionStore = readerSessionStore,
        journalRepository = journalRepository,
    )

    @Provides
    @Singleton
    fun provideGrimmoryCatalogSyncService(
        grimmoryApiFactory: GrimmoryApiFactory,
        remoteCatalogWriter: RemoteCatalogWriter,
        readerSessionStore: ReaderSessionStore,
        journalRepository: JournalRepository,
    ): GrimmoryCatalogSyncer = GrimmoryCatalogSyncService(
        grimmoryApiFactory = grimmoryApiFactory,
        remoteCatalogWriter = remoteCatalogWriter,
        readerSessionStore = readerSessionStore,
        journalRepository = journalRepository,
    )

    @Provides
    @Singleton
    fun provideStumpCatalogSyncService(
        stumpApiFactory: StumpApiFactory,
        remoteCatalogWriter: RemoteCatalogWriter,
        readerSessionStore: ReaderSessionStore,
    ): StumpCatalogSyncer = StumpCatalogSyncService(
        stumpApiFactory = stumpApiFactory,
        remoteCatalogWriter = remoteCatalogWriter,
        readerSessionStore = readerSessionStore,
    )

    @Provides
    fun provideRemoteSourceHealthChecks(
        kavitaApiFactory: KavitaApiFactory,
        kavitaCredentialStore: KavitaCredentialStore,
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
        grimmoryApiFactory: GrimmoryApiFactory,
        grimmoryCredentialStore: GrimmoryCredentialStore,
    ): List<@JvmSuppressWildcards RemoteSourceHealthCheck> = listOf(
        KavitaRemoteSourceHealthCheck(
            kavitaApiFactory = kavitaApiFactory,
            kavitaCredentialStore = kavitaCredentialStore,
        ),
        StumpRemoteSourceHealthCheck(
            stumpApiFactory = stumpApiFactory,
            stumpCredentialStore = stumpCredentialStore,
        ),
        GrimmoryRemoteSourceHealthCheck(
            grimmoryApiFactory = grimmoryApiFactory,
            grimmoryCredentialStore = grimmoryCredentialStore,
        ),
    )

    @Provides
    @Singleton
    fun provideGrimmoryContentRepository(
        @ApplicationContext context: Context,
        grimmoryApiFactory: GrimmoryApiFactory,
        grimmoryCredentialStore: GrimmoryCredentialStore,
        cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
    ): GrimmoryContentRepository = GrimmoryContentRepository(
        context = context.applicationContext,
        grimmoryApiFactory = grimmoryApiFactory,
        grimmoryCredentialStore = grimmoryCredentialStore,
        cacheInvalidationDispatcher = cacheInvalidationDispatcher,
    )

    @Provides
    fun provideGrimmoryContentDataSource(
        repository: GrimmoryContentRepository,
    ): GrimmoryContentDataSource = repository

    @Provides
    fun provideRemoteItemDownloadDataSources(
        grimmoryContentRepository: GrimmoryContentRepository,
        kavitaContentRepository: KavitaContentRepository,
        stumpContentRepository: StumpContentRepository,
    ): List<@JvmSuppressWildcards RemoteItemDownloadDataSource> = listOf(
        grimmoryContentRepository,
        kavitaContentRepository,
        stumpContentRepository,
    )

    @Provides
    @Singleton
    fun provideKavitaCoverRepository(
        kavitaApiFactory: KavitaApiFactory,
        kavitaCredentialStore: KavitaCredentialStore,
        coverCacheResolver: RemoteCoverCacheResolver,
    ): KavitaCoverDataSource = KavitaCoverRepository(
        kavitaApiFactory = kavitaApiFactory,
        kavitaCredentialStore = kavitaCredentialStore,
        coverCacheResolver = coverCacheResolver,
    )

    @Provides
    @Singleton
    fun provideGrimmoryCoverRepository(
        grimmoryApiFactory: GrimmoryApiFactory,
        grimmoryCredentialStore: GrimmoryCredentialStore,
        coverCacheResolver: RemoteCoverCacheResolver,
    ): GrimmoryCoverDataSource = GrimmoryCoverRepository(
        grimmoryApiFactory = grimmoryApiFactory,
        grimmoryCredentialStore = grimmoryCredentialStore,
        coverCacheResolver = coverCacheResolver,
    )

    @Provides
    @Singleton
    fun provideStumpCoverRepository(
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
        coverCacheResolver: RemoteCoverCacheResolver,
    ): StumpCoverDataSource = StumpCoverRepository(
        stumpApiFactory = stumpApiFactory,
        stumpCredentialStore = stumpCredentialStore,
        coverCacheResolver = coverCacheResolver,
    )

    @Provides
    @Singleton
    fun provideGrimmoryProgressSyncService(
        grimmoryApiFactory: GrimmoryApiFactory,
        grimmoryCredentialStore: GrimmoryCredentialStore,
        readerSessionStore: ReaderSessionStore,
    ): GrimmoryProgressSyncService = GrimmoryProgressSyncService(
        grimmoryApiFactory = grimmoryApiFactory,
        grimmoryCredentialStore = grimmoryCredentialStore,
        readerSessionStore = readerSessionStore,
    )

    @Provides
    @Singleton
    fun provideStumpProgressSyncService(
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
        readerSessionStore: ReaderSessionStore,
    ): StumpProgressSyncService = StumpProgressSyncService(
        stumpApiFactory = stumpApiFactory,
        stumpCredentialStore = stumpCredentialStore,
        readerSessionStore = readerSessionStore,
    )

    @Provides
    @Singleton
    fun provideKavitaProgressSyncService(
        kavitaApiFactory: KavitaApiFactory,
        kavitaCredentialStore: KavitaCredentialStore,
        readerSessionStore: ReaderSessionStore,
    ): KavitaProgressSyncService = KavitaProgressSyncService(
        kavitaApiFactory = kavitaApiFactory,
        kavitaCredentialStore = kavitaCredentialStore,
        readerSessionStore = readerSessionStore,
    )

    @Provides
    fun provideSourceProgressSyncs(
        kavitaProgressSyncService: KavitaProgressSyncService,
        stumpProgressSyncService: StumpProgressSyncService,
        grimmoryProgressSyncService: GrimmoryProgressSyncService,
    ): List<@JvmSuppressWildcards SourceProgressSync> = listOf(
        kavitaProgressSyncService,
        stumpProgressSyncService,
        grimmoryProgressSyncService,
    )

    @Provides
    @Singleton
    fun provideCompositeCatalogRepository(
        sampleCatalogRepository: SampleCatalogRepository,
        remoteCatalogRepository: RoomRemoteCatalogRepository,
    ): CompositeCatalogRepository = CompositeCatalogRepository(
        sampleCatalogRepository = sampleCatalogRepository,
        remoteCatalogRepository = remoteCatalogRepository,
    )

    @Provides
    fun provideLibraryRepository(
        repository: CompositeCatalogRepository,
    ): LibraryRepository = repository

    @Provides
    fun provideReadableItemRepository(
        repository: CompositeCatalogRepository,
    ): ReadableItemRepository = repository

    @Provides
    @Singleton
    fun provideLocalDocumentRepository(
        @ApplicationContext context: Context,
        importedComicArchiveDao: ImportedComicArchiveDao,
        appStartupTasks: AppStartupTasks,
        readerSessionStore: ReaderSessionStore,
        previewStore: PersistentComicPagePreviewStore,
        readerWorkCoordinator: ReaderWorkCoordinator,
    ): LocalDocumentRepository {
        return LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = importedComicArchiveDao,
            appStartupTasks = appStartupTasks,
            onItemImported = readerSessionStore::restoreItem,
            previewStore = previewStore,
            readerWorkCoordinator = readerWorkCoordinator,
        )
    }

    @Provides
    fun provideLocalDocumentDataSource(
        repository: LocalDocumentRepository,
    ): LocalDocumentDataSource = repository

    @Provides
    fun provideLocalComicDocumentDataSource(
        repository: LocalDocumentRepository,
    ): LocalComicDocumentDataSource = repository

    @Provides
    fun provideLocalDocumentHydrator(
        repository: LocalDocumentRepository,
    ): LocalDocumentHydrator = repository

    @Provides
    @Singleton
    fun provideImportedItemDeletionService(
        localDocumentDataSource: LocalDocumentDataSource,
        readerSessionStore: ReaderSessionStore,
    ): ImportedItemDeletionService = DefaultImportedItemDeletionService(
        localDocumentDataSource = localDocumentDataSource,
        readerSessionStore = readerSessionStore,
    )

    @Provides
    @Singleton
    fun provideReaderSessionStore(
        readingProgressDao: ReadingProgressDao,
        readerSessionStateDao: ReaderSessionStateDao,
        appStartupTasks: AppStartupTasks,
    ): RoomReaderSessionStore = RoomReaderSessionStore(
        readingProgressDao = readingProgressDao,
        readerSessionStateDao = readerSessionStateDao,
        appStartupTasks = appStartupTasks,
    )

    @Provides
    fun provideReaderSessionStoreContract(
        store: RoomReaderSessionStore,
    ): ReaderSessionStore = store

    @Provides
    fun provideReaderSessionHydrator(
        store: RoomReaderSessionStore,
    ): ReaderSessionHydrator = store

    @Provides
    @Singleton
    fun provideLocalComicPageSource(
        localComicDocumentDataSource: LocalComicDocumentDataSource,
    ): LocalComicPageSource = LocalComicPageSource(localComicDocumentDataSource)

    @Provides
    @Singleton
    fun providePersistentComicPagePreviewStore(
        @ApplicationContext context: Context,
    ): PersistentComicPagePreviewStore = PersistentComicPagePreviewStore(
        rootDirectory = File(context.cacheDir, "comic-page-previews"),
    )

    @Provides
    @Singleton
    fun provideKavitaPageSource(
        kavitaApiFactory: KavitaApiFactory,
        kavitaCredentialStore: KavitaCredentialStore,
        remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
        previewStore: PersistentComicPagePreviewStore,
        readerWorkCoordinator: ReaderWorkCoordinator,
    ): KavitaPageSource = KavitaPageSource(
        kavitaApiFactory = kavitaApiFactory,
        kavitaCredentialStore = kavitaCredentialStore,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        previewStore = previewStore,
        readerWorkCoordinator = readerWorkCoordinator,
    )

    @Provides
    @Singleton
    fun provideStumpPageSource(
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
        remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
        readerWorkCoordinator: ReaderWorkCoordinator,
    ): StumpPageSource = StumpPageSource(
        stumpApiFactory = stumpApiFactory,
        stumpCredentialStore = stumpCredentialStore,
        remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        readerWorkCoordinator = readerWorkCoordinator,
    )

    @Provides
    @Singleton
    fun provideCompositeComicPageSource(
        localComicPageSource: LocalComicPageSource,
        downloadedRemoteComicPageSource: DownloadedRemoteComicPageSource,
        kavitaPageSource: KavitaPageSource,
        stumpPageSource: StumpPageSource,
        cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
    ): CompositeComicPageSource = CompositeComicPageSource(
        localComicPageSource = localComicPageSource,
        downloadedRemoteComicPageSource = downloadedRemoteComicPageSource,
        kavitaPageSource = kavitaPageSource,
        stumpPageSource = stumpPageSource,
        cacheInvalidationDispatcher = cacheInvalidationDispatcher,
    )

    @Provides
    fun provideCompositeComicPageSourceAsComicPageSource(
        compositeComicPageSource: CompositeComicPageSource,
    ): ComicPageSource = compositeComicPageSource

    @Provides
    fun provideComicPageSourceCacheController(
        compositeComicPageSource: CompositeComicPageSource,
    ): ComicPageSourceCacheController = compositeComicPageSource

    @Provides
    @Singleton
    fun provideComicsReaderSessionAdapter(
        readerSessionStore: ReaderSessionStore,
        directionPreferencesStore: ComicReadingDirectionPreferencesStore,
        readerModePreferencesStore: ComicReaderModePreferencesStore,
    ): ComicsReaderSessionAdapter = ComicsReaderSessionAdapter(
        sessionStore = readerSessionStore,
        directionPreferencesStore = directionPreferencesStore,
        readerModePreferencesStore = readerModePreferencesStore,
    )

    @Provides
    fun provideComicReaderProgressStore(
        adapter: ComicsReaderSessionAdapter,
    ): ComicReaderProgressStore = adapter

    @Provides
    fun provideComicReaderPreferencesStore(
        adapter: ComicsReaderSessionAdapter,
    ): ComicReaderPreferencesStore = adapter

    @Provides
    @Singleton
    fun provideReaderFactories(
        comicPageSource: ComicPageSource,
        adapter: ComicsReaderSessionAdapter,
        journalEventRecorder: JournalEventRecorder,
        journalReadingSessionRecorder: JournalReadingSessionRecorder,
        readerSessionStore: ReaderSessionStore,
        localDocumentRepository: LocalDocumentRepository,
        remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
        remoteDocumentReadCache: RemoteDocumentReadCache,
    ): List<@JvmSuppressWildcards ReaderScreenFactory> = listOf(
        comicsReaderFactory(
            pageSource = comicPageSource,
            progressStore = JournalRecordingComicReaderProgressStore(
                delegate = adapter,
                journalEventRecorder = journalEventRecorder,
            ),
            preferencesStore = adapter,
            journalReadingSessionRecorder = journalReadingSessionRecorder,
        ),
        epubReaderFactory(
            sessionStore = JournalRecordingReaderSessionStore(
                delegate = readerSessionStore,
                journalEventRecorder = journalEventRecorder,
            ),
            journalReadingSessionRecorder = journalReadingSessionRecorder,
            documentUriProvider = { item ->
                localDocumentRepository.getDocumentUri(item)
                    ?: remoteDownloadDataSources.firstOrNull { dataSource ->
                        dataSource.supports(item)
                    }?.storedDocumentUri(item)
                    ?: remoteDocumentReadCache.documentUriForReading(item)
            },
        ),
        pdfReaderFactory(
            sessionStore = JournalRecordingReaderSessionStore(
                delegate = readerSessionStore,
                journalEventRecorder = journalEventRecorder,
            ),
            journalReadingSessionRecorder = journalReadingSessionRecorder,
            documentUriProvider = { item ->
                localDocumentRepository.getDocumentUri(item)
                    ?: remoteDownloadDataSources.firstOrNull { dataSource ->
                        dataSource.supports(item)
                    }?.storedDocumentUri(item)
                    ?: remoteDocumentReadCache.documentUriForReading(item)
            },
        ),
    )
}
