package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource

class LocalComicPageSource(
    private val localComicDocumentDataSource: LocalComicDocumentDataSource,
) : ComicPageSource {
    override fun getPages(item: ReadableItem) = localComicDocumentDataSource.getPages(item)

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: com.example.unifiedreader.feature.reader.comics.ComicPageSpec,
    ) = localComicDocumentDataSource.loadPageMetadata(item = item, page = page)

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: com.example.unifiedreader.feature.reader.comics.ComicPageSpec,
        priority: ComicPageLoadPriority,
    ) = localComicDocumentDataSource.loadPageImage(item = item, page = page, priority = priority)

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        localComicDocumentDataSource.prefetchAround(
            item = item,
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
        localComicDocumentDataSource.prefetchAround(
            item = item,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = window,
        )
    }
}
