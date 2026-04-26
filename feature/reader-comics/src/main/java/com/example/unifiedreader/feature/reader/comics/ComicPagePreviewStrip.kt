package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val NormalPreviewHeight = 154.dp
private val SelectedPreviewHeight = 176.dp
private val PreviewStripHeight = 214.dp
private val SinglePreviewFallbackWidth = 104.dp
private val SpreadPreviewGap = 5.dp
private val MinPreviewPageWidth = 56.dp
private val MaxPreviewPageWidth = 240.dp
private val MaxWidePreviewPageWidth = 360.dp
private val PreviewOptions = ComicPagePreviewOptions(
    targetLongEdge = 520,
    maxPixelCount = 320_000,
)
private const val PreviewMetadataWarmConcurrency = 2
private const val PreviewMetadataWarmPageLimit = 24
private const val PreviewUnavailableRetryDelayMillis = 300L

@Composable
internal fun ComicPagePreviewStrip(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    currentPage: Int,
    readingDirection: ComicReadingDirection,
    spreadMode: ComicSpreadMode,
    pageSource: ComicPageSource,
    modifier: Modifier = Modifier,
    onPageSelected: (Int) -> Unit,
) {
    if (pages.isEmpty()) return
    val tokens = readerOverlayTokens()
    val listState = rememberLazyListState()
    val safeCurrentPage = currentPage.coerceIn(0, pages.lastIndex)
    var pageMetadataById by remember(item.id, pages) { mutableStateOf<Map<String, ComicPageMetadata>>(emptyMap()) }

    LaunchedEffect(item.id, pages, pageSource, safeCurrentPage) {
        pageSource.prefetchPagePreviews(
            item = item,
            pages = pages,
            options = PreviewOptions,
            priorityPageIndex = safeCurrentPage,
        )
    }

    LaunchedEffect(item.id, pages, pageSource, safeCurrentPage) {
        val metadataSemaphore = Semaphore(PreviewMetadataWarmConcurrency)
        kotlinx.coroutines.coroutineScope {
            pages
                .sortedByDistanceTo(safeCurrentPage)
                .take(PreviewMetadataWarmPageLimit)
                .map { page ->
                    async {
                        metadataSemaphore.withPermit {
                            page.id to pageSource.loadCachedPagePreviewMetadata(
                                item = item,
                                page = page,
                                options = PreviewOptions,
                            )
                        }
                    }
                }
                .forEach { deferred ->
                    val (pageId, metadata) = deferred.await()
                    if (metadata != null && pageMetadataById[pageId] != metadata) {
                        pageMetadataById = pageMetadataById + (pageId to metadata)
                    }
                }
        }
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val useDoublePageSpreads = shouldUseDoublePageSpreads(
            spreadMode = spreadMode,
            maxWidthDp = maxWidth.value,
            maxHeightDp = maxHeight.value,
            pageCount = pages.size,
        )
        val spreads = remember(
            pages,
            readingDirection,
            useDoublePageSpreads,
            pageMetadataById,
        ) {
            buildComicPageSpreads(
                pages = pages,
                readingDirection = readingDirection,
                useDoublePageSpreads = useDoublePageSpreads,
                pageMetadataById = pageMetadataById,
            )
        }
        val targetListIndex = remember(spreads, safeCurrentPage) {
            spreads.indexOfFirst { spread -> safeCurrentPage in spread.pageIndexes }
                .takeIf { it >= 0 }
                ?: safeCurrentPage
        }

        LaunchedEffect(item.id, targetListIndex, spreads.size) {
            val centeredIndex = (targetListIndex - 2).coerceAtLeast(0)
            listState.scrollToItem(centeredIndex)
        }

        LazyRow(
            state = listState,
            reverseLayout = readingDirection == ComicReadingDirection.RTL,
            modifier = Modifier
                .safeGesturesPadding()
                .background(tokens.colors.container.copy(alpha = 0.84f), tokens.shapes.previewStrip)
                .height(PreviewStripHeight)
                .testTag(ComicPagePreviewStripTag),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                items = spreads,
                key = ComicPageSpread::id,
            ) { spread ->
                ComicSpreadPreviewItem(
                    item = item,
                    spread = spread,
                    pageSource = pageSource,
                    selected = safeCurrentPage in spread.pageIndexes,
                    onMetadataLoaded = { pageId, metadata ->
                        if (pageMetadataById[pageId] != metadata) {
                            pageMetadataById = pageMetadataById + (pageId to metadata)
                        }
                    },
                    onClick = { onPageSelected(spread.resumePageIndex) },
                )
            }
        }
    }
}

