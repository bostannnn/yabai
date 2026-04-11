package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderBottomProgressBar
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ComicsReaderScreen(
    item: ReadableItem,
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
    onBack: () -> Unit,
) {
    val pages = remember(item.id, pageSource) { pageSource.getPages(item) }
    val progressHydrated by progressStore.isHydratedFlow.collectAsState()
    val preferencesHydrated by preferencesStore.isHydratedFlow.collectAsState()
    val savedPagesByItemId by progressStore.savedPagesFlow.collectAsState()
    val restoredPreferences by preferencesStore.observePreferences(item).collectAsState(initial = null)

    if (pages.isEmpty()) {
        EmptyComicsReader(item = item)
        return
    }

    val readerState = rememberComicsReaderState(
        itemId = item.id,
        pageCount = pages.size,
        initialPage = remember(item.id, progressStore, pages.size) {
            progressStore.loadPage(item)?.coerceIn(0, pages.lastIndex) ?: 0
        },
        initialReadingDirection = remember(item.id, preferencesStore) {
            preferencesStore.loadReadingDirection(item)
        },
        initialPageFitMode = remember(item.id, preferencesStore) {
            preferencesStore.loadPageFitMode(item)
        },
        initialSpreadMode = remember(item.id, preferencesStore) {
            preferencesStore.loadSpreadMode(item)
        },
        initialSplitWidePages = remember(item.id, preferencesStore) {
            preferencesStore.loadSplitWidePages(item)
        },
        initialResumeSegment = remember(item.id, preferencesStore) {
            preferencesStore.loadResumeSegment(item)
        },
    )
    var pageMetadataById by remember(item.id) { mutableStateOf<Map<String, ComicPageMetadata>>(emptyMap()) }
    val currentPageProgress = (readerState.currentPage + 1).toFloat() / pages.size.toFloat()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var progressRestoreCompleted by remember(item.id) { mutableStateOf(false) }
    var preferencesRestoreCompleted by remember(item.id) { mutableStateOf(false) }
    val overlayState = rememberReaderOverlayState(
        key = "comic-overlay-${item.id}",
        initiallyVisible = true,
    )
    val latestProgressHydrated by rememberUpdatedState(progressHydrated)
    val latestPreferencesHydrated by rememberUpdatedState(preferencesHydrated)
    val latestProgressRestoreCompleted by rememberUpdatedState(progressRestoreCompleted)
    val latestPreferencesRestoreCompleted by rememberUpdatedState(preferencesRestoreCompleted)
    val persistCurrentPage by rememberUpdatedState(
        newValue = {
            progressStore.savePage(
                item = item,
                pageIndex = readerState.currentPage,
                pageCount = pages.size,
                isFinished = isComicProgressFinished(
                    pageIndex = readerState.currentPage,
                    pageCount = pages.size,
                    segment = readerState.resumeSegment,
                    readingDirection = readerState.readingDirection,
                ),
            )
        },
    )
    val persistReaderPreferences by rememberUpdatedState(
        newValue = {
            preferencesStore.saveReadingDirection(item, readerState.readingDirection)
            preferencesStore.savePageFitMode(item, readerState.pageFitMode)
            preferencesStore.saveSpreadMode(item, readerState.spreadMode)
            preferencesStore.saveSplitWidePages(item, readerState.splitWidePages)
            preferencesStore.saveResumeSegment(item, readerState.resumeSegment)
        },
    )
    val restoredPage = savedPagesByItemId[item.id]
    LaunchedEffect(item.id, progressHydrated, restoredPage, pages.size) {
        if (!progressHydrated) {
            progressRestoreCompleted = false
            return@LaunchedEffect
        }
        restoredPage?.coerceIn(0, pages.lastIndex)?.let(readerState::restorePageIfUnchanged)
        progressRestoreCompleted = true
    }

    LaunchedEffect(item.id, preferencesHydrated, restoredPreferences) {
        if (!preferencesHydrated) {
            preferencesRestoreCompleted = false
            return@LaunchedEffect
        }
        restoredPreferences?.let { preferences ->
            readerState.restoreReadingDirectionIfUnchanged(preferences.readingDirection)
            readerState.restorePageFitModeIfUnchanged(preferences.pageFitMode)
            readerState.restoreSpreadModeIfUnchanged(preferences.spreadMode)
            readerState.restoreSplitWidePagesIfUnchanged(preferences.splitWidePages)
            readerState.restoreResumeSegmentIfUnchanged(preferences.resumeSegment)
        }
        preferencesRestoreCompleted = true
    }

    LaunchedEffect(
        item.id,
        readerState.currentPage,
        readerState.readingDirection,
        readerState.spreadMode,
        readerState.splitWidePages,
        pages,
    ) {
        val targetIndexes = metadataRequestIndexes(
            currentPage = readerState.currentPage,
            pageCount = pages.size,
        ).filter { index -> pages[index].id !in pageMetadataById }
        if (targetIndexes.isEmpty()) return@LaunchedEffect
        val loadedMetadata = coroutineScope {
            targetIndexes.map { index ->
                async {
                    val page = pages[index]
                    page.id to pageSource.loadPageMetadata(item, page)
                }
            }.awaitAll()
        }.mapNotNull { (pageId, metadata) ->
            metadata?.let { pageId to it }
        }.toMap()
        if (loadedMetadata.isNotEmpty()) {
            pageMetadataById = pageMetadataById + loadedMetadata
        }
    }

    LaunchedEffect(
        item.id,
        progressHydrated,
        progressRestoreCompleted,
        readerState.currentPage,
        readerState.resumeSegment,
        readerState.readingDirection,
        pages.size,
    ) {
        if (!progressHydrated || !progressRestoreCompleted) return@LaunchedEffect
        delay(250)
        persistCurrentPage()
    }

    LaunchedEffect(
        item.id,
        preferencesHydrated,
        preferencesRestoreCompleted,
        readerState.readingDirection,
        readerState.pageFitMode,
        readerState.spreadMode,
        readerState.splitWidePages,
        readerState.resumeSegment,
    ) {
        if (!preferencesHydrated || !preferencesRestoreCompleted) return@LaunchedEffect
        persistReaderPreferences()
    }

    DisposableEffect(lifecycleOwner, item.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (latestProgressHydrated && latestProgressRestoreCompleted) {
                    persistCurrentPage()
                }
                if (latestPreferencesHydrated && latestPreferencesRestoreCompleted) {
                    persistReaderPreferences()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (latestProgressHydrated && latestProgressRestoreCompleted) {
                persistCurrentPage()
            }
            if (latestPreferencesHydrated && latestPreferencesRestoreCompleted) {
                persistReaderPreferences()
            }
        }
    }

    ImmersiveReaderOverlay(
        overlayState = overlayState,
        autoHideDelayMillis = 0L,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D11)),
        content = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val useDoublePageSpreads = shouldUseDoublePageSpreads(
                    spreadMode = readerState.spreadMode,
                    maxWidthDp = maxWidth.value,
                    maxHeightDp = maxHeight.value,
                    pageCount = pages.size,
                )
                val spreads = remember(
                    pages,
                    readerState.readingDirection,
                    useDoublePageSpreads,
                    pageMetadataById,
                    readerState.splitWidePages,
                ) {
                    buildComicPageSpreads(
                        pages = pages,
                        readingDirection = readerState.readingDirection,
                        useDoublePageSpreads = useDoublePageSpreads,
                        pageMetadataById = pageMetadataById,
                        splitWidePages = readerState.splitWidePages,
                    )
                }
                val pagerState = rememberPagerState(
                    initialPage = spreads.indexOfPage(readerState.currentPage, readerState.resumeSegment),
                    pageCount = { spreads.size },
                )
                val currentSpread = spreads.getOrNull(pagerState.currentPage) ?: spreads.first()

                LaunchedEffect(spreads, readerState.currentPage, readerState.resumeSegment) {
                    val targetSpreadIndex = spreads.indexOfPage(readerState.currentPage, readerState.resumeSegment)
                    if (pagerState.currentPage != targetSpreadIndex &&
                        !currentSpread.matchesPosition(readerState.currentPage, readerState.resumeSegment)
                    ) {
                        pagerState.scrollToPage(targetSpreadIndex)
                    }
                }

                LaunchedEffect(pagerState.settledPage, spreads, item.id) {
                    val settledSpread = spreads.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                    val settledPageIndex = when {
                        readerState.currentPage in settledSpread.pageIndexes -> readerState.currentPage
                        else -> settledSpread.resumePageIndex
                    }
                    readerState.onPageSettled(
                        pageIndex = settledPageIndex,
                        segment = settledSpread.resumeSegment,
                    )
                    pageSource.prefetchAround(
                        item = item,
                        settledPageIndex = settledPageIndex,
                        pageCount = pages.size,
                    )
                }

                ComicSpreadPager(
                    item = item,
                    spreads = spreads,
                    currentPage = readerState.currentPage,
                    readingDirection = readerState.readingDirection,
                    pageFitMode = readerState.pageFitMode,
                    isZoomed = readerState.isCurrentPageZoomed,
                    overlayVisible = overlayState.isVisible,
                    pageSource = pageSource,
                    pagerState = pagerState,
                    onZoomStateChanged = readerState::onCurrentPageZoomChanged,
                    onInteraction = {},
                    onToggleOverlay = overlayState::toggle,
                    onNavigatePrevious = { spreadIndex ->
                        if (spreadIndex > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(spreadIndex - 1)
                            }
                        }
                    },
                    onNavigateNext = { spreadIndex ->
                        if (spreadIndex < spreads.lastIndex) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(spreadIndex + 1)
                            }
                        }
                    },
                )
            }
        },
        overlay = {
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderOverlayBar(
                    itemId = item.id,
                    title = item.title,
                    progressLabel = "${readerState.currentPage + 1} / ${pages.size}",
                    readingDirection = readerState.readingDirection,
                    pageFitMode = readerState.pageFitMode,
                    spreadMode = readerState.spreadMode,
                    splitWidePages = readerState.splitWidePages,
                    overlayVisible = overlayState.isVisible,
                    onBack = onBack,
                    onReadingDirectionChanged = readerState::onReadingDirectionChanged,
                    onPageFitModeChanged = readerState::onPageFitModeChanged,
                    onSpreadModeChanged = readerState::onSpreadModeChanged,
                    onSplitWidePagesChanged = readerState::onSplitWidePagesChanged,
                )
                ReaderBottomProgressBar(
                    progress = currentPageProgress,
                    percentLabel = "${(currentPageProgress * 100f).roundToInt()}%",
                    pageLabel = "${readerState.currentPage + 1}/${pages.size}",
                    readingDirection = readerState.readingDirection,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onProgressSelected = { selectedProgress ->
                        val targetPage = (selectedProgress * (pages.size - 1))
                            .roundToInt()
                            .coerceIn(0, pages.lastIndex)
                        readerState.onPageSettled(targetPage)
                    },
                )
            }
        },
    )
}

private fun metadataRequestIndexes(
    currentPage: Int,
    pageCount: Int,
    radius: Int = 2,
): List<Int> = buildList {
    if (pageCount <= 0) return@buildList
    add(currentPage.coerceIn(0, pageCount - 1))
    for (offset in 1..radius) {
        add((currentPage + offset).coerceIn(0, pageCount - 1))
        add((currentPage - offset).coerceIn(0, pageCount - 1))
    }
}.distinct()

@Composable
private fun EmptyComicsReader(item: ReadableItem) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No local sample pages configured for ${item.title}.",
            color = Color(0xFFB7C4CE),
            textAlign = TextAlign.Center,
        )
    }
}
