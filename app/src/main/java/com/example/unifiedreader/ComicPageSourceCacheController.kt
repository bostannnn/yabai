package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagesUnavailableDiagnostic
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface ComicPageSourceCacheController {
    fun clearSourcePageCache(
        sourceType: SourceType,
    )

    fun trimReaderMemoryCaches(
        request: ReaderMemoryTrimRequest,
    ) {
        if (request.mode == ReaderMemoryTrimMode.ClearAll) {
            clearReaderMemoryCaches()
        }
    }

    fun clearReaderMemoryCaches() = Unit
}

class CompositeComicPageSource(
    private val localComicPageSource: LocalComicPageSource,
    private val downloadedRemoteComicPageSource: DownloadedRemoteComicPageSource,
    private val kavitaPageSource: KavitaPageSource,
    private val stumpPageSource: StumpPageSource,
    private val cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
) : ComicPageSource, ComicPageSourceCacheController {
    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = pageSourceFor(item)?.getPages(item).orEmpty()

    override fun observePages(
        item: ReadableItem,
    ): Flow<List<ComicPageSpec>> = cacheInvalidationDispatcher.observeInvalidations(item.id)
        .onStart { emit(Unit) }
        .flatMapLatest {
            pageSourceFor(item)?.observePages(item) ?: flowOf(emptyList())
        }

    override fun pagesUnavailableDiagnostic(
        item: ReadableItem,
    ): ComicPagesUnavailableDiagnostic {
        val selectedPageSource = pageSourceFor(item)
            ?: return ComicPagesUnavailableDiagnostic(
                title = "No comic page source selected",
                detail = "itemId=${item.id}, source=${item.sourceType}, format=${item.format}",
            )
        val selectedDiagnostic = selectedPageSource.pagesUnavailableDiagnostic(item)
        val downloadedDiagnostic = when {
            item.sourceType == SourceType.KAVITA || item.sourceType == SourceType.STUMP ->
                downloadedRemoteComicPageSource.pagesUnavailableDiagnostic(item)
            else -> null
        }
        return if (downloadedDiagnostic == null || selectedPageSource === downloadedRemoteComicPageSource) {
            selectedDiagnostic
        } else {
            ComicPagesUnavailableDiagnostic(
                title = selectedDiagnostic.title,
                detail = "${selectedDiagnostic.detail}; selectedPageSource=${selectedPageSource.javaClass.simpleName}; " +
                    "downloadedLocalState=${downloadedDiagnostic.title}: ${downloadedDiagnostic.detail}",
            )
        }
    }

    override fun observePagesUnavailableDiagnostic(
        item: ReadableItem,
    ): Flow<ComicPagesUnavailableDiagnostic> = cacheInvalidationDispatcher.observeInvalidations(item.id)
        .onStart { emit(Unit) }
        .flatMapLatest {
            pageSourceFor(item)?.observePagesUnavailableDiagnostic(item)?.map {
                pagesUnavailableDiagnostic(item)
            } ?: flowOf(pagesUnavailableDiagnostic(item))
        }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = pageSourceFor(item)?.loadPageMetadata(item, page)

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = pageSourceFor(item)?.loadPageImage(item, page, priority)
        ?: ComicPageLoadResult.Failure("Unsupported source.")

    override suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult = pageSourceFor(item)?.loadPagePreview(item, page, options)
        ?: ComicPagePreviewResult.Unavailable

    override suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? = pageSourceFor(item)?.loadPagePreviewMetadata(item, page, options)

    override suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? = pageSourceFor(item)?.loadCachedPagePreviewMetadata(item, page, options)

    override fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ): Flow<Long> = pageSourceFor(item)?.observePagePreviewVersion(item, pages, options) ?: flowOf(0L)

    override fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        pageSourceFor(item)?.prefetchPagePreviews(item, pages, options, priorityPageIndex)
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        pageSourceFor(item)?.prefetchAround(item, settledPageIndex, pageCount)
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        pageSourceFor(item)?.prefetchAround(
            item = item,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = window,
        )
    }

    override fun clearSourcePageCache(
        sourceType: SourceType,
    ) {
        when (sourceType) {
            SourceType.KAVITA -> kavitaPageSource.clear()
            SourceType.STUMP -> stumpPageSource.clear()
            SourceType.LOCAL, SourceType.GRIMMORY -> Unit
        }
    }

    override fun clearReaderMemoryCaches() {
        trimReaderMemoryCaches(ReaderMemoryTrimRequest.ClearAll)
    }

    override fun trimReaderMemoryCaches(
        request: ReaderMemoryTrimRequest,
    ) {
        val retainingSource = request.retention?.item?.let(::pageSourceFor)
        localComicPageSource.trimMemoryCaches(
            request.forSource(localComicPageSource, retainingSource),
        )
        downloadedRemoteComicPageSource.trimMemoryCaches(
            request.forSource(downloadedRemoteComicPageSource, retainingSource),
        )
        kavitaPageSource.trimMemoryCaches(
            request.forSource(kavitaPageSource, retainingSource),
        )
        stumpPageSource.trimMemoryCaches(
            request.forSource(stumpPageSource, retainingSource),
        )
    }

    private fun pageSourceFor(
        item: ReadableItem,
    ): ComicPageSource? = when {
        item.sourceType == SourceType.LOCAL -> localComicPageSource
        downloadedRemoteComicPageSource.hasStoredDocument(item) -> downloadedRemoteComicPageSource
        item.sourceType == SourceType.KAVITA -> kavitaPageSource
        item.sourceType == SourceType.STUMP -> stumpPageSource
        else -> null
    }

    private fun ReaderMemoryTrimRequest.forSource(
        source: ComicPageSource,
        retainingSource: ComicPageSource?,
    ): ReaderMemoryTrimRequest {
        if (retention == null || source === retainingSource) return this
        return copy(retention = null)
    }
}
