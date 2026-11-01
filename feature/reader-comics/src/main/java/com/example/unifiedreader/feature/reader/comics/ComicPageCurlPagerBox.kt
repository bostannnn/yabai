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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.curl.PageCurlState
import com.example.unifiedreader.feature.reader.core.curl.PageCurlSurface
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapTurnDurationMillis
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapTurner
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import com.example.unifiedreader.feature.reader.core.curl.pageCurlDragGesture
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun ComicPageCurlPagerBox(
    item: ReadableItem,
    spreads: List<ComicPageSpread>,
    readerState: ComicsReaderState,
    pageSource: ComicPageSource,
    pagerState: PagerState,
    loadedVisiblePageIndexes: Set<Int>,
    tapTurner: PageCurlTapTurner,
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
    val curlState = remember(item.id) { PageCurlState() }
    val canvasColor = UnifiedReaderThemeTokens.colors.readerCanvas
    val curlCompositeCache = remember(item.id, pageSource, canvasColor) { ComicPageCurlCompositeCache(canvasColor) }
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

    val curlBaseEnabled = resolveComicPageCurlAvailability(
        pageTurnStyle = readerState.pageTurnStyle,
        readerMode = readerState.readerMode,
        isCurrentPageZoomed = readerState.isCurrentPageZoomed,
        hasCurrentComposite = true,
        hasAdjacentComposite = true,
        containerSize = containerSize,
    ).isAvailable
    val latestCurlBaseEnabled by rememberUpdatedState(curlBaseEnabled)

    val start: suspend (PageCurlTarget, Offset?) -> ComicPageCurlInteraction? = start@{ target, down ->
        if (!curlBaseEnabled) return@start null
        // Read the pager at execution time: another queued turn may just have completed.
        val currentIndex = pagerState.currentPage
        val targetIndex = resolveComicPageCurlTargetSpreadIndex(currentIndex, spreads.size, target) ?: return@start null
        val composites = withTimeoutOrNull(ComicPageCurlPreparationTimeoutMillis) {
            coroutineScope {
                val current = async {
                    curlCompositeCache.getOrLoad(
                        item, spreads[currentIndex], pageSource, containerSize,
                        readerState.pageFitMode, stitchedSpreadOverlapPx, ComicPageLoadPriority.Visible,
                    )
                }
                val adjacent = async {
                    curlCompositeCache.getOrLoad(
                        item, spreads[targetIndex], pageSource, containerSize,
                        readerState.pageFitMode, stitchedSpreadOverlapPx, ComicPageLoadPriority.Visible,
                    )
                }
                current.await() to adjacent.await()
            }
        } ?: return@start null
        startComicPageCurlInteraction(
            target = target,
            currentSpreadIndex = currentIndex,
            spreadCount = spreads.size,
            readingDirection = readerState.readingDirection,
            currentComposite = composites.first,
            adjacentComposite = composites.second,
            downPosition = down,
        )?.also { curlInteraction = it }
    }
    val latestStart by rememberUpdatedState(start)

    DisposableEffect(tapTurner, curlState, pagerState) {
        tapTurner.turnHandler = handler@{ target ->
            if (!latestCurlBaseEnabled) return@handler false
            // A loading failure leaves the page in place; it must not silently select slide mode.
            val interaction = latestStart(target, null) ?: return@handler true
            try {
                val turn = interaction.turn
                curlState.snapTo(turn.restingEdge(interaction.grab))
                curlState.animateAlong(durationMillis = PageCurlTapTurnDurationMillis) { fraction ->
                    turn.automaticTurnEdge(grab = interaction.grab, fraction = fraction)
                }
                pagerState.scrollToPage(interaction.targetSpreadIndex)
            } finally {
                curlInteraction = null
                curlState.reset()
            }
            true
        }
        onDispose {
            tapTurner.turnHandler = null
        }
    }

    val settledSpreadLoaded = spreads
        .getOrNull(pagerState.settledPage)
        ?.pageIndexes
        ?.all { pageIndex -> pageIndex in loadedVisiblePageIndexes } == true

    val prewarmSpreadIndex = curlInteraction?.targetSpreadIndex ?: pagerState.settledPage
    LaunchedEffect(
        curlBaseEnabled,
        settledSpreadLoaded,
        prewarmSpreadIndex,
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
        if (!settledSpreadLoaded && curlInteraction == null) return@LaunchedEffect
        curlCompositeCache.prewarm(
            item = item,
            spreads = spreads,
            settledSpreadIndex = prewarmSpreadIndex,
            pageSource = pageSource,
            canvasSize = containerSize,
            pageFitMode = readerState.pageFitMode,
            stitchedSpreadOverlapPx = stitchedSpreadOverlapPx,
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pageCurlDragGesture(
                enabled = curlBaseEnabled,
                rightToLeft = readerState.readingDirection == ComicReadingDirection.RTL,
                state = curlState,
                gestureScope = coroutineScope,
                turner = tapTurner,
                canStartTurn = { target ->
                    resolveComicPageCurlTargetSpreadIndex(pagerState.currentPage, spreads.size, target) != null
                },
                startTurn = { target, down -> latestStart(target, down) },
                onTurnSettled = { drag, committed ->
                    if (committed) {
                        pagerState.scrollToPage((drag as ComicPageCurlInteraction).targetSpreadIndex)
                    }
                },
                onTurnEnded = {
                    curlInteraction = null
                },
                keys = arrayOf(item.id, spreads.map { it.id }, containerSize, pagerState, readerState.pageFitMode),
            ),
    ) {
        content(!curlBaseEnabled)
        curlInteraction?.let { interaction ->
            PageCurlSurface(
                turn = interaction.turn,
                grab = interaction.grab,
                frames = interaction.frames,
                state = curlState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// Slow/failed page loads leave the current page available for another attempt.
private const val ComicPageCurlPreparationTimeoutMillis = 5_000L
