package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun ComicsContinuousReaderSurface(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
    readerState: ComicsReaderState,
    loadedVisiblePageIndexes: Set<Int>,
    metadataLoadingEnabled: Boolean,
    onVisiblePagesLoaded: (Collection<Int>) -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePastEnd: () -> Unit,
) {
    var hasTriggeredPastEndNavigation by remember(item.id) { mutableStateOf(false) }
    var settledPageIndexForPrefetch by remember(item.id) { mutableStateOf<Int?>(null) }
    var lastPrefetchedPageIndex by remember(item.id) { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = readerState.resumeAnchorPage.coerceIn(0, pages.lastIndex),
    )
    val endNavigationModifier = rememberContinuousEndNavigationModifier(
        canScrollForward = listState.canScrollForward,
        alreadyTriggered = hasTriggeredPastEndNavigation,
        onNavigatePastEnd = {
            hasTriggeredPastEndNavigation = true
            onNavigatePastEnd()
        },
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val activePageWindow by remember(listState, pages.size) {
            derivedStateOf {
                val visibleIndexes = listState.layoutInfo.visibleItemsInfo
                    .map { it.index }
                    .filter { it in 0 until pages.size }
                val firstVisible = visibleIndexes.minOrNull() ?: readerState.currentPage.coerceIn(0, pages.lastIndex)
                val lastVisible = visibleIndexes.maxOrNull() ?: firstVisible
                val startIndex = (firstVisible - 1).coerceAtLeast(0)
                val endIndex = (lastVisible + 1).coerceAtMost(pages.lastIndex)
                startIndex..endIndex
            }
        }
        val contentWidth = remember(maxWidth, maxHeight) {
            continuousReaderContentWidth(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
            )
        }

        LaunchedEffect(item.id, readerState.resumeAnchorPage, readerState.resumeScrollOffsetRatio, contentWidth) {
            val targetIndex = readerState.resumeAnchorPage.coerceIn(0, pages.lastIndex)
            val targetRatio = sanitizeComicResumeScrollOffsetRatio(readerState.resumeScrollOffsetRatio) ?: 0f
            if (listState.isScrollInProgress) {
                return@LaunchedEffect
            }
            val currentPosition = listState.currentVisiblePosition(pageCount = pages.size)
            if (currentPosition != null &&
                currentPosition.pageIndex == targetIndex &&
                abs(currentPosition.offsetRatio - targetRatio) < 0.02f
            ) {
                return@LaunchedEffect
            }
            listState.scrollToItem(targetIndex, 0)
            if (targetRatio <= 0f) return@LaunchedEffect
            withFrameNanos { }
            val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            val targetOffset = visibleItem
                ?.size
                ?.takeIf { it > 0 }
                ?.let { size -> (size * targetRatio).roundToInt() }
                ?: return@LaunchedEffect
            listState.scrollToItem(
                index = targetIndex,
                scrollOffset = targetOffset,
            )
        }

        LaunchedEffect(item.id, listState, pages.size) {
            snapshotFlow { listState.currentVisiblePosition(pageCount = pages.size) }
                .filterNotNull()
                .distinctUntilChanged { previous, next ->
                    previous.pageIndex == next.pageIndex &&
                        abs(previous.offsetRatio - next.offsetRatio) < 0.02f
                }
                .collect { position ->
                    readerState.onContinuousPositionSettled(
                        pageIndex = position.pageIndex,
                        offsetRatio = position.offsetRatio,
                    )
                    settledPageIndexForPrefetch = position.pageIndex
                }
        }

        LaunchedEffect(item.id, settledPageIndexForPrefetch, loadedVisiblePageIndexes, pages.size) {
            val pageIndex = settledPageIndexForPrefetch ?: return@LaunchedEffect
            if (pageIndex !in loadedVisiblePageIndexes || lastPrefetchedPageIndex == pageIndex) {
                return@LaunchedEffect
            }
            lastPrefetchedPageIndex = pageIndex
            pageSource.prefetchAround(
                item = item,
                settledPageIndex = pageIndex,
                pageCount = pages.size,
                window = continuousReaderPrefetchWindow(),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(endNavigationModifier)
                .background(Color(0xFF0A0D11)),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(
                items = pages,
                key = { _, page -> page.id },
            ) { _, page ->
                ContinuousComicPageItem(
                    item = item,
                    page = page,
                    pageSource = pageSource,
                    shouldLoadImage = page.pageIndex in activePageWindow,
                    metadataLoadingEnabled = metadataLoadingEnabled,
                    contentWidth = contentWidth,
                    fallbackPlaceholderHeight = continuousReaderFallbackPlaceholderHeight(
                        contentWidth = contentWidth,
                        viewportHeight = maxHeight,
                    ),
                    onVisiblePageLoaded = { pageIndex -> onVisiblePagesLoaded(listOf(pageIndex)) },
                    onToggleOverlay = onToggleOverlay,
                )
            }
        }
    }
}

private data class ContinuousVisiblePosition(
    val pageIndex: Int,
    val offsetRatio: Float,
)

private fun androidx.compose.foundation.lazy.LazyListState.currentVisiblePosition(
    pageCount: Int,
): ContinuousVisiblePosition? {
    val visibleItems = layoutInfo.visibleItemsInfo
        .filter { item -> item.index in 0 until pageCount }
    val visibleItem = visibleItems
        .lastOrNull { item -> item.index == pageCount - 1 }
        ?: visibleItems
        .firstOrNull { it.index >= 0 }
        ?: return null
    val offsetPixels = (-visibleItem.offset).coerceAtLeast(0)
    val ratio = if (visibleItem.size > 0) {
        offsetPixels / visibleItem.size.toFloat()
    } else {
        0f
    }
    return ContinuousVisiblePosition(
        pageIndex = visibleItem.index,
        offsetRatio = sanitizeComicResumeScrollOffsetRatio(ratio) ?: 0f,
    )
}
