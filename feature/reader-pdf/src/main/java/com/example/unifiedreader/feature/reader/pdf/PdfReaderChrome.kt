package com.example.unifiedreader.feature.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.feature.reader.core.ReaderBarButton
import com.example.unifiedreader.feature.reader.core.ReaderChromeDefaults
import com.example.unifiedreader.feature.reader.core.ReaderChromeFrame
import com.example.unifiedreader.feature.reader.core.ReaderDock
import com.example.unifiedreader.feature.reader.core.ReaderDockProgress
import com.example.unifiedreader.feature.reader.core.ReaderTopBar
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The PDF reader on the shared frame: the title over the author with previous and next at the
 * top; the page strip over the percentage, the slider and the page in the dock. It has no panels.
 */
@Composable
internal fun PdfReaderChrome(
    title: String,
    author: String?,
    context: Context,
    documentUriState: ReaderDocumentUriState,
    pageCount: Int,
    currentPageIndex: Int,
    progress: Float,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val thumbnailHeight = ReaderChromeDefaults.stripThumbnailHeight(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
    ReaderChromeFrame(
        panelOpen = false,
        onDismissPanel = {},
        topBar = {
            ReaderTopBar(title = title, subtitle = author, onBack = onBack) {
                ReaderBarButton(
                    icon = AppIcons.NavigateBefore,
                    contentDescription = "Previous page",
                    onClick = onPrevious,
                    enabled = canNavigatePrevious,
                )
                ReaderBarButton(
                    icon = AppIcons.NavigateNext,
                    contentDescription = "Next page",
                    onClick = onNext,
                    enabled = canNavigateNext,
                )
            }
        },
        dock = {
            ReaderDock(
                strip = if (pageCount > 0) {
                    {
                        PdfPagePreviewStrip(
                            context = context,
                            documentUriState = documentUriState,
                            pageCount = pageCount,
                            currentPageIndex = currentPageIndex,
                            thumbnailHeight = thumbnailHeight,
                            onPageSelected = onPageSelected,
                        )
                    }
                } else {
                    null
                },
            ) {
                ReaderDockProgress(
                    progress = progress,
                    percentLabel = "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                    positionLabel = if (pageCount > 0) "${currentPageIndex + 1} / $pageCount" else "",
                    onProgressSelected = { selected ->
                        if (pageCount > 0) {
                            onPageSelected((selected * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1))
                        }
                    },
                )
            }
        },
    ) { }
}

@Composable
private fun PdfPagePreviewStrip(
    context: Context,
    documentUriState: ReaderDocumentUriState,
    pageCount: Int,
    currentPageIndex: Int,
    thumbnailHeight: Dp,
    onPageSelected: (Int) -> Unit,
) {
    if (pageCount <= 0) return
    val documentUri = (documentUriState as? ReaderDocumentUriState.Ready)?.uri ?: return
    val listState = rememberLazyListState()
    val safeCurrentPage = currentPageIndex.coerceIn(0, pageCount - 1)
    val pages = remember(pageCount) { List(pageCount) { it } }
    val previewCache = remember(documentUriState.cacheKey) {
        PdfPreviewBitmapCache(maxEntries = PDF_PREVIEW_CACHE_MAX_ENTRIES)
    }

    DisposableEffect(previewCache) {
        onDispose {
            previewCache.clear()
        }
    }

    LaunchedEffect(documentUriState.cacheKey, safeCurrentPage, pageCount) {
        listState.scrollToItem((safeCurrentPage - 2).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PdfPagePreviewStripTag),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items(
            items = pages,
            key = { pageIndex -> "pdf-page-$pageIndex" },
        ) { pageIndex ->
            PdfPagePreviewItem(
                context = context,
                documentUri = documentUri,
                previewCache = previewCache,
                pageIndex = pageIndex,
                selected = pageIndex == safeCurrentPage,
                thumbnailHeight = thumbnailHeight,
                onClick = { onPageSelected(pageIndex) },
            )
        }
    }
}

@Composable
private fun PdfPagePreviewItem(
    context: Context,
    documentUri: Uri,
    previewCache: PdfPreviewBitmapCache,
    pageIndex: Int,
    selected: Boolean,
    thumbnailHeight: Dp,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    val previewState by produceState<PdfPageRenderState>(
        initialValue = PdfPageRenderState.Loading,
        key1 = documentUri.toString(),
        key2 = pageIndex,
    ) {
        value = withContext(Dispatchers.IO) {
            previewCache.get(pageIndex)
                ?: renderPdfPage(
                    context = context,
                    documentUri = documentUri,
                    pageIndex = pageIndex,
                    targetLongEdgePx = PDF_PREVIEW_RENDER_LONG_EDGE_PX,
                ).also { renderedState ->
                    if (renderedState is PdfPageRenderState.Ready) {
                        previewCache.put(pageIndex, renderedState)
                    }
                }
        }
    }
    val itemWidth = thumbnailHeight * PdfPreviewWidthRatio
    Column(
        modifier = Modifier.width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(itemWidth)
                .height(thumbnailHeight)
                .clip(tokens.shapes.previewItem)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) tokens.colors.accent else Color.Transparent,
                    shape = tokens.shapes.previewItem,
                )
                .padding(if (selected) 2.dp else 0.dp)
                .background(tokens.colors.fallbackPage, tokens.shapes.previewItem)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = previewState) {
                PdfPageRenderState.Empty -> PdfPreviewFallback(pageIndex = pageIndex)
                PdfPageRenderState.Loading -> AppCircularProgress(
                    color = tokens.colors.accent,
                )
                is PdfPageRenderState.Error -> PdfPreviewFallback(pageIndex = pageIndex)
                is PdfPageRenderState.Ready -> {
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = state.contentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) tokens.colors.primaryText else tokens.colors.secondaryText,
            maxLines = 1,
        )
    }
}

@Composable
private fun PdfPreviewFallback(pageIndex: Int) {
    val tokens = readerOverlayTokens()
    Text(
        text = "${pageIndex + 1}",
        style = MaterialTheme.typography.labelLarge,
        color = tokens.colors.tertiaryText,
        textAlign = TextAlign.Center,
    )
}

// A thumbnail's width against its height: about a page's shape.
private const val PdfPreviewWidthRatio = 0.73f
private const val PdfPagePreviewStripTag = "pdf-page-preview-strip"
private const val PDF_PREVIEW_CACHE_MAX_ENTRIES = 64
private const val PDF_PREVIEW_RENDER_LONG_EDGE_PX = 420
