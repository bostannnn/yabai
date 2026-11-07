package com.example.unifiedreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import kotlinx.coroutines.CoroutineScope
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
    private val readerWorkCoordinator: ReaderWorkCoordinator,
) : LocalDocumentDataSource, LocalDocumentHydrator {
    private val coverWarmupSemaphore = Semaphore(permits = 3)
    private val coverMutationMutex = Mutex()
    private val hydrationMutex = Mutex()
    private val importProbe = LocalDocumentImportProbe(context, comicArchiveCodecs)
    private val store = LocalDocumentStore(
        appStartupTasks = appStartupTasks,
        importedComicArchiveDao = importedComicArchiveDao,
        persistedCoverUriResolver = coverSupport::resolvePersistedCoverUri,
    )

    /** The pages of the imported comics. */
    val comicPages = LocalComicPageSource(
        context = context,
        comicArchiveCodecs = comicArchiveCodecs,
        scope = scope,
        previewStore = previewStore,
        readerWorkCoordinator = readerWorkCoordinator,
        findRecord = { itemId -> store.findRecord(itemId) },
    )

    constructor(
        context: Context,
        importedComicArchiveDao: ImportedComicArchiveDao,
        appStartupTasks: AppStartupTasks = AppStartupTasks {},
        scope: CoroutineScope,
        onItemImported: (String) -> Unit = {},
        previewStore: PersistentComicPagePreviewStore? = null,
        readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
    ) : this(
        context = context,
        importedComicArchiveDao = importedComicArchiveDao,
        appStartupTasks = appStartupTasks,
        scope = scope,
        comicArchiveCodecs = defaultComicArchiveCodecs(context),
        coverSupport = LocalCoverCache(context, defaultComicArchiveCodecs(context)),
        onItemImported = onItemImported,
        previewStore = previewStore,
        readerWorkCoordinator = readerWorkCoordinator,
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
                comicPages.clearItem(record.id)
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
            runSuspendCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(record.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            comicPages.clearItem(itemId)
            coverSupport.clear(itemId)
            true
        }
    }

    override fun getPageCount(item: ReadableItem): Int = store.findRecord(item.id)?.pageCount ?: 0

    override fun getDocumentUri(item: ReadableItem): Uri? = store.findRecord(item.id)?.uri?.let(Uri::parse)

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
            readerWorkCoordinator: ReaderWorkCoordinator = ReaderWorkCoordinator(),
        ): LocalDocumentRepository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = importedComicArchiveDao,
            appStartupTasks = appStartupTasks,
            scope = scope,
            comicArchiveCodecs = comicArchiveCodecs,
            coverSupport = coverSupport,
            onItemImported = onItemImported,
            previewStore = previewStore,
            readerWorkCoordinator = readerWorkCoordinator,
        )
    }
}
