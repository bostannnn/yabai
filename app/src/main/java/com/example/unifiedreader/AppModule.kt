package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.UnifiedReaderDatabaseFactory
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.network.buildHttpClient
import com.example.unifiedreader.data.grimmory.KtorGrimmoryApi
import com.example.unifiedreader.data.kavita.KtorKavitaApi
import com.example.unifiedreader.data.stump.KtorStumpApi
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.SampleCatalogRepository
import com.example.unifiedreader.feature.library.DataStoreLibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.LibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.comics.ComicsReaderSessionAdapter
import com.example.unifiedreader.feature.reader.comics.comicsReaderFactory
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.epub.epubReaderFactory
import com.example.unifiedreader.feature.reader.pdf.pdfReaderFactory
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
    fun provideAppPreferencesStore(
        @ApplicationContext context: Context,
    ): AppPreferencesStore = DataStoreAppPreferencesStore(context)

    @Provides
    @Singleton
    fun provideLibraryBrowsePreferencesStore(
        @ApplicationContext context: Context,
    ): LibraryBrowsePreferencesStore = DataStoreLibraryBrowsePreferencesStore(context)

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
        readingProgressDao: ReadingProgressDao,
        readerSessionStateDao: ReaderSessionStateDao,
    ): AppStartupInitializer = AppStartupInitializer(
        context = context,
        importedComicArchiveDao = importedComicArchiveDao,
        readingProgressDao = readingProgressDao,
        readerSessionStateDao = readerSessionStateDao,
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
    ): GrimmoryContentRepository = GrimmoryContentRepository(
        context = context.applicationContext,
        grimmoryApiFactory = grimmoryApiFactory,
        grimmoryCredentialStore = grimmoryCredentialStore,
    )

    @Provides
    fun provideGrimmoryContentDataSource(
        repository: GrimmoryContentRepository,
    ): GrimmoryContentDataSource = repository

    @Provides
    @Singleton
    fun provideGrimmoryCoverRepository(
        @ApplicationContext context: Context,
        grimmoryApiFactory: GrimmoryApiFactory,
        grimmoryCredentialStore: GrimmoryCredentialStore,
    ): GrimmoryCoverDataSource = GrimmoryCoverRepository(
        context = context.applicationContext,
        grimmoryApiFactory = grimmoryApiFactory,
        grimmoryCredentialStore = grimmoryCredentialStore,
    )

    @Provides
    @Singleton
    fun provideStumpCoverRepository(
        @ApplicationContext context: Context,
        stumpApiFactory: StumpApiFactory,
        stumpCredentialStore: StumpCredentialStore,
    ): StumpCoverDataSource = StumpCoverRepository(
        context = context.applicationContext,
        stumpApiFactory = stumpApiFactory,
        stumpCredentialStore = stumpCredentialStore,
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
    ): LocalDocumentRepository {
        return LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = importedComicArchiveDao,
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
    ): RoomReaderSessionStore = RoomReaderSessionStore(
        readingProgressDao = readingProgressDao,
        readerSessionStateDao = readerSessionStateDao,
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
        kavitaPageSource: KavitaPageSource,
        stumpPageSource: StumpPageSource,
    ): CompositeComicPageSource = CompositeComicPageSource(
        localComicPageSource = localComicPageSource,
        kavitaPageSource = kavitaPageSource,
        stumpPageSource = stumpPageSource,
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
    ): ComicsReaderSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)

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
        grimmoryContentRepository: GrimmoryContentRepository,
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
                    ?: grimmoryContentRepository.getDocumentUri(item)
            },
        ),
        pdfReaderFactory(
            documentUriProvider = { item ->
                localDocumentRepository.getDocumentUri(item)
                    ?: grimmoryContentRepository.getDocumentUri(item)
            },
        ),
    )
}
