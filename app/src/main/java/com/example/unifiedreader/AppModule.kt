package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.UnifiedReaderDatabaseFactory
import com.example.unifiedreader.core.network.buildHttpClient
import com.example.unifiedreader.data.grimmory.KtorGrimmoryApi
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.repository.SampleCatalogRepository
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
    fun provideGrimmoryCredentialStore(
        @ApplicationContext context: Context,
    ): GrimmoryCredentialStore = SharedPreferencesGrimmoryCredentialStore(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): UnifiedReaderDatabase = UnifiedReaderDatabaseFactory.create(context)

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
    fun provideGrimmoryApiFactory(
        httpClient: HttpClient,
    ): GrimmoryApiFactory = GrimmoryApiFactory { baseUrl, serverId ->
        KtorGrimmoryApi(
            httpClient = httpClient,
            baseUrl = baseUrl,
            serverId = serverId,
        )
    }

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
        database: UnifiedReaderDatabase,
    ): LocalDocumentRepository {
        LocalDocumentBootstrapper(
            context = context,
            importedComicArchiveDao = database.importedComicArchiveDao(),
        ).bootstrapIfNeeded()
        return LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = database.importedComicArchiveDao(),
        )
    }

    @Provides
    fun provideLocalDocumentDataSource(
        repository: LocalDocumentRepository,
    ): LocalDocumentDataSource = repository

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
        @ApplicationContext context: Context,
        database: UnifiedReaderDatabase,
    ): RoomReaderSessionStore {
        ReaderStateBootstrapper(
            context = context,
            readingProgressDao = database.readingProgressDao(),
            readerSessionStateDao = database.readerSessionStateDao(),
        ).bootstrapIfNeeded()
        return RoomReaderSessionStore(
            readingProgressDao = database.readingProgressDao(),
            readerSessionStateDao = database.readerSessionStateDao(),
        )
    }

    @Provides
    fun provideReaderSessionStoreContract(
        store: RoomReaderSessionStore,
    ): ReaderSessionStore = store

    @Provides
    @Singleton
    fun provideComicPageSource(
        repository: LocalDocumentRepository,
    ): ComicPageSource = LocalComicPageSource(repository)

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
