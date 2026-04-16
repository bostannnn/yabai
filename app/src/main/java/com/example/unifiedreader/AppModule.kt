package com.example.unifiedreader

import android.content.Context
import androidx.work.WorkManager
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.UnifiedReaderDatabaseFactory
import com.example.unifiedreader.core.database.dao.DownloadQueueDao
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.network.buildHttpClient
import com.example.unifiedreader.data.grimmory.KtorGrimmoryApi
import com.example.unifiedreader.data.kavita.KtorKavitaApi
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.data.stump.KtorStumpApi
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.SampleCatalogRepository
import com.example.unifiedreader.feature.downloads.DownloadCompletionSideEffect
import com.example.unifiedreader.feature.downloads.DownloadQueueStore
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import com.example.unifiedreader.feature.downloads.RemoteDownloadWorkerRunner
import com.example.unifiedreader.feature.downloads.RoomDownloadQueueStore
import com.example.unifiedreader.feature.library.DataStoreLibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.LibraryBrowseStateCoordinator
import com.example.unifiedreader.feature.library.LibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.navigation.DataStoreSourceNavigationPreferencesStore
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferencesStore
import com.example.unifiedreader.feature.navigation.SourceNavigationStateCoordinator
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.comics.ComicsReaderSessionAdapter
import com.example.unifiedreader.feature.reader.comics.comicsReaderFactory
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
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
    ): AppStartupTasks = AppStartupInitializer(
        context = context,
        importedComicArchiveDao = importedComicArchiveDao,
        remoteDownloadQueueController = remoteDownloadQueueController,
        readingProgressDao = readingProgressDao,
        readerSessionStateDao = readerSessionStateDao,
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
    fun provideDownloadWorkScheduler(
        workManager: WorkManager,
    ): com.example.unifiedreader.feature.downloads.DownloadWorkScheduler = WorkManagerDownloadScheduler(workManager)

    @Provides
    fun provideDownloadCompletionSideEffects(
        sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
    ): List<@JvmSuppressWildcards DownloadCompletionSideEffect> = listOf(
        SourceProgressSyncDownloadCompletionSideEffect(sourceProgressSyncs),
    )

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
    ): RemoteDownloadWorkerRunner = RemoteDownloadWorkerRunner(
        store = store,
        remoteDownloadDataSources = remoteDownloadDataSources,
        completionSideEffects = completionSideEffects,
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
    ): KavitaCatalogSyncer = KavitaCatalogSyncService(
        kavitaApiFactory = kavitaApiFactory,
        remoteCatalogWriter = remoteCatalogWriter,
        readerSessionStore = readerSessionStore,
    )

    @Provides
    @Singleton
    fun provideGrimmoryCatalogSyncService(
        grimmoryApiFactory: GrimmoryApiFactory,
        remoteCatalogWriter: RemoteCatalogWriter,
        readerSessionStore: ReaderSessionStore,
    ): GrimmoryCatalogSyncer = GrimmoryCatalogSyncService(
        grimmoryApiFactory = grimmoryApiFactory,
        remoteCatalogWriter = remoteCatalogWriter,
        readerSessionStore = readerSessionStore,
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
        coverStore: PersistentRemoteCoverStore,
        thumbnailGenerator: LibraryCoverThumbnailGenerator,
    ): KavitaCoverDataSource = KavitaCoverRepository(
        kavitaApiFactory = kavitaApiFactory,
        kavitaCredentialStore = kavitaCredentialStore,
        coverStore = coverStore,
        thumbnailGenerator = thumbnailGenerator,
    )

    @Provides
    @Singleton
    fun provideGrimmoryCoverRepository(
        grimmoryApiFactory: GrimmoryApiFactory,
        grimmoryCredentialStore: GrimmoryCredentialStore,
        coverStore: PersistentRemoteCoverStore,
        thumbnailGenerator: LibraryCoverThumbnailGenerator,
    ): GrimmoryCoverDataSource = GrimmoryCoverRepository(
        grimmoryApiFactory = grimmoryApiFactory,
        grimmoryCredentialStore = grimmoryCredentialStore,
        coverStore = coverStore,
        thumbnailGenerator = thumbnailGenerator,
    )

    @Provides
    @Singleton
    fun provideStumpCoverRepository(
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
        coverStore: PersistentRemoteCoverStore,
        thumbnailGenerator: LibraryCoverThumbnailGenerator,
    ): StumpCoverDataSource = StumpCoverRepository(
        stumpApiFactory = stumpApiFactory,
        stumpCredentialStore = stumpCredentialStore,
        coverStore = coverStore,
        thumbnailGenerator = thumbnailGenerator,
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
    ): LocalDocumentRepository {
        return LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = importedComicArchiveDao,
            appStartupTasks = appStartupTasks,
            onItemImported = readerSessionStore::restoreItem,
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
    fun provideKavitaPageSource(
        kavitaApiFactory: KavitaApiFactory,
        kavitaCredentialStore: KavitaCredentialStore,
    ): KavitaPageSource = KavitaPageSource(
        kavitaApiFactory = kavitaApiFactory,
        kavitaCredentialStore = kavitaCredentialStore,
    )

    @Provides
    @Singleton
    fun provideStumpPageSource(
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
    ): StumpPageSource = StumpPageSource(
        stumpApiFactory = stumpApiFactory,
        stumpCredentialStore = stumpCredentialStore,
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
        readerSessionStore: ReaderSessionStore,
        localDocumentRepository: LocalDocumentRepository,
        remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    ): List<@JvmSuppressWildcards ReaderScreenFactory> = listOf(
        comicsReaderFactory(
            pageSource = comicPageSource,
            progressStore = adapter,
            preferencesStore = adapter,
        ),
        epubReaderFactory(
            sessionStore = readerSessionStore,
            documentUriProvider = { item ->
                localDocumentRepository.getDocumentUri(item)
                    ?: remoteDownloadDataSources.firstOrNull { dataSource ->
                        dataSource.supports(item)
                    }?.getDocumentUri(item)
            },
        ),
        pdfReaderFactory(
            documentUriProvider = { item ->
                localDocumentRepository.getDocumentUri(item)
                    ?: remoteDownloadDataSources.firstOrNull { dataSource ->
                        dataSource.supports(item)
                    }?.getDocumentUri(item)
            },
        ),
    )
}
