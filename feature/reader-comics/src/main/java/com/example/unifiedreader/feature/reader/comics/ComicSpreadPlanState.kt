package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

private const val SpreadPlanningMaxMetadataRetries = 3
private const val SpreadPlanningRetryDelayMillis = 750L

internal data class ComicSpreadPlanState(
    val metadataByPageId: Map<String, ComicPageMetadata>,
)

@Composable
internal fun rememberComicSpreadPlanState(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
    currentPage: Int,
    readingDirection: ComicReadingDirection,
    spreadMode: ComicSpreadMode,
    useDoublePageSpreads: Boolean,
): ComicSpreadPlanState {
    var pageMetadataById by remember(item.id, pages) { mutableStateOf<Map<String, ComicPageMetadata>>(emptyMap()) }
    var spreadPlanningFailuresById by remember(item.id, pages) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var degradedSpreadPlanningPageIds by remember(item.id, pages) { mutableStateOf<Set<String>>(emptySet()) }
    var metadataRetryNonce by remember(item.id, pages) { mutableIntStateOf(0) }

    LaunchedEffect(
        item.id,
        currentPage,
        readingDirection,
        spreadMode,
        useDoublePageSpreads,
        pages,
        pageSource,
        metadataRetryNonce,
    ) {
        val requiredIndexes = requiredMetadataIndexes(
            currentPage = currentPage,
            pageCount = pages.size,
            useDoublePageSpreads = useDoublePageSpreads,
        )
        val targetIndexes = backgroundMetadataIndexes(
            currentPage = currentPage,
            pageCount = pages.size,
            useDoublePageSpreads = useDoublePageSpreads,
        ).filter { index ->
            val pageId = pages[index].id
            pageId !in pageMetadataById && pageId !in degradedSpreadPlanningPageIds
        }
        if (targetIndexes.isEmpty()) return@LaunchedEffect
        val loadResults = targetIndexes.map { index ->
            async {
                val page = pages[index]
                page.id to loadSpreadPlanningMetadata(
                    item = item,
                    page = page,
                    pageSource = pageSource,
                )
            }
        }.awaitAll()
        val planningUpdate = applySpreadPlanningResults(
            pages = pages,
            requiredIndexes = requiredIndexes,
            loadResults = loadResults,
            pageMetadataById = pageMetadataById,
            spreadPlanningFailuresById = spreadPlanningFailuresById,
            degradedSpreadPlanningPageIds = degradedSpreadPlanningPageIds,
            maxMetadataRetries = SpreadPlanningMaxMetadataRetries,
        )
        if (planningUpdate.metadataByPageId != pageMetadataById) {
            pageMetadataById = planningUpdate.metadataByPageId
        }
        if (planningUpdate.failureCountsByPageId != spreadPlanningFailuresById) {
            spreadPlanningFailuresById = planningUpdate.failureCountsByPageId
        }
        if (planningUpdate.degradedPageIds != degradedSpreadPlanningPageIds) {
            degradedSpreadPlanningPageIds = planningUpdate.degradedPageIds
        }
        if (planningUpdate.shouldRetry) {
            delay(SpreadPlanningRetryDelayMillis)
            metadataRetryNonce += 1
        }
    }

    return ComicSpreadPlanState(
        metadataByPageId = pageMetadataById,
    )
}
