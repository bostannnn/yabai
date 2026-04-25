package com.example.unifiedreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class LocalDocumentRepository private constructor(
    private val context: Context,
    importedComicArchiveDao: ImportedComicArchiveDao,
    appStartupTasks: AppStartupTasks,
    private val scope: CoroutineScope,
    private val comicArchiveCodecs: List<ComicArchiveCodec>,
    private val coverSupport: LocalDocumentCoverSupport,
    private val onItemImported: (String) -> Unit,
    private val previewStore: PersistentComicPagePreviewStore?,
) : LocalDocumentDataSource, LocalComicDocumentDataSource, LocalDocumentHydrator {
    private val coverWarmupSemaphore = Semaphore(permits = 3)
    private val coverMutationMutex = Mutex()
    private val hydrationMutex = Mutex()
    private val importProbe = LocalDocumentImportProbe(context, comicArchiveCodecs)
    private val store = LocalDocumentStore(
        appStartupTasks = appStartupTasks,
        importedComicArchiveDao = importedComicArchiveDao,
        persistedCoverUriResolver = coverSupport::resolvePersistedCoverUri,
    )
    private val comicPageLoader = LocalComicPageLoader(
        context = context,
        comicArchiveCodecs = comicArchiveCodecs,
        scope = scope,
        previewStore = previewStore,
    )

    constructor(
        context: Context,
        importedComicArchiveDao: ImportedComicArchiveDao,
        appStartupTasks: AppStartupTasks = AppStartupTasks {},
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        onItemImported: (String) -> Unit = {},
        previewStore: PersistentComicPagePreviewStore? = null,
    ) : this(
        context = context,
        importedComicArchiveDao = importedComicArchiveDao,
        appStartupTasks = appStartupTasks,
        scope = scope,
        comicArchiveCodecs = defaultComicArchiveCodecs(context),
        coverSupport = LocalCoverCache(context, defaultComicArchiveCodecs(context)),
        onItemImported = onItemImported,
        previewStore = previewStore,
    )

    override val isHydratedFlow = store.isHydratedFlow
    override val importedItemsFlow = store.importedItemsFlow

    override fun getImportedItems(): List<ReadableItem> = store.getImportedItems()

    override suspend fun hydrate() {
        if (isHydratedFlow.value) return
        hydrationMutex.withLock {
            if (isHydratedFlow.value) return
            val persistedRecords = store.hydrate()
            warmImportedCovers(persistedRecords.map(LocalDocumentRecord::id))
        }
    }

    override suspend fun import(uri: Uri): ReadableItem? {
        val record = importProbe.probe(uri) ?: return null
        val existingRecord = store.findRecord(record.id)
        val recordWithAddedAt = record.copy(
            addedAtEpochMillis = existingRecord?.addedAtEpochMillis ?: record.addedAtEpochMillis,
        )
        val recordWithCover = coverMutationMutex.withLock {
            if (existingRecord != null) {
                comicPageLoader.clear(record.id)
                previewStore?.clear()
            }
            val refreshedRecord = coverSupport.attach(recordWithAddedAt, forceRefresh = true)
            store.upsert(refreshedRecord)
            refreshedRecord
        }
        onItemImported(recordWithCover.id)
        if (recordWithCover.coverUri == null) {
            warmImportedCovers(listOf(recordWithCover.id))
        }
        return recordToReadableItem(recordWithCover)
    }

    override suspend fun removeById(itemId: String): Boolean {
        return coverMutationMutex.withLock {
            val record = store.remove(itemId) ?: return@withLock false
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(record.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            comicPageLoader.clear(itemId)
            coverSupport.clear(itemId)
            true
        }
    }

    override fun getPageCount(item: ReadableItem): Int = store.findRecord(item.id)?.pageCount ?: 0

    override fun getDocumentUri(item: ReadableItem): Uri? = store.findRecord(item.id)?.uri?.let(Uri::parse)

    override fun getPages(item: ReadableItem): List<ComicPageSpec> {
        val record = store.findRecord(item.id) ?: return emptyList()
        return record.pageEntryNames.mapIndexed { index, entryName ->
            ComicPageSpec(
                id = entryName,
                pageIndex = index,
                contentDescription = "${item.title} page ${index + 1}",
            )
        }
    }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? {
        val record = store.findRecord(item.id) ?: return null
        return comicPageLoader.loadPageMetadata(
            item = item,
            record = record,
            entryName = page.id,
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        val record = store.findRecord(item.id)
            ?: return ComicPageLoadResult.Failure("Unable to find imported archive.")
        return comicPageLoader.loadPageImage(
            item = item,
            record = record,
            entryName = page.id,
            priority = priority,
        )
    }

    override suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult {
        val record = store.findRecord(item.id)
            ?: return ComicPagePreviewResult.Unavailable
        return comicPageLoader.loadPagePreview(
            item = item,
            record = record,
            entryName = page.id,
            options = options,
        )
    }

    override suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? {
        val record = store.findRecord(item.id) ?: return null
        return comicPageLoader.loadPageMetadata(
            item = item,
            record = record,
            entryName = page.id,
        )
    }

    override suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? {
        val record = store.findRecord(item.id) ?: return null
        return comicPageLoader.cachedPageMetadata(
            item = item,
            record = record,
            entryName = page.id,
        )
    }

    override fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        val record = store.findRecord(item.id) ?: return
        if (pages.isEmpty()) return
        comicPageLoader.prefetchPagePreviews(
            item = item,
            record = record,
            entryNames = record.pageEntryNames,
            options = options,
            priorityPageIndex = priorityPageIndex,
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        val record = store.findRecord(item.id) ?: return
        comicPageLoader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        val record = store.findRecord(item.id) ?: return
        comicPageLoader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = window,
        )
    }

    private fun warmImportedCovers(
        itemIds: List<String>,
    ) {
        itemIds.distinct().forEach { itemId ->
            scope.launch {
                coverWarmupSemaphore.withPermit {
                    coverMutationMutex.withLock {
                        val currentRecord = store.findRecord(itemId) ?: return@launch
                        if (currentRecord.coverUri != null) return@launch
                        val updatedRecord = coverSupport.attach(currentRecord)
                        if (updatedRecord.coverUri == currentRecord.coverUri) return@launch
                        if (store.findRecord(itemId) != currentRecord) return@launch
                        store.upsert(updatedRecord)
                    }
                }
            }
        }
    }

    internal companion object {
        fun createForTesting(
            context: Context,
            importedComicArchiveDao: ImportedComicArchiveDao,
            appStartupTasks: AppStartupTasks,
            scope: CoroutineScope,
            comicArchiveCodecs: List<ComicArchiveCodec>,
            coverSupport: LocalDocumentCoverSupport,
            onItemImported: (String) -> Unit = {},
            previewStore: PersistentComicPagePreviewStore? = null,
        ): LocalDocumentRepository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = importedComicArchiveDao,
            appStartupTasks = appStartupTasks,
            scope = scope,
            comicArchiveCodecs = comicArchiveCodecs,
            coverSupport = coverSupport,
            onItemImported = onItemImported,
            previewStore = previewStore,
        )
    }
}

private fun defaultComicArchiveCodecs(
    context: Context,
): List<ComicArchiveCodec> = listOf(
    ZipComicArchiveCodec,
    RarComicArchiveCodec(context),
)
