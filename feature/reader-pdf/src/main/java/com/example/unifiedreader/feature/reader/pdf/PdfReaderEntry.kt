package com.example.unifiedreader.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderActiveSessionTracker
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlaySurfaceRole
import com.example.unifiedreader.feature.reader.core.ReaderBottomProgressBar
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReaderZoomViewportState
import com.example.unifiedreader.feature.reader.core.ReaderZoomLayout
import com.example.unifiedreader.feature.reader.core.detectReaderZoomGestures
import com.example.unifiedreader.feature.reader.core.readerOverlaySurface
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.math.roundToInt

@Composable
fun PdfReaderScreen(
    item: ReadableItem,
    documentUri: Uri? = null,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)? = null,
    sessionStore: ReaderSessionStore? = null,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    openMode: ReaderOpenMode = ReaderOpenMode.Default,
    onBack: () -> Unit = {},
    onCompletionRequested: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val tokens = readerOverlayTokens()
    val overlayState = rememberReaderOverlayState(
        key = "pdf-overlay-${item.id}",
        initiallyVisible = true,
    )
    val sessionHydrated by sessionStore?.isHydratedFlow?.collectAsState()
        ?: remember { mutableStateOf(true) }
    val savedProgressByItemId by sessionStore?.progressByItemIdFlow?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }
    val documentUriState by rememberDocumentUriState(
        item = item,
        documentUri = documentUri,
        documentUriProvider = documentUriProvider,
    )
    val documentInfo by produceState<PdfDocumentInfoState>(
        initialValue = PdfDocumentInfoState.Loading,
        key1 = documentUriState.cacheKey,
    ) {
        value = when (val uriState = documentUriState) {
            ReaderDocumentUriState.Loading -> PdfDocumentInfoState.Loading
            is ReaderDocumentUriState.Error -> PdfDocumentInfoState.Error(uriState.message)
            is ReaderDocumentUriState.Ready -> uriState.uri?.let { uri ->
                withContext(Dispatchers.IO) {
                    inspectPdfDocument(
                        context = context.applicationContext,
                        documentUri = uri,
                    )
                }
            } ?: PdfDocumentInfoState.Empty
        }
    }
    val pageCount = (documentInfo as? PdfDocumentInfoState.Ready)?.pageCount ?: 0
    var currentPageIndex by remember(item.id, documentUriState.cacheKey) {
        mutableIntStateOf(0)
    }
    var renderLongEdgePx by remember(item.id, documentUriState.cacheKey, currentPageIndex) {
        mutableIntStateOf(PDF_RENDER_LONG_EDGE_PX)
    }
    var initialRestoreCompleted by remember(item.id, documentUriState.cacheKey, openMode) {
        mutableStateOf(false)
    }
    var userNavigated by remember(item.id, documentUriState.cacheKey, openMode) {
        mutableStateOf(false)
    }
    val restoredProgress = savedProgressByItemId[item.id]
    LaunchedEffect(
        item.id,
        documentUriState.cacheKey,
        pageCount,
        openMode,
        sessionHydrated,
        restoredProgress,
    ) {
        if (initialRestoreCompleted) return@LaunchedEffect
        if (!sessionHydrated) return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        if (userNavigated) {
            initialRestoreCompleted = true
            return@LaunchedEffect
        }
        currentPageIndex = initialPdfPageIndex(
            savedProgress = restoredProgress ?: sessionStore?.getProgress(item),
            pageCount = pageCount,
            openMode = openMode,
        )
        initialRestoreCompleted = true
    }
    val pageState by produceState<PdfPageRenderState>(
        initialValue = PdfPageRenderState.Loading,
        documentUriState.cacheKey,
        currentPageIndex,
        pageCount,
        renderLongEdgePx,
    ) {
        val uri = (documentUriState as? ReaderDocumentUriState.Ready)?.uri
        val targetPageIndex = currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val renderedState = when {
            uri == null -> PdfPageRenderState.Empty
            pageCount <= 0 -> PdfPageRenderState.Empty
            else -> withContext(Dispatchers.IO) {
                renderPdfPage(
                    context = context.applicationContext,
                    documentUri = uri,
                    pageIndex = targetPageIndex,
                    targetLongEdgePx = renderLongEdgePx,
                )
            }
        }
        val previousReadyPage = value as? PdfPageRenderState.Ready
        if (
            renderedState is PdfPageRenderState.Error &&
            previousReadyPage?.pageIndex == targetPageIndex
        ) {
            return@produceState
        }
        value = renderedState
    }
    val tracker = remember(item.id, journalReadingSessionRecorder) {
        journalReadingSessionRecorder?.let { recorder ->
            ReaderActiveSessionTracker(
                item = item,
                recorder = recorder,
                scope = scope,
            )
        }
    }
    LaunchedEffect(pageCount, initialRestoreCompleted) {
        if (pageCount > 0 && initialRestoreCompleted) {
            tracker?.start(
                page = currentPageIndex + 1,
                percent = (currentPageIndex + 1).toDouble() / pageCount.toDouble(),
            )
        }
    }
    LaunchedEffect(item.id, currentPageIndex, pageCount, initialRestoreCompleted, userNavigated) {
        if (!shouldPersistPdfProgress(
                pageCount = pageCount,
                initialRestoreCompleted = initialRestoreCompleted,
                userNavigated = userNavigated,
            )
        ) {
            return@LaunchedEffect
        }
        sessionStore?.savePdfProgress(
            item = item,
            pageIndex = currentPageIndex,
            pageCount = pageCount,
        )
    }
    val latestPageIndex by rememberUpdatedState(currentPageIndex)
    val latestPageCount by rememberUpdatedState(pageCount)
    val latestInitialRestoreCompleted by rememberUpdatedState(initialRestoreCompleted)
    val flushReadingSession by rememberUpdatedState(
        newValue = {
            if (latestPageCount > 0) {
                tracker?.flushAndReset(
                    page = latestPageIndex + 1,
                    percent = (latestPageIndex + 1).toDouble() / latestPageCount.toDouble(),
                    pageCount = latestPageCount,
                )
            }
        },
    )
    val persistPdfProgress by rememberUpdatedState(
        newValue = {
            if (shouldPersistPdfProgress(
                    pageCount = latestPageCount,
                    initialRestoreCompleted = latestInitialRestoreCompleted,
                    userNavigated = userNavigated,
                )
            ) {
                sessionStore?.savePdfProgress(
                    item = item,
                    pageIndex = latestPageIndex,
                    pageCount = latestPageCount,
                )
            }
        },
    )
    val flushSessionStoreWrites by rememberUpdatedState(
        newValue = {
            runBlocking {
                sessionStore?.flushPendingWrites()
            }
        },
    )
    val closeReader by rememberUpdatedState(
        newValue = {
            flushReadingSession()
            persistPdfProgress()
            flushSessionStoreWrites()
            onBack()
        },
    )
    val startReadingSession by rememberUpdatedState(
        newValue = {
            if (latestPageCount > 0 && latestInitialRestoreCompleted) {
                tracker?.start(
                    page = latestPageIndex + 1,
                    percent = (latestPageIndex + 1).toDouble() / latestPageCount.toDouble(),
                )
            }
        },
    )
    DisposableEffect(lifecycleOwner, item.id, tracker) {
        startReadingSession()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startReadingSession()
                Lifecycle.Event.ON_STOP -> {
                    flushReadingSession()
                    persistPdfProgress()
                    flushSessionStoreWrites()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushReadingSession()
            persistPdfProgress()
            flushSessionStoreWrites()
        }
    }
    BackHandler(onBack = closeReader)

    val safePageIndex = currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val currentPageProgress = if (pageCount > 0) {
        (safePageIndex + 1).toFloat() / pageCount.toFloat()
    } else {
        0f
    }
    val navigateToPage: (Int) -> Unit = { targetPage ->
        if (pageCount > 0) {
            val safeTargetPage = targetPage.coerceIn(0, pageCount - 1)
            if (safeTargetPage != currentPageIndex) {
                userNavigated = true
                currentPageIndex = safeTargetPage
            }
        }
    }
    val navigatePrevious = {
        navigateToPage(currentPageIndex - 1)
    }
    val navigateNext = {
        if (pageCount > 0 && currentPageIndex >= pageCount - 1) {
            userNavigated = true
            sessionStore?.savePdfProgress(
                item = item,
                pageIndex = currentPageIndex,
                pageCount = pageCount,
                isFinished = true,
            )
            flushSessionStoreWrites()
            flushReadingSession()
            onCompletionRequested()
        } else {
            navigateToPage(currentPageIndex + 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.fallbackPage),
    ) {
        ImmersiveReaderOverlay(
            overlayState = overlayState,
            autoHideDelayMillis = 0L,
            modifier = Modifier.fillMaxSize(),
            content = {
                PdfPageViewport(
                    pageState = pageState,
                    modifier = Modifier.fillMaxSize(),
                    onToggleOverlay = overlayState::toggle,
                    onPrevious = navigatePrevious,
                    onNext = navigateNext,
                    onRenderLongEdgeRequested = { pageIndex, requestedLongEdge ->
                        if (pageIndex == currentPageIndex && requestedLongEdge > renderLongEdgePx) {
                            renderLongEdgePx = requestedLongEdge
                        }
                    },
                )
            },
            overlay = {
                Box(modifier = Modifier.fillMaxSize()) {
                    PdfReaderOverlayBar(
                        title = item.title,
                        progressLabel = pdfProgressLabel(
                            currentPageIndex = safePageIndex,
                            pageCount = pageCount,
                        ),
                        canNavigatePrevious = pageCount > 0 && currentPageIndex > 0,
                        canNavigateNext = pageCount > 0,
                        onBack = closeReader,
                        onPrevious = navigatePrevious,
                        onNext = navigateNext,
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeGestures.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                    ) {
                        PdfPagePreviewStrip(
                            context = context.applicationContext,
                            documentUriState = documentUriState,
                            pageCount = pageCount,
                            currentPageIndex = safePageIndex,
                            onPageSelected = navigateToPage,
                        )
                        Spacer(modifier = Modifier.height(tokens.spacing.smallGap))
                        ReaderBottomProgressBar(
                            progress = currentPageProgress,
                            percentLabel = "${(currentPageProgress * 100f).roundToInt()}%",
                            pageLabel = if (pageCount > 0) "${safePageIndex + 1}/$pageCount" else "0/0",
                            applySafeGesturesPadding = false,
                            onProgressSelected = { selectedProgress ->
                                if (pageCount > 0) {
                                    navigateToPage(
                                        (selectedProgress * (pageCount - 1))
                                            .roundToInt()
                                            .coerceIn(0, pageCount - 1),
                                    )
                                }
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun rememberDocumentUriState(
    item: ReadableItem,
    documentUri: Uri?,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)?,
) = produceState<ReaderDocumentUriState>(
    initialValue = if (documentUri != null || documentUriProvider == null) {
        ReaderDocumentUriState.Ready(documentUri)
    } else {
        ReaderDocumentUriState.Loading
    },
    key1 = item.id,
    key2 = documentUri?.toString(),
    key3 = documentUriProvider,
) {
    if (documentUri != null || documentUriProvider == null) {
        value = ReaderDocumentUriState.Ready(documentUri)
    } else {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ReaderDocumentUriState.Ready(documentUriProvider(item))
            }.getOrElse { throwable ->
                ReaderDocumentUriState.Error(
                    throwable.message ?: "Failed to prepare PDF content.",
                )
            }
        }
    }
}

@Composable
private fun PdfReaderOverlayBar(
    title: String,
    progressLabel: String,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeGestures.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
    ) {
        CompactReaderOverlayFrame(
            title = title,
            progressLabel = progressLabel,
            applySafeGesturesPadding = false,
            bottomPadding = 0.dp,
            leadingActions = {
                ReaderOverlayBackButton(onClick = onBack)
            },
            trailingActions = {
                ReaderOverlayIconButton(
                    onClick = onPrevious,
                    isActive = false,
                    enabled = canNavigatePrevious,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous page",
                        tint = if (canNavigatePrevious) {
                            tokens.colors.primaryText
                        } else {
                            tokens.colors.tertiaryText
                        },
                    )
                }
                ReaderOverlayIconButton(
                    onClick = onNext,
                    isActive = false,
                    enabled = canNavigateNext,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next page",
                        tint = if (canNavigateNext) {
                            tokens.colors.primaryText
                        } else {
                            tokens.colors.tertiaryText
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun PdfPagePreviewStrip(
    context: Context,
    documentUriState: ReaderDocumentUriState,
    pageCount: Int,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
) {
    if (pageCount <= 0) return
    val documentUri = (documentUriState as? ReaderDocumentUriState.Ready)?.uri ?: return
    val tokens = readerOverlayTokens()
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
            .padding(horizontal = 10.dp)
            .readerOverlaySurface(
                role = ReaderOverlaySurfaceRole.PreviewStrip,
                containerColor = tokens.colors.container.copy(alpha = 0.84f),
            )
            .height(PdfPreviewStripHeight)
            .testTag(PdfPagePreviewStripTag),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
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
    Column(
        modifier = Modifier.width(PdfPreviewItemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .width(PdfPreviewItemWidth)
                .height(if (selected) PdfSelectedPreviewHeight else PdfNormalPreviewHeight)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) tokens.colors.accent else Color.Transparent,
                    shape = tokens.shapes.previewItem,
                )
                .padding(if (selected) 3.dp else 0.dp)
                .background(tokens.colors.fallbackPage, tokens.shapes.previewItem)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = previewState) {
                PdfPageRenderState.Empty -> PdfPreviewFallback(pageIndex = pageIndex)
                PdfPageRenderState.Loading -> CircularProgressIndicator(
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
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) tokens.colors.primaryText else tokens.colors.tertiaryText,
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

@Composable
private fun PdfPageViewport(
    pageState: PdfPageRenderState,
    modifier: Modifier = Modifier,
    onToggleOverlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRenderLongEdgeRequested: (pageIndex: Int, longEdgePx: Int) -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewportState = remember(pageState.cacheKey) {
        ReaderZoomViewportState(maximumScale = 5f)
    }
    var viewportSize by remember(pageState.cacheKey) { mutableStateOf(IntSize.Zero) }
    val imageSize = when (pageState) {
        is PdfPageRenderState.Ready -> IntSize(pageState.bitmap.width, pageState.bitmap.height)
        else -> IntSize.Zero
    }
    val contentPaddingPx = with(density) { 12.dp.toPx() }
    val zoomLayout = remember(viewportSize, imageSize, contentPaddingPx) {
        pdfPageZoomLayout(
            viewportSize = viewportSize,
            imageSize = imageSize,
            contentPaddingPx = contentPaddingPx,
        )
    }
    LaunchedEffect(zoomLayout) {
        viewportState.updateLayout(zoomLayout)
    }
    LaunchedEffect(pageState.cacheKey, zoomLayout, viewportState.scale) {
        val readyPage = pageState as? PdfPageRenderState.Ready ?: return@LaunchedEffect
        if (!zoomLayout.isReady) return@LaunchedEffect
        delay(PDF_RENDER_REQUEST_DEBOUNCE_MILLIS)
        val requiredLongEdge = (
            maxOf(zoomLayout.contentSize.width, zoomLayout.contentSize.height) *
                viewportState.scale
            ).roundToInt()
        onRenderLongEdgeRequested(
            readyPage.pageIndex,
            pdfRenderLongEdgeBucket(requiredLongEdge),
        )
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clipToBounds()
            .onSizeChanged { viewportSize = it }
            .pointerInput(pageState.cacheKey, viewportSize) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        when {
                            viewportState.isZoomed -> onToggleOverlay()
                            tapOffset.x < size.width * PDF_EDGE_TAP_FRACTION -> onPrevious()
                            tapOffset.x > size.width * (1f - PDF_EDGE_TAP_FRACTION) -> onNext()
                            else -> onToggleOverlay()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        viewportState.animateZoomToggle(
                            coroutineScope = animationScope,
                            focalPoint = tapOffset,
                        )
                    },
                )
            }
            .pointerInput(pageState.cacheKey, viewportSize) {
                detectReaderZoomGestures(
                    viewportCenter = { viewportSize.center },
                    canPan = viewportState::canPan,
                    onPointerDown = viewportState::stopAnimation,
                    onTransformStart = {},
                    onPan = viewportState::onPan,
                    onTransform = { centroid, panChange, zoomChange ->
                        viewportState.onGesture(
                            centroid = centroid,
                            zoomChange = zoomChange,
                            panChange = panChange,
                        )
                    },
                    onTransformEnd = { velocity, _ ->
                        viewportState.launchFling(
                            coroutineScope = animationScope,
                            initialVelocity = velocity,
                            density = this,
                        )
                    },
                )
            }
            .semantics {
                stateDescription = "${(viewportState.scale * 100f).roundToInt()}% zoom"
                customActions = listOf(
                    CustomAccessibilityAction(label = "Zoom in") {
                        viewportState.animateZoomBy(
                            coroutineScope = animationScope,
                            zoomFactor = 1.5f,
                            focalPoint = viewportSize.center,
                        )
                        true
                    },
                    CustomAccessibilityAction(label = "Zoom out") {
                        viewportState.animateZoomBy(
                            coroutineScope = animationScope,
                            zoomFactor = 1f / 1.5f,
                            focalPoint = viewportSize.center,
                        )
                        true
                    },
                    CustomAccessibilityAction(label = "Reset zoom") {
                        viewportState.animateReset(
                            coroutineScope = animationScope,
                        )
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (pageState) {
            PdfPageRenderState.Empty -> PdfStatus("No PDF document available.")
            PdfPageRenderState.Loading -> CircularProgressIndicator()
            is PdfPageRenderState.Error -> PdfStatus(pageState.message)
            is PdfPageRenderState.Ready -> {
                val bitmap = pageState.bitmap
                DisposableEffect(bitmap) {
                    onDispose {
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = pageState.contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .graphicsLayer {
                            scaleX = viewportState.scale
                            scaleY = viewportState.scale
                            translationX = viewportState.offset.x
                            translationY = viewportState.offset.y
                        },
                )
            }
        }
    }
}

internal fun pdfPageZoomLayout(
    viewportSize: IntSize,
    imageSize: IntSize,
    contentPaddingPx: Float,
): ReaderZoomLayout {
    if (
        viewportSize.width <= 0 || viewportSize.height <= 0 ||
        imageSize.width <= 0 || imageSize.height <= 0
    ) {
        return ReaderZoomLayout.Empty
    }
    val availableWidth = (viewportSize.width - contentPaddingPx * 2f).coerceAtLeast(1f)
    val availableHeight = (viewportSize.height - contentPaddingPx * 2f).coerceAtLeast(1f)
    val baseScale = minOf(
        availableWidth / imageSize.width,
        availableHeight / imageSize.height,
    )
    return ReaderZoomLayout(
        viewportSize = viewportSize,
        contentSize = Size(
            width = imageSize.width * baseScale,
            height = imageSize.height * baseScale,
        ),
    )
}

internal fun pdfRenderLongEdgeBucket(requiredLongEdgePx: Int): Int = when {
    requiredLongEdgePx <= PDF_RENDER_LONG_EDGE_PX -> PDF_RENDER_LONG_EDGE_PX
    requiredLongEdgePx <= PDF_RENDER_MEDIUM_LONG_EDGE_PX -> PDF_RENDER_MEDIUM_LONG_EDGE_PX
    else -> PDF_RENDER_MAX_LONG_EDGE_PX
}

private val IntSize.center: Offset
    get() = Offset(width / 2f, height / 2f)

@Composable
private fun PdfStatus(
    message: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.width(1.dp))
    }
}

private suspend fun inspectPdfDocument(
    context: Context,
    documentUri: Uri,
): PdfDocumentInfoState = runCatching {
    context.contentResolver.openFileDescriptor(documentUri, "r")?.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val pageCount = renderer.pageCount
            if (pageCount <= 0) {
                PdfDocumentInfoState.Error("This PDF has no pages.")
            } else {
                PdfDocumentInfoState.Ready(pageCount = pageCount)
            }
        }
    } ?: PdfDocumentInfoState.Error("Unable to open this PDF.")
}.getOrElse {
    PdfDocumentInfoState.Error("Unable to open this PDF.")
}

private suspend fun renderPdfPage(
    context: Context,
    documentUri: Uri,
    pageIndex: Int,
    targetLongEdgePx: Int = PDF_RENDER_LONG_EDGE_PX,
): PdfPageRenderState {
    var unownedBitmap: Bitmap? = null
    return try {
        val renderedState = context.contentResolver.openFileDescriptor(documentUri, "r")
            ?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) {
                        return PdfPageRenderState.Error("This PDF has no pages.")
                    }
                    val safePageIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safePageIndex).use { page ->
                        val scale = targetLongEdgePx.toFloat() /
                            maxOf(page.width, page.height).coerceAtLeast(1)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        unownedBitmap = bitmap
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        currentCoroutineContext().ensureActive()
                        PdfPageRenderState.Ready(
                            bitmap = bitmap,
                            pageIndex = safePageIndex,
                        )
                    }
                }
            }
            ?: PdfPageRenderState.Error("Unable to open this PDF.")
        if (renderedState is PdfPageRenderState.Ready) {
            unownedBitmap = null
        }
        renderedState
    } catch (error: CancellationException) {
        unownedBitmap?.recycle()
        throw error
    } catch (_: Throwable) {
        unownedBitmap?.recycle()
        PdfPageRenderState.Error("Unable to open this PDF.")
    }
}

private fun initialPdfPageIndex(
    savedProgress: ReaderProgressSnapshot?,
    pageCount: Int,
    openMode: ReaderOpenMode,
): Int {
    if (openMode == ReaderOpenMode.StartFromBeginning) return 0
    val savedPage = savedProgress
        ?.takeIf { progress -> progress.locatorType == LocatorType.PAGE && !progress.isFinished }
        ?.page
    return savedPage?.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) ?: 0
}

private fun ReaderSessionStore.savePdfProgress(
    item: ReadableItem,
    pageIndex: Int,
    pageCount: Int,
    isFinished: Boolean = false,
) {
    val safePageCount = pageCount.coerceAtLeast(0)
    val safePageIndex = if (safePageCount > 0) {
        pageIndex.coerceIn(0, safePageCount - 1)
    } else {
        0
    }
    saveProgress(
        item = item,
        locator = ReaderLocator(
            locatorType = LocatorType.PAGE,
            page = safePageIndex,
            percent = if (safePageCount > 0) {
                (safePageIndex + 1).toDouble() / safePageCount.toDouble()
            } else {
                null
            },
        ),
        isFinished = isFinished,
        syncState = if (item.sourceType == SourceType.LOCAL) SyncState.CLEAN else SyncState.DIRTY,
        pageCount = safePageCount,
    )
}

private fun shouldPersistPdfProgress(
    pageCount: Int,
    initialRestoreCompleted: Boolean,
    userNavigated: Boolean,
): Boolean = pageCount > 0 && initialRestoreCompleted && userNavigated

private fun pdfProgressLabel(
    currentPageIndex: Int,
    pageCount: Int,
): String = if (pageCount > 0) {
    "Page ${currentPageIndex + 1} of $pageCount"
} else {
    "Preparing PDF"
}

private sealed interface PdfDocumentInfoState {
    data object Empty : PdfDocumentInfoState
    data object Loading : PdfDocumentInfoState
    data class Ready(val pageCount: Int) : PdfDocumentInfoState
    data class Error(val message: String) : PdfDocumentInfoState
}

private sealed interface PdfPageRenderState {
    val cacheKey: String

    data object Empty : PdfPageRenderState {
        override val cacheKey: String = "empty"
    }

    data object Loading : PdfPageRenderState {
        override val cacheKey: String = "loading"
    }

    data class Ready(
        val bitmap: Bitmap,
        val pageIndex: Int,
    ) : PdfPageRenderState {
        override val cacheKey: String = "page:$pageIndex"
        val contentDescription: String = "PDF page ${pageIndex + 1}"
    }

    data class Error(val message: String) : PdfPageRenderState {
        override val cacheKey: String = "error:$message"
    }
}

private sealed interface ReaderDocumentUriState {
    val cacheKey: String

    data object Loading : ReaderDocumentUriState {
        override val cacheKey: String = "loading"
    }

    data class Ready(
        val uri: Uri?,
    ) : ReaderDocumentUriState {
        override val cacheKey: String = uri?.toString() ?: "empty"
    }

    data class Error(
        val message: String,
    ) : ReaderDocumentUriState {
        override val cacheKey: String = "error:$message"
    }
}

private class PdfPreviewBitmapCache(
    private val maxEntries: Int,
) {
    private val lock = Any()
    private var closed = false
    private val cache = object : LinkedHashMap<Int, PdfPageRenderState.Ready>(
        maxEntries,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, PdfPageRenderState.Ready>,
        ): Boolean {
            val shouldRemove = size > maxEntries
            if (shouldRemove) {
                eldest.value.recycle()
            }
            return shouldRemove
        }
    }

    fun get(pageIndex: Int): PdfPageRenderState.Ready? = synchronized(lock) {
        if (closed) return@synchronized null
        cache[pageIndex]?.takeUnless { state -> state.bitmap.isRecycled }
    }

    fun put(
        pageIndex: Int,
        state: PdfPageRenderState.Ready,
    ) {
        synchronized(lock) {
            if (closed) {
                state.recycle()
                return@synchronized
            }
            cache.put(pageIndex, state)?.recycle()
        }
    }

    fun clear() {
        synchronized(lock) {
            closed = true
            cache.values.forEach(PdfPageRenderState.Ready::recycle)
            cache.clear()
        }
    }
}

private fun PdfPageRenderState.Ready.recycle() {
    if (!bitmap.isRecycled) {
        bitmap.recycle()
    }
}

private val PdfPreviewStripHeight = 190.dp
private val PdfNormalPreviewHeight = 126.dp
private val PdfSelectedPreviewHeight = 146.dp
private val PdfPreviewItemWidth = 92.dp
private const val PdfPagePreviewStripTag = "pdf-page-preview-strip"
private const val PDF_PREVIEW_CACHE_MAX_ENTRIES = 64
private const val PDF_RENDER_LONG_EDGE_PX = 2200
private const val PDF_RENDER_MEDIUM_LONG_EDGE_PX = 3200
private const val PDF_RENDER_MAX_LONG_EDGE_PX = 4096
private const val PDF_RENDER_REQUEST_DEBOUNCE_MILLIS = 180L
private const val PDF_PREVIEW_RENDER_LONG_EDGE_PX = 420
private const val PDF_EDGE_TAP_FRACTION = 0.28f
