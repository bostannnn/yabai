package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.core.model.ReadableItem
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal const val SpreadModeAutoMinWidthDp = 700

@Composable
internal fun ComicSpreadPager(
    item: ReadableItem,
    spreads: List<ComicPageSpread>,
    currentPage: Int,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    isZoomed: Boolean,
    userScrollEnabled: Boolean = true,
    pageSource: ComicPageSource,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onZoomStateChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePrevious: (Int) -> Unit,
    onNavigateNext: (Int) -> Unit,
    onVisiblePagesLoaded: (Collection<Int>) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled && !isZoomed,
        contentPadding = PaddingValues(0.dp),
        pageSpacing = 0.dp,
        reverseLayout = readingDirection == ComicReadingDirection.RTL,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .comicTerminalSwipeNavigation(
                isAtLastSpread = pagerState.currentPage >= spreads.lastIndex,
                isZoomed = isZoomed,
                readingDirection = readingDirection,
                onNavigatePastEnd = { onNavigateNext(spreads.lastIndex) },
            ),
    ) { spreadIndex ->
        val spread = spreads[spreadIndex]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            ComicSpreadSurface(
                item = item,
                spread = spread,
                pageSource = pageSource,
                isActiveSpread = spread.pageIndexes.contains(currentPage),
                readingDirection = readingDirection,
                pageFitMode = pageFitMode,
                onZoomStateChanged = onZoomStateChanged,
                onInteraction = onInteraction,
                onToggleOverlay = onToggleOverlay,
                onNavigatePrevious = { onNavigatePrevious(spreadIndex) },
                onNavigateNext = { onNavigateNext(spreadIndex) },
                onVisiblePagesLoaded = onVisiblePagesLoaded,
            )
        }
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
    onZoomStateChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onVisiblePagesLoaded: (Collection<Int>) -> Unit,
) {
    val viewportState = rememberComicPageViewportState(pageId = spread.id)
    val animationScope = rememberCoroutineScope()
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    var containerSize by remember(spread.id) { mutableStateOf(IntSize.Zero) }
    val pageLoadResults by produceState<Map<String, ComicPageLoadResult?>>(
        initialValue = emptyMap(),
        key1 = item.id,
        key2 = spread.id,
    ) {
        value = coroutineScope {
            spread.pages
                .map(ComicSpreadPage::spec)
                .distinctBy(ComicPageSpec::id)
                .map { page ->
                    async {
                        page.id to pageSource.loadPageImage(
                            item = item,
                            page = page,
                            priority = ComicPageLoadPriority.Visible,
                        )
                    }
                }
                .awaitAll()
                .toMap()
        }
    }
    val imageSize = remember(pageLoadResults, spread.pages) {
        comicSpreadCompositeImageSize(
            spread = spread,
            imageSizesByPageId = pageLoadResults.successImageSizesByPageId(),
        )
    }

    LaunchedEffect(pageFitMode, containerSize, imageSize) {
        viewportState.reconcileLayout(
            containerSize = containerSize,
            imageSize = imageSize,
            fitMode = pageFitMode,
        )
    }
    LaunchedEffect(isActiveSpread, viewportState.isZoomed) {
        if (isActiveSpread) {
            onZoomStateChanged(viewportState.isZoomed)
        }
    }
    LaunchedEffect(isActiveSpread, spread.id, pageLoadResults) {
        if (!isActiveSpread) return@LaunchedEffect
        if (spread.pages.all { page -> pageLoadResults[page.spec.id] != null }) {
            onVisiblePagesLoaded(spread.pageIndexes)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { containerSize = it },
            color = ComicReaderBackgroundColor,
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
                    animationScope = animationScope,
                    nestedScrollDispatcher = nestedScrollDispatcher,
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
    val loadedImageSizesByPageId = remember(loadedImagesByPageId) {
        loadedImagesByPageId.imageSizesByPageId()
    }
    val useFitSpread = isDoublePage && pageFitMode == ComicPageFitMode.FitPage && spread.pages.all { it.segment == ComicPageSegment.Full }
    val likelyStitchedSpread = remember(loadedImagesByPageId, spread.pages) {
        spread.isLikelyStitchedSpread(loadedImagesByPageId)
    }
    if (useFitSpread) {
        FitSpreadContent(
            spread = spread,
            pageLoadResults = pageLoadResults,
            loadedImagesByPageId = loadedImagesByPageId,
            stitchedOverlap = if (likelyStitchedSpread) ComicStitchedSpreadOverlap else 0.dp,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = if (isDoublePage) Arrangement.spacedBy(0.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        spread.pages.forEachIndexed { index, page ->
            Box(
                modifier = Modifier
                    .then(
                        if (isDoublePage) {
                            Modifier
                                .weight(page.layoutWeightForSourceSize(loadedImageSizesByPageId[page.spec.id]))
                                .fillMaxHeight()
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                    .then(
                        if (likelyStitchedSpread && index > 0) {
                            Modifier.offset(x = -ComicStitchedSpreadOverlap)
                        } else {
                            Modifier
                        },
                    )
                    .background(ComicPageBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                PageResultContent(
                    page = page,
                    result = pageLoadResults[page.spec.id],
                    pageFitMode = pageFitMode,
                )
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
    val pageAspectRatios = remember(loadedImagesByPageId, spread.pages) {
        val loadedImageSizesByPageId = loadedImagesByPageId.imageSizesByPageId()
        spread.pages.map { page ->
            page.layoutWeightForSourceSize(loadedImageSizesByPageId[page.spec.id])
        }
    }
    val compositeSize = remember(pageLoadResults, spread.pages) {
        comicSpreadCompositeImageSize(
            spread = spread,
            imageSizesByPageId = pageLoadResults.successImageSizesByPageId(),
        )
    }
    val combinedAspectRatio = remember(compositeSize, pageAspectRatios) {
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
                        .background(ComicPageBackgroundColor),
                    contentAlignment = Alignment.Center,
                ) {
                    PageResultContent(
                        page = page,
                        result = pageLoadResults[page.spec.id],
                        pageFitMode = ComicPageFitMode.FitPage,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageResultContent(
    page: ComicSpreadPage,
    result: ComicPageLoadResult?,
    pageFitMode: ComicPageFitMode,
) {
    when (result) {
        is ComicPageLoadResult.Success -> ComicPageBitmap(
            bitmap = result.image,
            page = page,
            pageFitMode = pageFitMode,
        )

        is ComicPageLoadResult.Failure -> ReaderPageMessage(message = result.message)
        null -> ReaderPageMessage(message = "Loading page...")
    }
}

@Composable
private fun ComicPageBitmap(
    bitmap: ImageBitmap,
    page: ComicSpreadPage,
    pageFitMode: ComicPageFitMode,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = page.contentDescription
            },
    ) {
        val crop = page.segment.sourceWindow(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
        )
        val widthScale = size.width / crop.width.toFloat()
        val heightScale = size.height / crop.height.toFloat()
        val drawScale = when (pageFitMode) {
            ComicPageFitMode.FitPage -> minOf(widthScale, heightScale)
            ComicPageFitMode.FitWidth -> widthScale
            ComicPageFitMode.FitHeight -> heightScale
        }
        val drawWidth = max(1, (crop.width * drawScale).roundToInt())
        val drawHeight = max(1, (crop.height * drawScale).roundToInt())
        val drawOffset = IntOffset(
            x = ((size.width - drawWidth) / 2f).roundToInt(),
            y = ((size.height - drawHeight) / 2f).roundToInt(),
        )
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(crop.offsetX, 0),
            srcSize = IntSize(crop.width, crop.height),
            dstOffset = drawOffset,
            dstSize = IntSize(drawWidth, drawHeight),
            filterQuality = FilterQuality.High,
        )
    }
}

@Composable
internal fun ReaderPageMessage(
    message: String,
) {
    val tokens = readerOverlayTokens()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.fallbackPage),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.tertiaryText,
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
    pageMetadataById: Map<String, ComicPageMetadata>,
): List<ComicPageSpread> {
    if (pages.isEmpty()) return emptyList()
    return if (useDoublePageSpreads) {
        buildDoublePageSpreads(
            pages = pages,
            readingDirection = readingDirection,
            pageMetadataById = pageMetadataById,
        )
    } else {
        buildSinglePageSpreads(
            pages = pages,
            pageMetadataById = pageMetadataById,
        )
    }
}

private fun buildSinglePageSpreads(
    pages: List<ComicPageSpec>,
    pageMetadataById: Map<String, ComicPageMetadata>,
): List<ComicPageSpread> = buildList {
    pages.forEach { page ->
        val metadata = pageMetadataById[page.id]
        add(
            ComicPageSpread(
                id = "spread-${page.pageIndex}-${ComicPageSegment.Full.idSuffix}",
                pages = listOf(
                    ComicSpreadPage(
                        spec = page,
                        segment = ComicPageSegment.Full,
                        metadata = metadata,
                    ),
                ),
                pageIndexes = listOf(page.pageIndex),
                resumePageIndex = page.pageIndex,
                progressLabel = "${page.pageIndex + 1} / ${pages.size}",
            ),
        )
    }
}

private fun buildDoublePageSpreads(
    pages: List<ComicPageSpec>,
    readingDirection: ComicReadingDirection,
    pageMetadataById: Map<String, ComicPageMetadata>,
): List<ComicPageSpread> {
    val spreads = mutableListOf<ComicPageSpread>()
    spreads += soloComicPageSpread(
        page = pages.first(),
        pageCount = pages.size,
        metadata = pageMetadataById[pages.first().id],
        id = "spread-cover",
    )
    var pageIndex = 1
    while (pageIndex < pages.size) {
        val leftPage = pages[pageIndex]
        val leftMetadata = pageMetadataById[leftPage.id]
        if (leftMetadata?.isWide() == true) {
            spreads += soloComicPageSpread(
                page = leftPage,
                pageCount = pages.size,
                metadata = leftMetadata,
            )
            pageIndex += 1
            continue
        }

        val rightPage = pages.getOrNull(pageIndex + 1)
        if (rightPage == null) {
            spreads += soloComicPageSpread(
                page = leftPage,
                pageCount = pages.size,
                metadata = leftMetadata,
            )
            break
        }
        val rightMetadata = pageMetadataById[rightPage.id]
        if (rightMetadata?.isWide() == true) {
            spreads += soloComicPageSpread(
                page = leftPage,
                pageCount = pages.size,
                metadata = leftMetadata,
            )
            pageIndex += 1
        } else {
            spreads += pairedComicPageSpread(
                leftPage = leftPage,
                rightPage = rightPage,
                pageCount = pages.size,
                readingDirection = readingDirection,
                pageMetadataById = pageMetadataById,
            )
            pageIndex += 2
        }
    }
    return spreads
}

private fun soloComicPageSpread(
    page: ComicPageSpec,
    pageCount: Int,
    metadata: ComicPageMetadata?,
    id: String = "spread-${page.pageIndex}",
): ComicPageSpread = ComicPageSpread(
    id = id,
    pages = listOf(
        ComicSpreadPage(
            spec = page,
            metadata = metadata,
        ),
    ),
    pageIndexes = listOf(page.pageIndex),
    resumePageIndex = page.pageIndex,
    progressLabel = "${page.pageIndex + 1} / $pageCount",
)

private fun pairedComicPageSpread(
    leftPage: ComicPageSpec,
    rightPage: ComicPageSpec,
    pageCount: Int,
    readingDirection: ComicReadingDirection,
    pageMetadataById: Map<String, ComicPageMetadata>,
): ComicPageSpread {
    val pair = when (readingDirection) {
        ComicReadingDirection.LTR -> listOf(leftPage, rightPage)
        ComicReadingDirection.RTL -> listOf(rightPage, leftPage)
    }.map { page ->
        ComicSpreadPage(
            spec = page,
            metadata = pageMetadataById[page.id],
        )
    }
    return ComicPageSpread(
        id = "spread-${leftPage.pageIndex}",
        pages = pair,
        pageIndexes = pair.map(ComicSpreadPage::pageIndex),
        resumePageIndex = leftPage.pageIndex,
        progressLabel = "${leftPage.pageIndex + 1}-${rightPage.pageIndex + 1} / $pageCount",
    )
}

internal fun List<ComicPageSpread>.indexOfPage(
    pageIndex: Int,
    segment: ComicPageSegment? = null,
): Int {
    if (segment != null) {
        indexOfFirst { spread -> spread.matchesPosition(pageIndex, segment) }
            .takeIf { it >= 0 }
            ?.let { return it }
    }
    return indexOfFirst { spread -> pageIndex in spread.pageIndexes }
        .takeIf { it >= 0 }
        ?: 0
}

private val ComicPageSpread.isSoloCover: Boolean
    get() = pageIndexes == listOf(0)

private fun Map<String, ComicPageLoadResult?>.successImageSizesByPageId(): Map<String, IntSize> =
    mapNotNull { (pageId, result) ->
        val image = (result as? ComicPageLoadResult.Success)?.image ?: return@mapNotNull null
        pageId to IntSize(image.width, image.height)
    }.toMap()

private fun Map<String, ImageBitmap?>.imageSizesByPageId(): Map<String, IntSize> =
    mapNotNull { (pageId, image) ->
        image ?: return@mapNotNull null
        pageId to IntSize(image.width, image.height)
    }.toMap()

enum class ComicPageSegment(
    val idSuffix: String,
    val widthFraction: Float,
) {
    Full("full", 1f),
    LeftHalf("left", 0.5f),
    RightHalf("right", 0.5f);

    internal fun sourceWindow(
        imageWidth: Int,
        imageHeight: Int,
    ): ComicPageSourceWindow = when (this) {
        Full -> ComicPageSourceWindow(
            offsetX = 0,
            width = imageWidth.coerceAtLeast(1),
            height = imageHeight.coerceAtLeast(1),
        )

        LeftHalf -> ComicPageSourceWindow(
            offsetX = 0,
            width = max(1, imageWidth - rightHalfWidth(imageWidth)),
            height = imageHeight.coerceAtLeast(1),
        )

        RightHalf -> {
            val halfWidth = rightHalfWidth(imageWidth)
            ComicPageSourceWindow(
                offsetX = (imageWidth - halfWidth).coerceAtLeast(0),
                width = halfWidth,
                height = imageHeight.coerceAtLeast(1),
            )
        }
    }

    private fun rightHalfWidth(
        imageWidth: Int,
    ): Int = max(1, imageWidth / 2)
}

internal data class ComicPageSourceWindow(
    val offsetX: Int,
    val width: Int,
    val height: Int,
)

internal data class ComicSpreadPage(
    val spec: ComicPageSpec,
    val segment: ComicPageSegment = ComicPageSegment.Full,
    val metadata: ComicPageMetadata? = null,
) {
    val pageIndex: Int
        get() = spec.pageIndex

    val contentDescription: String
        get() = when (segment) {
            ComicPageSegment.Full -> spec.contentDescription
            ComicPageSegment.LeftHalf -> "${spec.contentDescription} (left half)"
            ComicPageSegment.RightHalf -> "${spec.contentDescription} (right half)"
        }
}

internal data class ComicPageSpread(
    val id: String,
    val pages: List<ComicSpreadPage>,
    val pageIndexes: List<Int>,
    val resumePageIndex: Int,
    val progressLabel: String,
) {
    val resumeSegment: ComicPageSegment?
        get() = pages.singleOrNull()
            ?.takeIf { it.pageIndex == resumePageIndex && it.segment != ComicPageSegment.Full }
            ?.segment

    fun matchesPosition(
        pageIndex: Int,
        segment: ComicPageSegment?,
    ): Boolean = pageIndex in pageIndexes && (segment == null || resumeSegment == segment)
}

internal fun comicGestureSurfaceTag(
    spreadId: String,
): String = "comic-gesture-surface-$spreadId"
