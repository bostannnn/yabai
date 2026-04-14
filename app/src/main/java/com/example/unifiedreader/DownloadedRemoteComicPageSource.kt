package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

class DownloadedRemoteComicPageSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val archiveEntryCache: DownloadedRemoteComicArchiveEntryCache,
    cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
) : ComicPageSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val comicArchiveCodecs: List<ComicArchiveCodec> = listOf(
        ZipComicArchiveCodec,
        RarComicArchiveCodec(context),
    )
    private val comicPageLoader = LocalComicPageLoader(context, comicArchiveCodecs, scope)
    private val entryCacheCoordinator = DownloadedRemoteComicEntryCacheCoordinator(archiveEntryCache)

    init {
        cacheInvalidationDispatcher.register(::invalidateItemCaches)
    }

    fun hasStoredDocument(
        item: ReadableItem,
    ): Boolean = storedDocumentUriFor(item) != null && item.format.supportedComicDownloadFormat()

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (!hasStoredDocument(item)) return emptyList()
        val cachedEntryNames = archiveEntryCache.get(item.id).orEmpty()
        return if (cachedEntryNames.isNotEmpty()) {
            cachedEntryNames.toComicPageSpecs(item)
        } else {
            List(item.pageCount?.coerceAtLeast(0) ?: 0) { index ->
                ComicPageSpec(
                    id = index.toString(),
                    pageIndex = index,
                    contentDescription = "${item.title} page ${index + 1}",
                )
            }
        }
    }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? {
        val record = localRecordFor(item) ?: return null
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex) ?: return null
        return comicPageLoader.loadPageMetadata(
            item = item,
            record = record,
            entryName = entryName,
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        val record = localRecordFor(item)
            ?: return ComicPageLoadResult.Failure("Unable to find downloaded archive.")
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex)
            ?: return ComicPageLoadResult.Failure("Unable to find downloaded page.")
        return comicPageLoader.loadPageImage(
            item = item,
            record = record,
            entryName = entryName,
            priority = priority,
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        if (!hasStoredDocument(item)) return
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames) ?: return
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
        if (!hasStoredDocument(item)) return
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames) ?: return
        comicPageLoader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = window,
        )
    }

    private suspend fun localRecordFor(
        item: ReadableItem,
    ): LocalDocumentRecord? {
        val uri = storedDocumentUriFor(item) ?: return null
        val codec = comicArchiveCodecs.firstOrNull { codec -> codec.format == item.format } ?: return null
        val entryNames = entryCacheCoordinator.entryNamesFor(item.id) {
            codec.listImageEntries(
                context = context,
                uri = uri,
            )
        }
        return localRecordForCachedEntries(item = item, entryNames = entryNames)
    }

    private fun localRecordForCachedEntries(
        item: ReadableItem,
        entryNames: List<String>,
    ): LocalDocumentRecord? {
        val uri = storedDocumentUriFor(item) ?: return null
        if (entryNames.isEmpty()) return null
        return LocalDocumentRecord(
            id = item.id,
            uri = uri.toString(),
            displayName = item.title,
            title = item.title,
            format = item.format,
            pageCount = entryNames.size,
            pageEntryNames = entryNames,
            coverUri = item.coverUrl,
            author = item.bookMetadata?.author,
            series = item.comicMetadata?.series,
            issueTitle = item.comicMetadata?.issueTitle,
            volume = item.comicMetadata?.volume,
            issueNumber = item.comicMetadata?.issueNumber,
            addedAtEpochMillis = item.addedAtEpochMillis ?: System.currentTimeMillis(),
        )
    }

    private fun storedDocumentUriFor(
        item: ReadableItem,
    ): Uri? = remoteDownloadDataSources.downloadDataSourceFor(item)
        ?.storedDocumentUri(item)

    private fun invalidateItemCaches(
        itemId: String,
    ) {
        entryCacheCoordinator.invalidate(itemId)
        comicPageLoader.clear(itemId)
    }
}

private fun List<String>.toComicPageSpecs(
    item: ReadableItem,
): List<ComicPageSpec> = mapIndexed { index, entryName ->
    ComicPageSpec(
        id = entryName,
        pageIndex = index,
        contentDescription = "${item.title} page ${index + 1}",
    )
}

private fun FormatType.supportedComicDownloadFormat(): Boolean = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> true
    FormatType.PDF,
    FormatType.EPUB -> false
}
