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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
    onToggleOverlay: () -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = readerState.resumeAnchorPage.coerceIn(0, pages.lastIndex),
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
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
            val currentPosition = listState.currentVisiblePosition()
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
            var lastPrefetchedPageIndex: Int? = null
            snapshotFlow { listState.currentVisiblePosition() }
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
                    if (lastPrefetchedPageIndex != position.pageIndex) {
                        lastPrefetchedPageIndex = position.pageIndex
                        pageSource.prefetchAround(
                            item = item,
                            settledPageIndex = position.pageIndex,
                            pageCount = pages.size,
                            window = continuousReaderPrefetchWindow(),
                        )
                    }
                }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
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
                    contentWidth = contentWidth,
                    fallbackPlaceholderHeight = continuousReaderFallbackPlaceholderHeight(
                        contentWidth = contentWidth,
                        viewportHeight = maxHeight,
                    ),
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

private fun androidx.compose.foundation.lazy.LazyListState.currentVisiblePosition(): ContinuousVisiblePosition? {
    val visibleItem = layoutInfo.visibleItemsInfo
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
