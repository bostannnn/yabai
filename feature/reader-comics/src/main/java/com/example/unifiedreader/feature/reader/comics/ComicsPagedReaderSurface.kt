package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapTurner
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import kotlinx.coroutines.launch

@Composable
internal fun ComicsPagedReaderSurface(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    spreads: List<ComicPageSpread>,
    pageSource: ComicPageSource,
    readerState: ComicsReaderState,
    loadedVisiblePageIndexes: Set<Int>,
    onVisiblePagesLoaded: (Collection<Int>) -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePastEnd: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var lastPrefetchedPageIndex by remember(item.id) { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val pagerState = rememberPagerState(
            initialPage = spreads.indexOfPage(readerState.resumeAnchorPage, readerState.resumeSegment),
            pageCount = { spreads.size },
        )
        val currentSpread = spreads.getOrNull(pagerState.currentPage) ?: spreads.first()
        val curlTapTurner = remember(item.id) { PageCurlTapTurner() }

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

        val spreadPagerContent: @Composable (Boolean) -> Unit = {
            ComicSpreadPager(
                item = item,
                spreads = spreads,
                currentPage = readerState.currentPage,
                readingDirection = readerState.readingDirection,
                pageFitMode = readerState.pageFitMode,
                isZoomed = readerState.isCurrentPageZoomed,
                userScrollEnabled = it,
                pageSource = pageSource,
                pagerState = pagerState,
                onVisiblePagesLoaded = onVisiblePagesLoaded,
                onZoomStateChanged = readerState::onCurrentPageZoomChanged,
                onInteraction = {},
                onToggleOverlay = onToggleOverlay,
                onNavigatePrevious = { spreadIndex ->
                    if (spreadIndex > 0) {
                        coroutineScope.launch {
                            if (!curlTapTurner.turn(PageCurlTarget.Previous)) {
                                pagerState.animateScrollToPage(spreadIndex - 1)
                            }
                        }
                    }
                },
                onNavigateNext = { spreadIndex ->
                    if (spreadIndex < spreads.lastIndex) {
                        coroutineScope.launch {
                            if (!curlTapTurner.turn(PageCurlTarget.Next)) {
                                pagerState.animateScrollToPage(spreadIndex + 1)
                            }
                        }
                    } else {
                        onNavigatePastEnd()
                    }
                },
            )
        }
        if (shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = readerState.pageTurnStyle,
                readerMode = readerState.readerMode,
            )
        ) {
            ComicPageCurlPagerBox(
                item = item,
                spreads = spreads,
                readerState = readerState,
                pageSource = pageSource,
                pagerState = pagerState,
                loadedVisiblePageIndexes = loadedVisiblePageIndexes,
                tapTurner = curlTapTurner,
                modifier = Modifier.fillMaxSize(),
                content = spreadPagerContent,
            )
        } else {
            spreadPagerContent(true)
        }
    }
}
