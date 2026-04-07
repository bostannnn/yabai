package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec

class LocalComicPageSource(
    private val localDocumentRepository: LocalDocumentRepository,
) : ComicPageSource {
    override fun getPages(item: ReadableItem): List<ComicPageSpec> {
        val record = localDocumentRepository.findRecord(item) ?: return emptyList()
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
    ) = localDocumentRepository.loadPageImage(
        item = item,
        entryName = page.id,
    )

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        localDocumentRepository.prefetchAround(
            item = item,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
        )
    }
}
