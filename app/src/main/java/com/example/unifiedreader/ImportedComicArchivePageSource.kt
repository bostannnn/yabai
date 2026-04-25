package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec

class LocalComicPageSource(
    private val localComicDocumentDataSource: LocalComicDocumentDataSource,
) : ComicPageSource {
    override fun getPages(item: ReadableItem) = localComicDocumentDataSource.getPages(item)

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ) = localComicDocumentDataSource.loadPageMetadata(item = item, page = page)

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ) = localComicDocumentDataSource.loadPageImage(item = item, page = page, priority = priority)

    override suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult = localComicDocumentDataSource.loadPagePreview(
        item = item,
        page = page,
        options = options,
    )

    override suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ) = localComicDocumentDataSource.loadPagePreviewMetadata(
        item = item,
        page = page,
        options = options,
    )

    override suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ) = localComicDocumentDataSource.loadCachedPagePreviewMetadata(
        item = item,
        page = page,
        options = options,
    )

    override fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ) = localComicDocumentDataSource.observePagePreviewVersion(item, pages, options)

    override fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        localComicDocumentDataSource.prefetchPagePreviews(
            item = item,
            pages = pages,
            options = options,
            priorityPageIndex = priorityPageIndex,
        )
    }

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