@Composable
private fun ComicSpreadPreviewItem(
    item: ReadableItem,
    spread: ComicPageSpread,
    pageSource: ComicPageSource,
    selected: Boolean,
    onMetadataLoaded: (String, ComicPageMetadata) -> Unit,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    val spreadPreviewVersion by remember(item.id, spread.id, pageSource) {
        pageSource.observePagePreviewVersion(
            item = item,
            pages = spread.pages.map(ComicSpreadPage::spec),
            options = PreviewOptions,
        )
    }.collectAsState(initial = 0L)
    val previewResults by produceState<Map<String, ComicPagePreviewResult>>(
        initialValue = emptyMap(),
        key1 = item.id,
        key2 = spread.id,
        key3 = pageSource to spreadPreviewVersion,
    ) {
        val loadedState = withContext(Dispatchers.IO) {
            loadSpreadPreviewPages(
                item = item,
                spread = spread,
                pageSource = pageSource,
            )
        }
        loadedState.mapNotNull(LoadedPreviewPage::metadata).forEach { (pageId, metadata) ->
            onMetadataLoaded(pageId, metadata)
        }
        value = loadedState.associate { it.pageId to it.result }
        if (loadedState.any { page -> page.result == ComicPagePreviewResult.Unavailable }) {
            delay(PreviewUnavailableRetryDelayMillis)
            val retryState = withContext(Dispatchers.IO) {
                loadSpreadPreviewPages(
                    item = item,
                    spread = spread,
                    pageSource = pageSource,
                )
            }
            retryState.mapNotNull(LoadedPreviewPage::metadata).forEach { (pageId, metadata) ->
                onMetadataLoaded(pageId, metadata)
            }
            value = retryState.associate { it.pageId to it.result }
        }
    }
    val previewHeight by animateDpAsState(
        targetValue = if (selected) SelectedPreviewHeight else NormalPreviewHeight,
        label = "comic-page-preview-height",
    )
    val itemWidth = previewItemWidth(
        spread = spread,
        previewResults = previewResults,
        previewHeight = previewHeight,
    )
    Column(
        modifier = Modifier.width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(itemWidth)
                .height(previewHeight)
                .clip(tokens.shapes.previewItem)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) tokens.colors.accent else Color.Transparent,
                    shape = tokens.shapes.previewItem,
                )
                .clickable(onClick = onClick)
                .padding(if (selected) 3.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.height(if (selected) previewHeight - 6.dp else previewHeight),
                horizontalArrangement = Arrangement.spacedBy(SpreadPreviewGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                spread.pages.forEach { spreadPage ->
                    val pageHeight = if (selected) previewHeight - 6.dp else previewHeight
                    ComicPagePreviewImage(
                        page = spreadPage.spec,
                        result = previewResults[spreadPage.spec.id] ?: ComicPagePreviewResult.Unavailable,
                        modifier = Modifier
                            .width(
                                previewPageWidth(
                                    spreadPage = spreadPage,
                                    result = previewResults[spreadPage.spec.id],
                                    previewHeight = pageHeight,
                                ),
                            )
                            .height(pageHeight),
                    )
                }
            }
        }
        Text(
            text = spread.previewLabel(),
            color = if (selected) tokens.colors.primaryText else tokens.colors.secondaryText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

private suspend fun loadSpreadPreviewPages(
    item: ReadableItem,
    spread: ComicPageSpread,
    pageSource: ComicPageSource,
): List<LoadedPreviewPage> = coroutineScope {
    spread.pages
        .map(ComicSpreadPage::spec)
        .distinctBy(ComicPageSpec::id)
        .map { page ->
            async {
                val preview = pageSource.loadPagePreview(
                    item = item,
                    page = page,
                    options = PreviewOptions,
                )
                val metadata = (preview as? ComicPagePreviewResult.Success)?.let {
                    page.id to
                        ComicPageMetadata(
                            width = it.image.width,
                            height = it.image.height,
                        )
                }
                LoadedPreviewPage(
                    pageId = page.id,
                    result = preview,
                    metadata = metadata,
                )
            }
        }
        .awaitAll()
}

@Composable
private fun ComicPagePreviewImage(
    page: ComicPageSpec,
    result: ComicPagePreviewResult,
    modifier: Modifier,
) {
    val tokens = readerOverlayTokens()
    when (result) {
        is ComicPagePreviewResult.Success -> Image(
            bitmap = result.image,
            contentDescription = page.contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )

        ComicPagePreviewResult.Unavailable -> Box(
            modifier = modifier
                .clip(tokens.shapes.previewItem)
                .background(tokens.colors.fallbackPage),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (page.pageIndex + 1).toString(),
                color = tokens.colors.tertiaryText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun previewItemWidth(
    spread: ComicPageSpread,
    previewResults: Map<String, ComicPagePreviewResult>,
    previewHeight: Dp,
): Dp = spread.pages.sumOfDp { spreadPage ->
    previewPageWidth(
        spreadPage = spreadPage,
        result = previewResults[spreadPage.spec.id],
        previewHeight = previewHeight,
    )
} + SpreadPreviewGap * (spread.pages.size - 1).coerceAtLeast(0)

internal fun previewPageWidth(
    spreadPage: ComicSpreadPage,
    result: ComicPagePreviewResult?,
    previewHeight: Dp,
): Dp {
    val metadata = spreadPage.metadata
    val aspectRatio: Float? = when {
        metadata != null && metadata.height > 0 -> metadata.width / metadata.height.toFloat()
        result is ComicPagePreviewResult.Success && result.image.height > 0 -> result.image.width / result.image.height.toFloat()
        else -> null
    }
    val width = aspectRatio?.let { ratio -> previewHeight * ratio } ?: SinglePreviewFallbackWidth
    val maxWidth = if (aspectRatio != null && aspectRatio >= ComicPageMetadata.DefaultWidePageAspectRatioThreshold) {
        MaxWidePreviewPageWidth
    } else {
        MaxPreviewPageWidth
    }
    return width
        .coerceAtLeast(MinPreviewPageWidth)
        .coerceAtMost(maxWidth)
}

private inline fun Iterable<ComicSpreadPage>.sumOfDp(
    selector: (ComicSpreadPage) -> Dp,
): Dp {
    var sum = 0.dp
    forEach { page -> sum += selector(page) }
    return sum
}

private fun List<ComicPageSpec>.sortedByDistanceTo(
    pageIndex: Int,
): List<ComicPageSpec> = sortedWith(
    compareBy<ComicPageSpec> { page -> kotlin.math.abs(page.pageIndex - pageIndex) }
        .thenBy(ComicPageSpec::pageIndex),
)

private fun ComicPageSpread.previewLabel(): String {
    val indexes = pageIndexes.distinct().sorted()
    return if (indexes.size == 1) {
        (indexes.first() + 1).toString()
    } else {
        "${indexes.first() + 1}-${indexes.last() + 1}"
    }
}

private data class LoadedPreviewPage(
    val pageId: String,
    val result: ComicPagePreviewResult,
    val metadata: Pair<String, ComicPageMetadata>?,
)

internal const val ComicPagePreviewStripTag = "comic-page-preview-strip"
