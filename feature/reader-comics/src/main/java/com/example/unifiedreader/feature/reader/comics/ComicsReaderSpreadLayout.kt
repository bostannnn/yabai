package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import kotlin.math.abs
import kotlin.math.sqrt

internal const val SpreadModeAutoMinWidthDp = 700
private val StitchedSpreadOverlap = 1.dp
private const val StitchedSpreadMaxDimensionDeltaFraction = 0.05f
private const val StitchedSpreadSampleCount = 24
private const val StitchedSpreadMaxAverageEdgeDistance = 0.08f

@Composable
internal fun ComicSpreadPager(
    item: ReadableItem,
    spreads: List<ComicPageSpread>,
    currentPage: Int,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    isZoomed: Boolean,
    overlayVisible: Boolean,
    pageSource: ComicPageSource,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onZoomStateChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePrevious: (Int) -> Unit,
    onNavigateNext: (Int) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !isZoomed,
        reverseLayout = readingDirection == ComicReadingDirection.RTL,
        modifier = Modifier.fillMaxSize(),
    ) { spreadIndex ->
        val spread = spreads[spreadIndex]
        ComicSpreadSurface(
            item = item,
            spread = spread,
            pageSource = pageSource,
            isActiveSpread = spread.pageIndexes.contains(currentPage),
            readingDirection = readingDirection,
            pageFitMode = pageFitMode,
            overlayVisible = overlayVisible,
            onZoomStateChanged = onZoomStateChanged,
            onInteraction = onInteraction,
            onToggleOverlay = onToggleOverlay,
            onNavigatePrevious = { onNavigatePrevious(spreadIndex) },
            onNavigateNext = { onNavigateNext(spreadIndex) },
        )
    }
}

@Composable
private fun ComicSpreadSurface(
    item: ReadableItem,
    spread: ComicPageSpread,
    pageSource: ComicPageSource,
    isActiveSpread: Boolean,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    overlayVisible: Boolean,
    onZoomStateChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
) {
    val viewportState = rememberComicPageViewportState(pageId = spread.id)
    var containerSize by remember(spread.id) { mutableStateOf(IntSize.Zero) }
    val pageLoadResults by produceState<Map<String, ComicPageLoadResult?>>(
        initialValue = emptyMap(),
        key1 = item.id,
        key2 = spread.id,
    ) {
        value = spread.pages.associate { page ->
            page.id to pageSource.loadPageImage(item, page)
        }
    }
    val imageSize = remember(pageLoadResults, spread.pageIndexes) {
        compositeImageSize(
            spread = spread,
            pageLoadResults = pageLoadResults,
        )
    }

    LaunchedEffect(
        isActiveSpread,
        viewportState.isZoomed,
        pageFitMode,
        containerSize,
        imageSize,
    ) {
        viewportState.reconcileLayout(
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = pageFitMode,
        )
        if (isActiveSpread) {
            onZoomStateChanged(
                viewportState.canPan(
                    containerSize = containerSize,
                    imageSize = imageSize,
                    fitMode = pageFitMode,
                ),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it },
            color = Color(0xFF0A0D11),
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.comicGestureModifier(
                    gestureKey = spread.id,
                    containerSize = containerSize,
                    imageSize = imageSize,
                    viewportState = viewportState,
                    readingDirection = readingDirection,
                    pageFitMode = pageFitMode,
                    overlayVisible = overlayVisible,
                    onInteraction = onInteraction,
                    onToggleOverlay = onToggleOverlay,
                    onNavigatePrevious = onNavigatePrevious,
                    onNavigateNext = onNavigateNext,
                ).testTag(comicGestureSurfaceTag(spread.id)),
            ) {
                SpreadContent(
                    spread = spread,
                    pageFitMode = pageFitMode,
                    pageLoadResults = pageLoadResults,
                )
            }
        }
    }
}

