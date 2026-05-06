package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.CancellationException

internal data class ComicSpreadPlanningUpdate(
    val metadataByPageId: Map<String, ComicPageMetadata>,
    val failureCountsByPageId: Map<String, Int>,
    val degradedPageIds: Set<String>,
    val shouldRetry: Boolean,
)

internal fun requiredMetadataIndexes(
    currentPage: Int,
    pageCount: Int,
    useDoublePageSpreads: Boolean,
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val safeCurrentPage = currentPage.coerceIn(0, pageCount - 1)
    return if (useDoublePageSpreads) {
        windowedPageIndexes(
            pageCount = pageCount,
            startInclusive = safeCurrentPage - 2,
            endInclusive = safeCurrentPage + 1,
        )
    } else {
        listOf(safeCurrentPage)
    }
}

internal fun backgroundMetadataIndexes(
    currentPage: Int,
    pageCount: Int,
    useDoublePageSpreads: Boolean,
    radius: Int = 2,
    lookahead: Int = 4,
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val safeCurrentPage = currentPage.coerceIn(0, pageCount - 1)
    return if (useDoublePageSpreads) {
        windowedPageIndexes(
            pageCount = pageCount,
            startInclusive = safeCurrentPage - radius,
            endInclusive = safeCurrentPage + lookahead,
        )
    } else {
        buildList {
            add(safeCurrentPage)
            for (offset in 1..radius) {
                add((safeCurrentPage + offset).coerceIn(0, pageCount - 1))
                add((safeCurrentPage - offset).coerceIn(0, pageCount - 1))
            }
        }.distinct()
    }
}

internal fun applySpreadPlanningResults(
    pages: List<ComicPageSpec>,
    requiredIndexes: List<Int>,
    loadResults: List<Pair<String, ComicPageMetadata?>>,
    pageMetadataById: Map<String, ComicPageMetadata>,
    spreadPlanningFailuresById: Map<String, Int>,
    degradedSpreadPlanningPageIds: Set<String>,
    maxMetadataRetries: Int,
): ComicSpreadPlanningUpdate {
    val requiredPageIds = requiredIndexes
        .map { index -> pages[index].id }
        .toSet()
    val nextFailures = spreadPlanningFailuresById.toMutableMap()
    val nextMetadata = pageMetadataById.toMutableMap()
    val nextDegradedPageIds = degradedSpreadPlanningPageIds.toMutableSet()

    loadResults.forEach { (pageId, metadata) ->
        when {
            metadata != null -> {
                if (pageId !in nextDegradedPageIds) {
                    nextMetadata[pageId] = metadata
                    nextFailures.remove(pageId)
                }
            }

            pageId in requiredPageIds -> {
                val failureCount = (nextFailures[pageId] ?: 0) + 1
                if (failureCount >= maxMetadataRetries) {
                    nextFailures.remove(pageId)
                    nextDegradedPageIds.add(pageId)
                } else {
                    nextFailures[pageId] = failureCount
                }
            }
        }
    }

    val shouldRetry = requiredIndexes.any { index ->
        val pageId = pages[index].id
        pageId !in nextMetadata && pageId !in nextDegradedPageIds
    }

    return ComicSpreadPlanningUpdate(
        metadataByPageId = nextMetadata,
        failureCountsByPageId = nextFailures,
        degradedPageIds = nextDegradedPageIds,
        shouldRetry = shouldRetry,
    )
}

internal suspend fun loadSpreadPlanningMetadata(
    item: ReadableItem,
    page: ComicPageSpec,
    pageSource: ComicPageSource,
): ComicPageMetadata? {
    try {
        pageSource.loadPageMetadata(item, page)?.let { return it }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        // Metadata is only used to refine spread layout; page loading can still provide dimensions.
    }
    val imageResult = try {
        pageSource.loadPageImage(
            item = item,
            page = page,
            priority = ComicPageLoadPriority.Prefetch,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        return null
    }
    return when (imageResult) {
        is ComicPageLoadResult.Success -> ComicPageMetadata(
            width = imageResult.image.width,
            height = imageResult.image.height,
        )

        is ComicPageLoadResult.Failure -> null
    }
}

private fun windowedPageIndexes(
    pageCount: Int,
    startInclusive: Int,
    endInclusive: Int,
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val safeStart = startInclusive.coerceIn(0, pageCount - 1)
    val safeEnd = endInclusive.coerceIn(0, pageCount - 1)
    if (safeStart > safeEnd) return emptyList()
    return (safeStart..safeEnd).toList()
}
