package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
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
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderBottomProgressBar
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun ComicsReaderScreen(
    item: ReadableItem,
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
    onBack: () -> Unit,
    openMode: ReaderOpenMode = ReaderOpenMode.Default,
    onNextItemRequested: () -> Unit = {},
) {
    val pages = rememberObservedComicPages(
        item = item,
        pageSource = pageSource,
    )
    val progressHydrated by progressStore.isHydratedFlow.collectAsState()
    val preferencesHydrated by preferencesStore.isHydratedFlow.collectAsState()
    val savedPagesByItemId by progressStore.savedPagesFlow.collectAsState()
    val restoredPreferences by preferencesStore.observePreferences(item).collectAsState(initial = null)

    if (pages.isEmpty()) {
        EmptyComicsReader(item = item)
        return
    }

    val initialPosition = remember(
        item.id,
        openMode,
        progressStore,
        preferencesStore,
        pages.size,
    ) {
        resolveComicReaderInitialPosition(
            openMode = openMode,
            savedPage = progressStore.loadPage(item),
            savedResumeAnchorPage = preferencesStore.loadResumeAnchorPage(item),
            savedResumeSegment = preferencesStore.loadResumeSegment(item),
            savedResumeScrollOffsetRatio = preferencesStore.loadResumeScrollOffsetRatio(item),
            lastPageIndex = pages.lastIndex,
        )
    }
    val readerState = rememberComicsReaderState(
        itemId = item.id,
        pageCount = pages.size,
        initialPage = initialPosition.page,
        initialReaderMode = remember(item.id, preferencesStore) {
            preferencesStore.loadReaderMode(item)
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
        initialResumeAnchorPage = initialPosition.resumeAnchorPage,
        initialResumeSegment = initialPosition.resumeSegment,
        initialResumeScrollOffsetRatio = initialPosition.resumeScrollOffsetRatio,
    )
    val currentPageProgress = (readerState.currentPage + 1).toFloat() / pages.size.toFloat()
    val lifecycleOwner = LocalLifecycleOwner.current
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
    val persistProgress by rememberUpdatedState(
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
    val persistCompletedProgress by rememberUpdatedState(
        newValue = {
            progressStore.savePage(
                item = item,
                pageIndex = completedComicPageIndex(pages.size),
                pageCount = pages.size,
                isFinished = true,
            )
        },
    )
    val persistResumePosition by rememberUpdatedState(
        newValue = {
            preferencesStore.saveResumeAnchorPage(item, readerState.resumeAnchorPage)
            preferencesStore.saveResumeSegment(
                item,
                sanitizeComicResumeSegment(readerState.resumeSegment),
            )
            preferencesStore.saveResumeScrollOffsetRatio(
                item,
                if (readerState.readerMode == ComicReaderMode.VerticalScroll) {
                    sanitizeComicResumeScrollOffsetRatio(readerState.resumeScrollOffsetRatio)
                } else {
                    null
                },
            )
        },
    )
    val persistReaderPreferences by rememberUpdatedState(
        newValue = {
            preferencesStore.saveReaderMode(item, readerState.readerMode)
            preferencesStore.saveReadingDirection(item, readerState.readingDirection)
            preferencesStore.savePageFitMode(item, readerState.pageFitMode)
            preferencesStore.saveSpreadMode(item, readerState.spreadMode)
        },
    )
    val restoredPage = savedPagesByItemId[item.id]
    LaunchedEffect(item.id, progressHydrated, restoredPage, pages.size) {
        if (!progressHydrated) {
            progressRestoreCompleted = false
            return@LaunchedEffect
        }
        if (shouldRestoreComicReaderPosition(openMode)) {
            restoredPage?.coerceIn(0, pages.lastIndex)?.let(readerState::restorePageIfUnchanged)
        }
        progressRestoreCompleted = true
    }

    LaunchedEffect(item.id, preferencesHydrated, restoredPreferences) {
        if (!preferencesHydrated) {
            preferencesRestoreCompleted = false
            return@LaunchedEffect
        }
        restoredPreferences?.let { preferences ->
            readerState.restoreReaderModeIfUnchanged(preferences.readerMode)
            readerState.restoreReadingDirectionIfUnchanged(preferences.readingDirection)
            readerState.restorePageFitModeIfUnchanged(preferences.pageFitMode)
            readerState.restoreSpreadModeIfUnchanged(preferences.spreadMode)
            if (shouldRestoreComicReaderPosition(openMode)) {
                readerState.restoreResumeAnchorIfUnchanged(preferences.resumeAnchorPage)
                readerState.restoreResumeSegmentIfUnchanged(preferences.resumeSegment)
                readerState.restoreResumeScrollOffsetRatioIfUnchanged(preferences.resumeScrollOffsetRatio)
            }
        }
        preferencesRestoreCompleted = true
    }

    LaunchedEffect(
        item.id,
        progressHydrated,
        progressRestoreCompleted,
        preferencesHydrated,
        preferencesRestoreCompleted,
        readerState.currentPage,
        readerState.resumeAnchorPage,
        readerState.resumeSegment,
        readerState.resumeScrollOffsetRatio,
        readerState.readingDirection,
        readerState.readerMode,
        pages.size,
    ) {
        if (!progressHydrated ||
            !progressRestoreCompleted ||
            !preferencesHydrated ||
            !preferencesRestoreCompleted
        ) {
            return@LaunchedEffect
        }
        delay(250)
        persistProgress()
        persistResumePosition()
    }

    LaunchedEffect(
        item.id,
        preferencesHydrated,
        preferencesRestoreCompleted,
        progressRestoreCompleted,
    ) {
        if (!progressRestoreCompleted || !preferencesHydrated || !preferencesRestoreCompleted) return@LaunchedEffect
        persistProgress()
        persistResumePosition()
    }

    LaunchedEffect(
        item.id,
        preferencesHydrated,
        preferencesRestoreCompleted,
        readerState.readerMode,
        readerState.readingDirection,
        readerState.pageFitMode,
        readerState.spreadMode,
    ) {
        if (!preferencesHydrated || !preferencesRestoreCompleted) return@LaunchedEffect
        persistReaderPreferences()
    }

    DisposableEffect(lifecycleOwner, item.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (latestProgressHydrated &&
                    latestProgressRestoreCompleted &&
                    latestPreferencesHydrated &&
                    latestPreferencesRestoreCompleted
                ) {
                    persistProgress()
                    persistResumePosition()
                }
                if (latestPreferencesHydrated && latestPreferencesRestoreCompleted) {
                    persistReaderPreferences()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (latestProgressHydrated &&
                latestProgressRestoreCompleted &&
                latestPreferencesHydrated &&
                latestPreferencesRestoreCompleted
            ) {
                persistProgress()
                persistResumePosition()
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
            val surfaceStateKey = remember(pages) {
                Triple(
                    pages.size,
                    pages.firstOrNull()?.id.orEmpty(),
                    pages.lastOrNull()?.id.orEmpty(),
                )
            }
            key(item.id, readerState.readerMode, surfaceStateKey) {
                when (readerState.readerMode) {
                    ComicReaderMode.Paged -> ComicsPagedReaderSurface(
                        item = item,
                        pages = pages,
                        pageSource = pageSource,
                        readerState = readerState,
                        overlayVisible = overlayState.isVisible,
                        onToggleOverlay = overlayState::toggle,
                        onNavigatePastEnd = {
                            persistCompletedProgress()
                            onNextItemRequested()
                        },
                    )

                    ComicReaderMode.VerticalScroll -> ComicsContinuousReaderSurface(
                        item = item,
                        pages = pages,
                        pageSource = pageSource,
                        readerState = readerState,
                        onToggleOverlay = overlayState::toggle,
                        onNavigatePastEnd = {
                            persistCompletedProgress()
                            onNextItemRequested()
                        },
                    )
                }
            }
        },
        overlay = {
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderOverlayBar(
                    itemId = item.id,
                    title = comicReaderDisplayTitle(item),
                    progressLabel = "${readerState.currentPage + 1} / ${pages.size}",
                    readerMode = readerState.readerMode,
                    readingDirection = readerState.readingDirection,
                    pageFitMode = readerState.pageFitMode,
                    spreadMode = readerState.spreadMode,
                    overlayVisible = overlayState.isVisible,
                    onBack = onBack,
                    onReaderModeChanged = readerState::onReaderModeChanged,
                    onReadingDirectionChanged = readerState::onReadingDirectionChanged,
                    onPageFitModeChanged = readerState::onPageFitModeChanged,
                    onSpreadModeChanged = readerState::onSpreadModeChanged,
                )
                ReaderBottomProgressBar(
                    progress = currentPageProgress,
                    percentLabel = "${(currentPageProgress * 100f).roundToInt()}%",
                    pageLabel = "${readerState.currentPage + 1}/${pages.size}",
                    readingDirection = comicProgressBarDirection(
                        readerMode = readerState.readerMode,
                        readingDirection = readerState.readingDirection,
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onProgressSelected = { selectedProgress ->
                        val targetPage = (selectedProgress * (pages.size - 1))
                            .roundToInt()
                            .coerceIn(0, pages.lastIndex)
                        when (readerState.readerMode) {
                            ComicReaderMode.Paged -> readerState.onPageSettled(targetPage)
                            ComicReaderMode.VerticalScroll -> readerState.onContinuousPositionSettled(
                                pageIndex = targetPage,
                                offsetRatio = 0f,
                            )
                        }
                    },
                )
                ComicPagePreviewStrip(
                    item = item,
                    pages = pages,
                    currentPage = readerState.currentPage,
                    readingDirection = readerState.readingDirection,
                    spreadMode = when (readerState.readerMode) {
                        ComicReaderMode.Paged -> readerState.spreadMode
                        ComicReaderMode.VerticalScroll -> ComicSpreadMode.Single
                    },
                    pageSource = pageSource,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 60.dp),
                    onPageSelected = { targetPage ->
                        when (readerState.readerMode) {
                            ComicReaderMode.Paged -> readerState.onPageSettled(targetPage)
                            ComicReaderMode.VerticalScroll -> readerState.onContinuousPositionSettled(
                                pageIndex = targetPage,
                                offsetRatio = 0f,
                            )
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun EmptyComicsReader(item: ReadableItem) {
    Box(
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
