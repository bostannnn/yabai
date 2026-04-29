package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private const val SpreadPreparationMaxMetadataAttempts = 3
private const val SpreadPreparationRetryDelayMillis = 750L

internal data class ComicSpreadPreparationProgress(
    val loadedPageCount: Int,
    val totalPageCount: Int,
)

internal suspend fun prepareComicSpreadPlanState(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
    maxMetadataAttempts: Int = SpreadPreparationMaxMetadataAttempts,
    retryDelayMillis: Long = SpreadPreparationRetryDelayMillis,
    onProgress: (ComicSpreadPreparationProgress) -> Unit = {},
): ComicSpreadPlanState {
    if (pages.isEmpty()) return ComicSpreadPlanState(metadataByPageId = emptyMap())
    require(maxMetadataAttempts > 0) { "maxMetadataAttempts must be > 0" }

    val metadataByPageId = mutableMapOf<String, ComicPageMetadata>()
    val degradedPageIds = mutableSetOf<String>()
    onProgress(
        ComicSpreadPreparationProgress(
            loadedPageCount = 0,
            totalPageCount = pages.size,
        ),
    )

    repeat(maxMetadataAttempts) { attempt ->
        val missingPages = pages.filter { page ->
            page.id !in metadataByPageId && page.id !in degradedPageIds
        }
        if (missingPages.isEmpty()) {
            return ComicSpreadPlanState(metadataByPageId = metadataByPageId.toMap())
        }

        val loadResults = loadMetadataForSpreadPreparation(
            item = item,
            pages = missingPages,
            pageSource = pageSource,
        )
        loadResults.forEach { (pageId, metadata) ->
            if (metadata != null) {
                metadataByPageId[pageId] = metadata
            } else if (attempt == maxMetadataAttempts - 1) {
                degradedPageIds += pageId
            }
        }
        onProgress(
            ComicSpreadPreparationProgress(
                loadedPageCount = metadataByPageId.size + degradedPageIds.size,
                totalPageCount = pages.size,
            ),
        )

        if (metadataByPageId.size + degradedPageIds.size < pages.size &&
            attempt < maxMetadataAttempts - 1
        ) {
            delay(retryDelayMillis)
        }
    }

    return ComicSpreadPlanState(metadataByPageId = metadataByPageId.toMap())
}

private suspend fun loadMetadataForSpreadPreparation(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
): List<Pair<String, ComicPageMetadata?>> = coroutineScope {
    pages.map { page ->
        async {
            val metadata = try {
                pageSource.loadPageMetadata(item, page)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            page.id to metadata
        }
    }.awaitAll()
}
