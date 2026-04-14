package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec

interface ComicPageSourceCacheController {
    fun clearSourcePageCache(
        sourceType: SourceType,
    )
}

class CompositeComicPageSource(
    private val localComicPageSource: LocalComicPageSource,
    private val downloadedRemoteComicPageSource: DownloadedRemoteComicPageSource,
    private val kavitaPageSource: KavitaPageSource,
    private val stumpPageSource: StumpPageSource,
) : ComicPageSource, ComicPageSourceCacheController {
    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = pageSourceFor(item)?.getPages(item).orEmpty()

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

    private fun pageSourceFor(
        item: ReadableItem,
    ): ComicPageSource? = when {
        item.sourceType == SourceType.LOCAL -> localComicPageSource
        downloadedRemoteComicPageSource.hasStoredDocument(item) -> downloadedRemoteComicPageSource
        item.sourceType == SourceType.KAVITA -> kavitaPageSource
        item.sourceType == SourceType.STUMP -> stumpPageSource
        else -> null
    }
}
