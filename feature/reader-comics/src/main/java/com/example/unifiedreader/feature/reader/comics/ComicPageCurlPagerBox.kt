package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.core.model.ReadableItem

@Composable
internal fun ComicPageCurlPagerBox(
    item: ReadableItem,
    spreads: List<ComicPageSpread>,
    readerState: ComicsReaderState,
    pageSource: ComicPageSource,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    content: @Composable (userScrollEnabled: Boolean) -> Unit,
) {
    if (!shouldUseComicPageCurlPagerLayer(
            pageTurnStyle = readerState.pageTurnStyle,
            readerMode = readerState.readerMode,
        )
    ) {
        Box(modifier = modifier) {
            content(true)
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember(item.id) { mutableStateOf(IntSize.Zero) }
    val curlState = rememberComicPageCurlState()
    val curlCompositeCache = remember(item.id, pageSource) { ComicPageCurlCompositeCache() }
    var curlInteraction by remember(item.id) { mutableStateOf<ComicPageCurlInteraction?>(null) }
    val density = LocalDensity.current
    val stitchedSpreadOverlapPx = with(density) { ComicStitchedSpreadOverlap.roundToPx() }

    DisposableEffect(curlCompositeCache) {
        onDispose {
            curlCompositeCache.clear()
        }
    }

    if (spreads.isEmpty()) {
        Box(modifier = modifier) {
            content(true)
        }
        return
    }

    val currentSpreadIndex = pagerState.currentPage.coerceIn(0, spreads.lastIndex)
    val currentSpread = spreads[currentSpreadIndex]
    val previousSpread = spreads.getOrNull(currentSpreadIndex - 1)
    val nextSpread = spreads.getOrNull(currentSpreadIndex + 1)
    val currentCurlComposite = curlCompositeCache.compositeFor(
        spread = currentSpread,
        canvasSize = containerSize,
        pageFitMode = readerState.pageFitMode,
    )
    val previousCurlComposite = curlCompositeCache.compositeFor(
        spread = previousSpread,
        canvasSize = containerSize,
        pageFitMode = readerState.pageFitMode,
    )
    val nextCurlComposite = curlCompositeCache.compositeFor(
        spread = nextSpread,
        canvasSize = containerSize,
        pageFitMode = readerState.pageFitMode,
    )
    val latestCurrentCurlComposite = rememberUpdatedState(currentCurlComposite)
    val latestPreviousCurlComposite = rememberUpdatedState(previousCurlComposite)
    val latestNextCurlComposite = rememberUpdatedState(nextCurlComposite)
    val latestCurlInteractionActive = rememberUpdatedState(curlInteraction != null)
    val curlBaseEnabled = resolveComicPageCurlAvailability(
        pageTurnStyle = readerState.pageTurnStyle,
        readerMode = readerState.readerMode,
        isCurrentPageZoomed = readerState.isCurrentPageZoomed,
        hasCurrentComposite = true,
        hasAdjacentComposite = true,
        containerSize = containerSize,
    ).isAvailable

    LaunchedEffect(
        curlBaseEnabled,
        pagerState.settledPage,
        spreads,
        item.id,
        readerState.pageFitMode,
        containerSize,
        stitchedSpreadOverlapPx,
    ) {
        if (!curlBaseEnabled) {
            curlCompositeCache.clear()
            curlInteraction = null
            curlState.reset()
            return@LaunchedEffect
        }
        curlCompositeCache.prewarm(
            item = item,
            spreads = spreads,
            settledSpreadIndex = pagerState.settledPage,
            pageSource = pageSource,
            canvasSize = containerSize,
            pageFitMode = readerState.pageFitMode,
            stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .then(
                Modifier.comicPageCurlPagerGesture(
                    enabled = curlBaseEnabled,
                    currentSpreadIndex = currentSpreadIndex,
                    spreadCount = spreads.size,
                    containerSize = containerSize,
                    readingDirection = readerState.readingDirection,
                    currentComposite = { latestCurrentCurlComposite.value },
                    previousComposite = { latestPreviousCurlComposite.value },
                    nextComposite = { latestNextCurlComposite.value },
                    isInteractionActive = { latestCurlInteractionActive.value },
                    state = curlState,
                    gestureScope = coroutineScope,
                    onInteractionStarted = { interaction ->
                        curlInteraction = interaction
                    },
                    onInteractionEnded = {
                        curlInteraction = null
                    },
                    onTurnCommitted = { targetSpreadIndex ->
                        pagerState.scrollToPage(targetSpreadIndex)
                    },
                ),
            ),
    ) {
        content(curlInteraction == null)
        curlInteraction?.let { interaction ->
            ComicPageCurlSurface(
                currentPage = interaction.currentPage,
                underneathPage = interaction.underneathPage,
                state = curlState,
                readingDirection = readerState.readingDirection,
                sheetBounds = interaction.sheetBounds,
                target = interaction.target,
                gesturesEnabled = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
