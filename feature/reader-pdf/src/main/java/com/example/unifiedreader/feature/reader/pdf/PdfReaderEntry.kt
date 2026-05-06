package com.example.unifiedreader.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderActiveSessionTracker
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PdfReaderScreen(
    item: ReadableItem,
    documentUri: Uri? = null,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)? = null,
    sessionStore: ReaderSessionStore? = null,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    openMode: ReaderOpenMode = ReaderOpenMode.Default,
    onCompletionRequested: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
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
    var initialRestoreCompleted by remember(item.id, documentUriState.cacheKey, openMode) {
        mutableStateOf(false)
    }
    LaunchedEffect(item.id, documentUriState.cacheKey, pageCount, openMode) {
        initialRestoreCompleted = false
        if (pageCount <= 0) {
            currentPageIndex = 0
        } else {
            currentPageIndex = initialPdfPageIndex(
                item = item,
                sessionStore = sessionStore,
                pageCount = pageCount,
                openMode = openMode,
            )
        }
        initialRestoreCompleted = true
    }
    val pageState by produceState<PdfPageRenderState>(
        initialValue = PdfPageRenderState.Loading,
        key1 = documentUriState.cacheKey,
        key2 = currentPageIndex,
        key3 = pageCount,
    ) {
        val uri = (documentUriState as? ReaderDocumentUriState.Ready)?.uri
        value = when {
            uri == null -> PdfPageRenderState.Empty
            pageCount <= 0 -> PdfPageRenderState.Empty
            else -> withContext(Dispatchers.IO) {
                renderPdfPage(
                    context = context.applicationContext,
                    documentUri = uri,
                    pageIndex = currentPageIndex.coerceIn(0, pageCount - 1),
                )
            }
        }
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
    LaunchedEffect(item.id, currentPageIndex, pageCount, initialRestoreCompleted) {
        if (pageCount <= 0 || !initialRestoreCompleted) return@LaunchedEffect
        sessionStore?.savePdfProgress(
            item = item,
            pageIndex = currentPageIndex,
            pageCount = pageCount,
        )
    }
    val latestPageIndex by rememberUpdatedState(currentPageIndex)
    val latestPageCount by rememberUpdatedState(pageCount)
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
    val startReadingSession by rememberUpdatedState(
        newValue = {
            if (latestPageCount > 0) {
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
                Lifecycle.Event.ON_STOP -> flushReadingSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushReadingSession()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PdfReaderControls(
            currentPageIndex = currentPageIndex,
            pageCount = pageCount,
            onPrevious = {
                currentPageIndex = (currentPageIndex - 1).coerceAtLeast(0)
            },
            onNext = {
                if (pageCount > 0 && currentPageIndex >= pageCount - 1) {
                    sessionStore?.savePdfProgress(
                        item = item,
                        pageIndex = currentPageIndex,
                        pageCount = pageCount,
                        isFinished = true,
                    )
                    onCompletionRequested()
                } else {
                    currentPageIndex = (currentPageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
                }
            },
        )
        PdfPageViewport(
            pageState = pageState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
private fun PdfReaderControls(
    currentPageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onPrevious,
            enabled = pageCount > 0 && currentPageIndex > 0,
        ) {
            Text(text = "Previous")
        }
        Text(
            text = if (pageCount > 0) {
                "Page ${currentPageIndex + 1} of $pageCount"
            } else {
                "Preparing PDF"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onNext,
            enabled = pageCount > 0,
        ) {
            Text(text = if (pageCount > 0 && currentPageIndex >= pageCount - 1) "Finish" else "Next")
        }
    }
}

@Composable
private fun PdfPageViewport(
    pageState: PdfPageRenderState,
    modifier: Modifier = Modifier,
) {
    var scale by remember(pageState.cacheKey) { mutableFloatStateOf(1f) }
    var offset by remember(pageState.cacheKey) { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) {
            Offset.Zero
        } else {
            offset + panChange
        }
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(pageState.cacheKey) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        offset = Offset.Zero
                    },
                )
            }
            .transformable(transformState),
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
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        }
    }
}

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
): PdfPageRenderState = runCatching {
    context.contentResolver.openFileDescriptor(documentUri, "r")?.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount <= 0) {
                return PdfPageRenderState.Error("This PDF has no pages.")
            }
            val safePageIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safePageIndex).use { page ->
                val scale = PDF_RENDER_LONG_EDGE_PX.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfPageRenderState.Ready(
                    bitmap = bitmap,
                    pageIndex = safePageIndex,
                )
            }
        }
    } ?: PdfPageRenderState.Error("Unable to open this PDF.")
}.getOrElse {
    PdfPageRenderState.Error("Unable to open this PDF.")
}

private fun initialPdfPageIndex(
    item: ReadableItem,
    sessionStore: ReaderSessionStore?,
    pageCount: Int,
    openMode: ReaderOpenMode,
): Int {
    if (openMode == ReaderOpenMode.StartFromBeginning) return 0
    val savedPage = sessionStore
        ?.getProgress(item)
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

private const val PDF_RENDER_LONG_EDGE_PX = 2200
