package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import kotlinx.coroutines.delay

@Composable
internal fun ComicPageCurlPagerBox(
    item: ReadableItem,
    spreads: List<ComicPageSpread>,
    readerState: ComicsReaderState,
    pageSource: ComicPageSource,
    pagerState: PagerState,
    loadedVisiblePageIndexes: Set<Int>,
    tapTurner: ComicPageCurlTapTurner,
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
    val curlState = remember { ComicPageCurlState() }
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
    val latestCurlBaseEnabled = rememberUpdatedState(curlBaseEnabled)
    val latestCurrentSpreadIndex = rememberUpdatedState(currentSpreadIndex)
    val latestSpreadCount = rememberUpdatedState(spreads.size)
    val latestReadingDirection = rememberUpdatedState(readerState.readingDirection)

    DisposableEffect(tapTurner, curlState, pagerState) {
        tapTurner.turnHandler = handler@{ target ->
            if (curlInteraction != null) return@handler true
            if (!latestCurlBaseEnabled.value) return@handler false
            val interaction = startComicPageCurlInteraction(
                target = target,
                currentSpreadIndex = latestCurrentSpreadIndex.value,
                spreadCount = latestSpreadCount.value,
                readingDirection = latestReadingDirection.value,
                currentComposite = latestCurrentCurlComposite.value,
                adjacentComposite = when (target) {
                    ComicPageCurlTarget.Previous -> latestPreviousCurlComposite.value
                    ComicPageCurlTarget.Next -> latestNextCurlComposite.value
                },
                downPosition = null,
            ) ?: return@handler false
            curlInteraction = interaction
            try {
                val turn = interaction.turn
                curlState.snapTo(turn.restingEdge(interaction.grab))
                curlState.animateAlong(durationMillis = ComicPageCurlTapTurnDurationMillis) { fraction ->
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

    LaunchedEffect(
        curlBaseEnabled,
        settledSpreadLoaded,
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
        if (!settledSpreadLoaded) return@LaunchedEffect
        delay(ComicPageCurlIdlePrewarmDelayMillis)
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
                interaction = interaction,
                state = curlState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// Short enough that a reader turning again soon after a turn still gets a curl, long enough to let
// the settled page draw first: rasterizing is off the main thread, but it competes for CPU.
private const val ComicPageCurlIdlePrewarmDelayMillis = 300L

/** Lets tap navigation play a curl instead of the pager's slide. */
@Stable
internal class ComicPageCurlTapTurner {
    internal var turnHandler: (suspend (ComicPageCurlTarget) -> Boolean)? = null

    /** True when the tap was handled: played as a curl, or ignored because a turn is in progress. */
    suspend fun turn(
        target: ComicPageCurlTarget,
    ): Boolean = turnHandler?.invoke(target) ?: false
}
