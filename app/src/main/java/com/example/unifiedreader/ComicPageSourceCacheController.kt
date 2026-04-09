package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec

interface ComicPageSourceCacheController {
    fun clearSourcePageCache(
        sourceType: SourceType,
    )
}

class CompositeComicPageSource(
    private val localComicPageSource: LocalComicPageSource,
    private val kavitaPageSource: KavitaPageSource,
    private val stumpPageSource: StumpPageSource,
) : ComicPageSource, ComicPageSourceCacheController {
    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.getPages(item)
        SourceType.KAVITA -> kavitaPageSource.getPages(item)
        SourceType.STUMP -> stumpPageSource.getPages(item)
        SourceType.GRIMMORY -> emptyList()
    }

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.loadPageMetadata(item, page)
        SourceType.KAVITA -> kavitaPageSource.loadPageMetadata(item, page)
        SourceType.STUMP -> stumpPageSource.loadPageMetadata(item, page)
        SourceType.GRIMMORY -> null
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.loadPageImage(item, page, priority)
        SourceType.KAVITA -> kavitaPageSource.loadPageImage(item, page, priority)
        SourceType.STUMP -> stumpPageSource.loadPageImage(item, page, priority)
        SourceType.GRIMMORY -> ComicPageLoadResult.Failure("Unsupported source.")
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        when (item.sourceType) {
            SourceType.LOCAL -> localComicPageSource.prefetchAround(item, settledPageIndex, pageCount)
            SourceType.KAVITA -> kavitaPageSource.prefetchAround(item, settledPageIndex, pageCount)
            SourceType.STUMP -> stumpPageSource.prefetchAround(item, settledPageIndex, pageCount)
            SourceType.GRIMMORY -> Unit
        }
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
}
