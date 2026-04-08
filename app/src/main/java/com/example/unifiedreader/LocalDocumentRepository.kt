package com.example.unifiedreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class LocalDocumentRepository(
    private val context: Context,
    importedComicArchiveDao: ImportedComicArchiveDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LocalDocumentDataSource, LocalComicDocumentDataSource {
    private val coverWarmupSemaphore = Semaphore(permits = 3)
    private val coverMutationMutex = Mutex()
    private val comicArchiveCodecs: List<ComicArchiveCodec> = listOf(
        ZipComicArchiveCodec,
        RarComicArchiveCodec(context),
    )
    private val importProbe = LocalDocumentImportProbe(context, comicArchiveCodecs)
    private val coverCache = LocalCoverCache(context, comicArchiveCodecs)
    private val store = LocalDocumentStore(
        importedComicArchiveDao = importedComicArchiveDao,
        persistedCoverUriResolver = coverCache::resolvePersistedCoverUri,
    )
    private val comicPageLoader = LocalComicPageLoader(context, comicArchiveCodecs, scope)

    override val isHydratedFlow = store.isHydratedFlow
    override val importedItemsFlow = store.importedItemsFlow

    init {
        scope.launch {
            val persistedRecords = store.hydrate()
            warmImportedCovers(persistedRecords.map(LocalDocumentRecord::id))
        }
    }

    override fun getImportedItems(): List<ReadableItem> = store.getImportedItems()

    override suspend fun import(uri: Uri): ReadableItem? {
        val record = importProbe.probe(uri) ?: return null
        val existingRecord = store.findRecord(record.id)
        val recordWithAddedAt = record.copy(
            addedAtEpochMillis = existingRecord?.addedAtEpochMillis ?: record.addedAtEpochMillis,
        )
        val recordWithCover = coverMutationMutex.withLock {
            val refreshedRecord = coverCache.attach(recordWithAddedAt, forceRefresh = true)
            store.upsert(refreshedRecord)
            refreshedRecord
        }
        if (recordWithCover.coverUri == null) {
            warmImportedCovers(listOf(recordWithCover.id))
        }
        return recordToReadableItem(recordWithCover)
    }

    override suspend fun removeById(itemId: String): Boolean {
        val record = store.remove(itemId) ?: return false
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(record.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        comicPageLoader.clear(itemId)
        coverCache.clear(itemId)
        return true
    }

    override fun getPageCount(item: ReadableItem): Int = store.findRecord(item.id)?.pageCount ?: 0

    override fun getDocumentUri(item: ReadableItem): Uri? = store.findRecord(item.id)?.uri?.let(Uri::parse)

    override fun getPages(item: ReadableItem): List<ComicPageSpec> {
        val record = store.findRecord(item.id) ?: return emptyList()
        return record.pageEntryNames.mapIndexed { index, entryName ->
            ComicPageSpec(
                id = entryName,
                contentDescription = "${item.title} page ${index + 1}",
            )
        }
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult {
        val record = store.findRecord(item.id)
            ?: return ComicPageLoadResult.Failure("Unable to find imported archive.")
        return comicPageLoader.loadPageImage(
            item = item,
            record = record,
            entryName = page.id,
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

    private fun warmImportedCovers(
        itemIds: List<String>,
    ) {
        itemIds.distinct().forEach { itemId ->
            scope.launch {
                coverWarmupSemaphore.withPermit {
                    coverMutationMutex.withLock {
                        val currentRecord = store.findRecord(itemId) ?: return@launch
                        if (currentRecord.coverUri != null) return@launch
                        val updatedRecord = coverCache.attach(currentRecord)
                        if (updatedRecord.coverUri == currentRecord.coverUri) return@launch
                        if (store.findRecord(itemId) != currentRecord) return@launch
                        store.upsert(updatedRecord)
                    }
                }
            }
        }
    }
}
