package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.launch

@Composable
internal fun ComicsPagedReaderSurface(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
    readerState: ComicsReaderState,
    spreadPlanState: ComicSpreadPlanState,
    loadedVisiblePageIndexes: Set<Int>,
    onVisiblePagesLoaded: (Collection<Int>) -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePastEnd: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var lastPrefetchedPageIndex by remember(item.id) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val useDoublePageSpreads = shouldUseDoublePageSpreads(
            spreadMode = readerState.spreadMode,
            maxWidthDp = maxWidth.value,
            maxHeightDp = maxHeight.value,
            pageCount = pages.size,
        )
        val spreads = remember(
            pages,
            readerState.readingDirection,
            useDoublePageSpreads,
            spreadPlanState.metadataByPageId,
        ) {
            buildComicPageSpreads(
                pages = pages,
                readingDirection = readerState.readingDirection,
                useDoublePageSpreads = useDoublePageSpreads,
                pageMetadataById = spreadPlanState.metadataByPageId,
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
            val settledPosition = settledSpread.settledReadingPosition(
                currentPage = readerState.currentPage,
                pageCount = pages.size,
            )
            readerState.onPageSettled(
                pageIndex = settledPosition.pageIndex,
                resumeAnchorPage = settledPosition.resumeAnchorPage,
                segment = settledPosition.segment,
            )
        }

        LaunchedEffect(pagerState.settledPage, spreads, item.id, loadedVisiblePageIndexes) {
            val settledSpread = spreads.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            val settledPosition = settledSpread.settledReadingPosition(
                currentPage = readerState.currentPage,
                pageCount = pages.size,
            )
            if (settledPosition.pageIndex !in loadedVisiblePageIndexes ||
                lastPrefetchedPageIndex == settledPosition.pageIndex
            ) {
                return@LaunchedEffect
            }
            lastPrefetchedPageIndex = settledPosition.pageIndex
            pageSource.prefetchAround(
                item = item,
                settledPageIndex = settledPosition.pageIndex,
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
            pageSource = pageSource,
            pagerState = pagerState,
            onVisiblePagesLoaded = onVisiblePagesLoaded,
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
                } else {
                    onNavigatePastEnd()
                }
            },
        )
    }
}