@Composable
private fun SpreadContent(
    spread: ComicPageSpread,
    pageFitMode: ComicPageFitMode,
    pageLoadResults: Map<String, ComicPageLoadResult?>,
) {
    val isDoublePage = spread.pages.size == 2
    val loadedImagesByPageId = remember(pageLoadResults) {
        pageLoadResults.mapValues { (_, result) ->
            (result as? ComicPageLoadResult.Success)?.image
        }
    }
    val useFitSpread = isDoublePage && pageFitMode == ComicPageFitMode.FitPage
    val likelyStitchedSpread = remember(loadedImagesByPageId, spread.pageIndexes) {
        spread.isLikelyStitchedSpread(loadedImagesByPageId)
    }
    if (useFitSpread) {
        FitSpreadContent(
            spread = spread,
            pageLoadResults = pageLoadResults,
            loadedImagesByPageId = loadedImagesByPageId,
            stitchedOverlap = if (likelyStitchedSpread) StitchedSpreadOverlap else 0.dp,
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (spread.isSoloCover) 0.dp else 0.dp,
                vertical = 0.dp,
            ),
        horizontalArrangement = if (isDoublePage) Arrangement.spacedBy(0.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        spread.pages.forEachIndexed { index, page ->
            Box(
                modifier = Modifier
                    .then(
                        if (isDoublePage) {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                    .then(
                        if (likelyStitchedSpread && index > 0) {
                            Modifier.offset(x = -StitchedSpreadOverlap)
                        } else {
                            Modifier
                        },
                    )
                    .background(Color(0xFF0B0F13)),
                contentAlignment = Alignment.Center,
            ) {
                when (val result = pageLoadResults[page.id]) {
                    is ComicPageLoadResult.Success -> {
                        Image(
                            bitmap = result.image,
                            contentDescription = page.contentDescription,
                            contentScale = pageFitMode.contentScale(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is ComicPageLoadResult.Failure -> ReaderPageMessage(message = result.message)
                    null -> ReaderPageMessage(message = "Loading page...")
                }
            }
        }
    }
}

@Composable
private fun FitSpreadContent(
    spread: ComicPageSpread,
    pageLoadResults: Map<String, ComicPageLoadResult?>,
    loadedImagesByPageId: Map<String, ImageBitmap?>,
    stitchedOverlap: Dp,
) {
    val pageAspectRatios = remember(loadedImagesByPageId, spread.pageIndexes) {
        spread.pages.map { page ->
            loadedImagesByPageId[page.id]?.safeAspectRatio() ?: 1f
        }
    }
    val compositeSize = remember(pageLoadResults, spread.pageIndexes) {
        compositeImageSize(
            spread = spread,
            pageLoadResults = pageLoadResults,
        )
    }
    val combinedAspectRatio = remember(compositeSize) {
        if (compositeSize.width > 0 && compositeSize.height > 0) {
            compositeSize.width / compositeSize.height.toFloat()
        } else {
            pageAspectRatios.sum().coerceAtLeast(1f)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val containerAspectRatio = if (maxHeight.value > 0f) {
            maxWidth.value / maxHeight.value
        } else {
            combinedAspectRatio
        }
        val spreadModifier = if (combinedAspectRatio >= containerAspectRatio) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(combinedAspectRatio)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(combinedAspectRatio, matchHeightConstraintsFirst = true)
        }

        Row(
            modifier = spreadModifier.clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            spread.pages.forEachIndexed { index, page ->
                Box(
                    modifier = Modifier
                        .weight(pageAspectRatios[index])
                        .fillMaxHeight()
                        .then(
                            if (stitchedOverlap > 0.dp && index > 0) {
                                Modifier.offset(x = -stitchedOverlap)
                            } else {
                                Modifier
                            },
                        )
                        .background(Color(0xFF0B0F13)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val result = pageLoadResults[page.id]) {
                        is ComicPageLoadResult.Success -> {
                            Image(
                                bitmap = result.image,
                                contentDescription = page.contentDescription,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        is ComicPageLoadResult.Failure -> ReaderPageMessage(message = result.message)
                        null -> ReaderPageMessage(message = "Loading page...")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderPageMessage(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141A20)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB7C4CE),
        )
    }
}

internal fun ComicPageFitMode.contentScale(): ContentScale = when (this) {
    ComicPageFitMode.FitPage -> ContentScale.Fit
    ComicPageFitMode.FitWidth -> ContentScale.FillWidth
    ComicPageFitMode.FitHeight -> ContentScale.FillHeight
}

internal fun shouldUseDoublePageSpreads(
    spreadMode: ComicSpreadMode,
    maxWidthDp: Float,
    maxHeightDp: Float,
    pageCount: Int,
): Boolean {
    if (pageCount < 2) return false
    return when (spreadMode) {
        ComicSpreadMode.Auto -> maxWidthDp >= SpreadModeAutoMinWidthDp || maxWidthDp > maxHeightDp
        ComicSpreadMode.Single -> false
        ComicSpreadMode.Double -> true
    }
}

internal fun buildComicPageSpreads(
    pages: List<ComicPageSpec>,
    readingDirection: ComicReadingDirection,
    useDoublePageSpreads: Boolean,
): List<ComicPageSpread> {
    if (!useDoublePageSpreads) {
        return pages.mapIndexed { index, page ->
            ComicPageSpread(
                id = "spread-$index",
                pages = listOf(page),
                pageIndexes = listOf(index),
                resumePageIndex = index,
                progressLabel = "${index + 1} / ${pages.size}",
            )
        }
    }

    val spreads = mutableListOf<ComicPageSpread>()
    spreads += ComicPageSpread(
        id = "spread-cover",
        pages = listOf(pages.first()),
        pageIndexes = listOf(0),
        resumePageIndex = 0,
        progressLabel = "1 / ${pages.size}",
    )
    var pageIndex = 1
    while (pageIndex < pages.size) {
        val nextPageIndex = (pageIndex + 1).takeIf { it < pages.size }
        val displayPageIndexes = when (readingDirection) {
            ComicReadingDirection.LTR -> listOfNotNull(pageIndex, nextPageIndex)
            ComicReadingDirection.RTL -> listOfNotNull(nextPageIndex, pageIndex)
        }
        val label = if (nextPageIndex != null) {
            "${pageIndex + 1}-${nextPageIndex + 1} / ${pages.size}"
        } else {
            "${pageIndex + 1} / ${pages.size}"
        }
        spreads += ComicPageSpread(
            id = "spread-$pageIndex",
            pages = displayPageIndexes.map(pages::get),
            pageIndexes = displayPageIndexes,
            resumePageIndex = pageIndex,
            progressLabel = label,
        )
        pageIndex += 2
    }
    return spreads
}

internal fun List<ComicPageSpread>.indexOfPage(
    pageIndex: Int,
): Int = indexOfFirst { spread -> pageIndex in spread.pageIndexes }
    .takeIf { it >= 0 }
    ?: 0

private fun compositeImageSize(
    spread: ComicPageSpread,
    pageLoadResults: Map<String, ComicPageLoadResult?>,
): IntSize {
    val loadedImages = spread.pages.mapNotNull { page ->
        (pageLoadResults[page.id] as? ComicPageLoadResult.Success)?.image
    }
    if (loadedImages.isEmpty()) {
        return IntSize.Zero
    }
    return IntSize(
        width = loadedImages.sumOf(ImageBitmap::width),
        height = loadedImages.maxOf(ImageBitmap::height),
    )
}

private val ComicPageSpread.isSoloCover: Boolean
    get() = pageIndexes == listOf(0)

private fun ComicPageSpread.isLikelyStitchedSpread(
    loadedImagesByPageId: Map<String, ImageBitmap?>,
): Boolean {
    if (pages.size != 2) return false
    val leftImage = loadedImagesByPageId[pages[0].id] ?: return false
    val rightImage = loadedImagesByPageId[pages[1].id] ?: return false
    val heightsAreAligned = abs(leftImage.height - rightImage.height) <=
        maxOf(leftImage.height, rightImage.height) * StitchedSpreadMaxDimensionDeltaFraction
    val widthsAreAligned = abs(leftImage.width - rightImage.width) <=
        maxOf(leftImage.width, rightImage.width) * StitchedSpreadMaxDimensionDeltaFraction
    if (!heightsAreAligned || !widthsAreAligned) return false

    val leftPixels = leftImage.toPixelMap()
    val rightPixels = rightImage.toPixelMap()
    val sampleCount = minOf(
        StitchedSpreadSampleCount,
        leftImage.height,
        rightImage.height,
    )
    if (sampleCount <= 0) return false

    val leftX = leftImage.width - 1
    val rightX = 0
    var totalDistance = 0f
    for (sampleIndex in 0 until sampleCount) {
        val y = if (sampleCount == 1) {
            0
        } else {
            ((sampleIndex.toFloat() / (sampleCount - 1)) * (minOf(leftImage.height, rightImage.height) - 1))
                .toInt()
        }
        totalDistance += leftPixels[leftX, y].distanceTo(rightPixels[rightX, y])
    }
    val averageDistance = totalDistance / sampleCount
    return averageDistance <= StitchedSpreadMaxAverageEdgeDistance
}

private fun ImageBitmap.safeAspectRatio(): Float = if (height > 0) {
    width / height.toFloat()
} else {
    1f
}

private fun Color.distanceTo(
    other: Color,
): Float {
    val redDelta = red - other.red
    val greenDelta = green - other.green
    val blueDelta = blue - other.blue
    return sqrt((redDelta * redDelta) + (greenDelta * greenDelta) + (blueDelta * blueDelta))
}

internal data class ComicPageSpread(
    val id: String,
    val pages: List<ComicPageSpec>,
    val pageIndexes: List<Int>,
    val resumePageIndex: Int,
    val progressLabel: String,
)

internal fun comicGestureSurfaceTag(
    spreadId: String,
): String = "comic-gesture-surface-$spreadId"
