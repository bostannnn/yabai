package com.example.unifiedreader

import android.content.Context
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
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The comic pages of an archive on this device: an imported one or a downloaded one. Each subclass says how to find
 * the archive's record for an item and which archive entry a page is; reading, caching and prefetching the pages is
 * the same for both.
 */
abstract class ArchiveComicPageSource internal constructor(
    private val context: Context,
    internal val comicArchiveCodecs: List<ComicArchiveCodec>,
    scope: CoroutineScope,
    previewStore: PersistentComicPagePreviewStore?,
    readerWorkCoordinator: ReaderWorkCoordinator,
    /** Names the archive in failures: "imported" or "downloaded". */
    private val archiveLabel: String,
) : ComicPageSource {
    internal val comicPageLoader = LocalComicPageLoader(
        context = context,
        comicArchiveCodecs = comicArchiveCodecs,
        scope = scope,
        previewStore = previewStore,
        readerWorkCoordinator = readerWorkCoordinator,
    )

    /** The record for [item], reading the archive's entries first if they are not known yet. */
    internal abstract suspend fun recordFor(item: ReadableItem): LocalDocumentRecord?

    /** The record for [item] from what is already known, without reading the archive; null if nothing is known. */
    internal abstract fun knownRecordFor(item: ReadableItem): LocalDocumentRecord?

    /** The archive entry that holds [page]. */
    internal abstract fun entryNameFor(record: LocalDocumentRecord, page: ComicPageSpec): String?

    /** The archive's record and the entry for [page], reading the archive first when needed. */
    private suspend fun entryFor(item: ReadableItem, page: ComicPageSpec): ArchiveEntry? =
        recordFor(item)?.entryFor(page)

    /** The same from what is already known, without reading the archive. */
    private fun knownEntryFor(item: ReadableItem, page: ComicPageSpec): ArchiveEntry? =
        knownRecordFor(item)?.entryFor(page)

    private fun LocalDocumentRecord.entryFor(page: ComicPageSpec): ArchiveEntry? =
        entryNameFor(this, page)?.let { entryName -> ArchiveEntry(record = this, name = entryName) }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = entryFor(item, page)?.let { entry ->
        comicPageLoader.loadPageMetadata(item = item, record = entry.record, entryName = entry.name)
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        val record = recordFor(item)
        val entry = record?.entryFor(page)
        return when {
            record == null -> ComicPageLoadResult.Failure("Unable to find $archiveLabel archive.")
            entry == null -> ComicPageLoadResult.Failure("Unable to find $archiveLabel page.")
            else -> comicPageLoader.loadPageImage(item = item, record = record, entryName = entry.name, priority = priority)
        }
    }

    override suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult = entryFor(item, page)?.let { entry ->
        comicPageLoader.loadPagePreview(item = item, record = entry.record, entryName = entry.name, options = options)
    } ?: ComicPagePreviewResult.Unavailable

    /** A preview has the page's own proportions, so its metadata is the page's. */
    override suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? = loadPageMetadata(item, page)

    override suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? = knownEntryFor(item, page)?.let { entry ->
        comicPageLoader.cachedPageMetadata(item = item, record = entry.record, entryName = entry.name)
    }

    override fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ): Flow<Long> {
        val record = knownRecordFor(item) ?: return flowOf(0L)
        val entryNames = pages.mapNotNull { page -> entryNameFor(record, page) }
        return comicPageLoader.observePagePreviewVersion(
            comicPageLoader.pagePreviewCacheKeys(item = item, record = record, entryNames = entryNames, options = options),
        )
    }

    override fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        val record = knownRecordFor(item).takeIf { pages.isNotEmpty() } ?: return
        val prefetchPages = pages.distinctBy(ComicPageSpec::pageIndex)
        val entryNames = prefetchPages.mapNotNull { page -> entryNameFor(record, page) }.ifEmpty { return }
        val priorityEntryIndex = prefetchPages.indexOfFirst { page -> page.pageIndex == priorityPageIndex }
            .takeIf { index -> index >= 0 }
        comicPageLoader.prefetchPagePreviews(
            item = item,
            record = record,
            entryNames = entryNames,
            options = options,
            priorityPageIndex = priorityEntryIndex,
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        val record = knownRecordFor(item) ?: return
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
        val record = knownRecordFor(item) ?: return
        comicPageLoader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = window,
        )
    }

    /** Forgets what is cached for one item, after it was imported again or removed. */
    internal fun clearItem(itemId: String) {
        comicPageLoader.clear(itemId)
    }

    fun clearMemoryCaches() {
        comicPageLoader.clearAll()
        ZipComicArchiveCodec.clearLocalArchiveCache(context)
        RarComicArchiveCodec.clearLocalArchiveCache(context)
    }

    fun trimMemoryCaches(
        request: ReaderMemoryTrimRequest,
    ) {
        comicPageLoader.trimMemoryCaches(request)
        if (request.mode == ReaderMemoryTrimMode.ClearAll) {
            ZipComicArchiveCodec.clearLocalArchiveCache(context)
        }
        RarComicArchiveCodec.clearLocalArchiveCache(context)
    }
}

internal data class ArchiveEntry(
    val record: LocalDocumentRecord,
    val name: String,
)

internal fun defaultComicArchiveCodecs(
    context: Context,
): List<ComicArchiveCodec> = listOf(
    ZipComicArchiveCodec,
    RarComicArchiveCodec(context),
)
