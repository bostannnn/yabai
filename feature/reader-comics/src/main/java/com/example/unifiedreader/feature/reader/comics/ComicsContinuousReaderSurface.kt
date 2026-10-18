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
            withFrameNanos { }
            if (targetRatio > 0f) {
                val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                val targetOffset = visibleItem
                    ?.size
                    ?.takeIf { it > 0 }
                    ?.let { size -> (size * targetRatio).roundToInt() }
                if (targetOffset != null) {
                    listState.scrollToItem(
                        index = targetIndex,
                        scrollOffset = targetOffset,
                    )
                    withFrameNanos { }
                }
            }
            // A last page too short to reach the top lands at the end of the strip, which is where the
            // reader was: sessions saved before the end was reported, a jump to the last page, or a
            // switch from paged mode on it.
            if (targetIndex == pages.lastIndex && !listState.canScrollForward) {
                readerState.onContinuousRestoreLanded(
                    pageIndex = targetIndex,
                    offsetRatio = ContinuousStripEndOffsetRatio,
                )
            }
        }

        LaunchedEffect(item.id, listState, pages.size) {
            val reporter = ContinuousPositionReporter()
            snapshotFlow {
                listState.isScrollInProgress to listState.currentVisiblePosition(pageCount = pages.size)
            }.collect { (isScrolling, position) ->
                if (position != null) {
                    settledPageIndexForPrefetch = position.pageIndex
                }
                reporter.onFrame(isScrolling = isScrolling, position = position)?.let { reported ->
                    readerState.onContinuousPositionSettled(
                        pageIndex = reported.pageIndex,
                        offsetRatio = reported.offsetRatio,
                    )
                }
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

internal data class ContinuousVisiblePosition(
    val pageIndex: Int,
    val offsetRatio: Float,
)

internal data class ContinuousVisiblePage(
    val index: Int,
    val offset: Int,
    val size: Int,
)

private fun androidx.compose.foundation.lazy.LazyListState.currentVisiblePosition(
    pageCount: Int,
): ContinuousVisiblePosition? = continuousReaderVisiblePosition(
    visiblePages = layoutInfo.visibleItemsInfo.map { item ->
        ContinuousVisiblePage(index = item.index, offset = item.offset, size = item.size)
    },
    pageCount = pageCount,
    isScrolledToEnd = !canScrollForward,
)

/** The offset a position reports for the end of the strip: past the whole last page. */
internal const val ContinuousStripEndOffsetRatio = 1f

// The reading position is the page at the top of the viewport. The end of the strip is the last page
// at a full offset, the one position that finishes the book: a short last page never reaches the top,
// and its top edge coming into view, or a tall one reaching the top, is not the end yet.
internal fun continuousReaderVisiblePosition(
    visiblePages: List<ContinuousVisiblePage>,
    pageCount: Int,
    isScrolledToEnd: Boolean,
): ContinuousVisiblePosition? {
    val pagesInRange = visiblePages.filter { page -> page.index in 0 until pageCount }
    if (isScrolledToEnd && pagesInRange.any { page -> page.index == pageCount - 1 }) {
        return ContinuousVisiblePosition(pageIndex = pageCount - 1, offsetRatio = ContinuousStripEndOffsetRatio)
    }
    val visiblePage = pagesInRange.firstOrNull() ?: return null
    val offsetPixels = (-visiblePage.offset).coerceAtLeast(0)
    val ratio = if (visiblePage.size > 0) {
        offsetPixels / visiblePage.size.toFloat()
    } else {
        0f
    }
    return ContinuousVisiblePosition(
        pageIndex = visiblePage.index,
        offsetRatio = sanitizeComicResumeScrollOffsetRatio(ratio) ?: 0f,
    )
}

/**
 * Picks the webtoon positions that are the reader's: the ones a scroll produced. A page at the top
 * changing height as its image replaces the placeholder also moves the position, as after reopening a
 * book, and must not overwrite the restored progress.
 */
internal class ContinuousPositionReporter {
    private var scrolledSinceReport = false
    private var lastReported: ContinuousVisiblePosition? = null

    fun onFrame(isScrolling: Boolean, position: ContinuousVisiblePosition?): ContinuousVisiblePosition? {
        if (isScrolling) scrolledSinceReport = true
        if (!scrolledSinceReport || position == null) return null
        if (!isScrolling) scrolledSinceReport = false
        val previous = lastReported
        if (previous != null &&
            previous.pageIndex == position.pageIndex &&
            abs(previous.offsetRatio - position.offsetRatio) < 0.02f
        ) {
            return null
        }
        lastReported = position
        return position
    }
}
