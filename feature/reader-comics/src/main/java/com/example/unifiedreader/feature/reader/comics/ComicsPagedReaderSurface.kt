package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SpreadPlanningMaxMetadataRetries = 3
private const val SpreadPlanningRetryDelayMillis = 750L

@Composable
internal fun ComicsPagedReaderSurface(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
    readerState: ComicsReaderState,
    overlayVisible: Boolean,
    onToggleOverlay: () -> Unit,
) {
    var pageMetadataById by remember(item.id) { mutableStateOf<Map<String, ComicPageMetadata>>(emptyMap()) }
    var spreadPlanningFailuresById by remember(item.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var degradedSpreadPlanningPageIds by remember(item.id) { mutableStateOf<Set<String>>(emptySet()) }
    var metadataRetryNonce by remember(item.id) { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val useDoublePageSpreads = shouldUseDoublePageSpreads(
            spreadMode = readerState.spreadMode,
            maxWidthDp = maxWidth.value,
            maxHeightDp = maxHeight.value,
            pageCount = pages.size,
        )
        LaunchedEffect(
            item.id,
            readerState.resumeAnchorPage,
            readerState.readingDirection,
            readerState.spreadMode,
            useDoublePageSpreads,
            pages,
            metadataRetryNonce,
        ) {
            val requiredIndexes = requiredMetadataIndexes(
                currentPage = readerState.resumeAnchorPage,
                pageCount = pages.size,
                useDoublePageSpreads = useDoublePageSpreads,
            )
            val targetIndexes = backgroundMetadataIndexes(
                currentPage = readerState.resumeAnchorPage,
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
        val spreads = remember(
            pages,
            readerState.readingDirection,
            useDoublePageSpreads,
            pageMetadataById,
        ) {
            buildComicPageSpreads(
                pages = pages,
                readingDirection = readerState.readingDirection,
                useDoublePageSpreads = useDoublePageSpreads,
                pageMetadataById = pageMetadataById,
            )
        }
        val pagerState = rememberPagerState(
            initialPage = spreads.indexOfPage(readerState.resumeAnchorPage, readerState.resumeSegment),
            pageCount = { spreads.size },
        )
        val currentSpread = spreads.getOrNull(pagerState.currentPage) ?: spreads.first()

        LaunchedEffect(spreads, readerState.resumeAnchorPage, readerState.resumeSegment) {
            val targetSpreadIndex = spreads.indexOfPage(readerState.resumeAnchorPage, readerState.resumeSegment)
            if (pagerState.currentPage != targetSpreadIndex &&
                !currentSpread.matchesPosition(readerState.resumeAnchorPage, readerState.resumeSegment)
            ) {
                pagerState.scrollToPage(targetSpreadIndex)
            }
        }

        LaunchedEffect(pagerState.settledPage, spreads, item.id) {
            val settledSpread = spreads.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            val settledPageIndex = when {
                readerState.currentPage in settledSpread.pageIndexes -> readerState.currentPage
                else -> settledSpread.resumePageIndex
            }
            readerState.onPageSettled(
                pageIndex = settledPageIndex,
                resumeAnchorPage = settledSpread.resumePageIndex,
                segment = settledSpread.resumeSegment,
            )
            pageSource.prefetchAround(
                item = item,
                settledPageIndex = settledPageIndex,
                pageCount = pages.size,
            )
        }

        ComicSpreadPager(
            item = item,
            spreads = spreads,
            currentPage = readerState.currentPage,
            readingDirection = readerState.readingDirection,
            pageFitMode = readerState.pageFitMode,
            isZoomed = readerState.isCurrentPageZoomed,
            overlayVisible = overlayVisible,
            pageSource = pageSource,
            pagerState = pagerState,
            onZoomStateChanged = readerState::onCurrentPageZoomChanged,
            onInteraction = {},
            onToggleOverlay = onToggleOverlay,
            onNavigatePrevious = { spreadIndex ->
                if (spreadIndex > 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(spreadIndex - 1)
                    }
                }
            },
            onNavigateNext = { spreadIndex ->
                if (spreadIndex < spreads.lastIndex) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(spreadIndex + 1)
                    }
                }
            },
        )
    }
}
