package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderBarButton
import com.example.unifiedreader.feature.reader.core.ReaderChromeDefaults
import com.example.unifiedreader.feature.reader.core.ReaderChromeFrame
import com.example.unifiedreader.feature.reader.core.ReaderDock
import com.example.unifiedreader.feature.reader.core.ReaderDockProgress
import com.example.unifiedreader.feature.reader.core.ReaderTopBar
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.ui.components.AppIcons

/** The comics reader's panels; one is open at a time. */
internal enum class ComicsReaderPanel { None, Pages, Settings }

/**
 * The comics reader's bars and panels: the issue over where it sits and the settings button at
 * the top; in the dock the page strip over the Pages button, the percentage, the slider and the
 * page. Pages (every page as a grid) opens at the start edge, the settings as a card under their
 * button, both between the bars.
 */
@Composable
internal fun ComicsReaderChrome(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    currentPage: Int,
    currentPageIndexes: Set<Int>,
    progress: Float,
    readerMode: ComicReaderMode,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    spreadMode: ComicSpreadMode,
    stripSpreadMode: ComicSpreadMode,
    spreadPlanState: ComicSpreadPlanState,
    pageTurnStyle: PageTurnStyle,
    pageNumbersVisible: Boolean,
    pageSource: ComicPageSource,
    readerWorkCoordinator: ReaderWorkCoordinator?,
    stripLoadingEnabled: Boolean,
    overlayVisible: Boolean,
    onBack: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onProgressSelected: (Float) -> Unit,
    onReaderModeChanged: (ComicReaderMode) -> Unit,
    onReadingDirectionChanged: (ComicReadingDirection) -> Unit,
    onPageFitModeChanged: (ComicPageFitMode) -> Unit,
    onSpreadModeChanged: (ComicSpreadMode) -> Unit,
    onPageTurnStyleChanged: (PageTurnStyle) -> Unit,
    onPageNumbersVisibleChanged: (Boolean) -> Unit,
) {
    val titles = remember(item) { comicReaderTitles(item) }
    var openPanel by rememberSaveable(item.id) { mutableStateOf(ComicsReaderPanel.None) }
    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) openPanel = ComicsReaderPanel.None
    }
    fun toggle(panel: ComicsReaderPanel) {
        openPanel = if (openPanel == panel) ComicsReaderPanel.None else panel
    }
    val configuration = LocalConfiguration.current
    val thumbnailHeight = ReaderChromeDefaults.stripThumbnailHeight(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
    )
    ReaderChromeFrame(
        panelOpen = openPanel != ComicsReaderPanel.None,
        onDismissPanel = { openPanel = ComicsReaderPanel.None },
        topBar = {
            ReaderTopBar(
                title = titles.title,
                subtitle = titles.subtitle,
                onBack = onBack,
            ) {
                ReaderBarButton(
                    icon = AppIcons.Tune,
                    contentDescription = "Reading settings",
                    active = openPanel == ComicsReaderPanel.Settings,
                    onClick = { toggle(ComicsReaderPanel.Settings) },
                )
            }
        },
        dock = {
            ReaderDock(
                strip = {
                    ComicPagePreviewStrip(
                        item = item,
                        pages = pages,
                        currentPage = currentPage,
                        readingDirection = readingDirection,
                        spreadMode = stripSpreadMode,
                        spreadPlanState = spreadPlanState,
                        pageSource = pageSource,
                        thumbnailHeight = thumbnailHeight,
                        readerWorkCoordinator = readerWorkCoordinator,
                        previewLoadingEnabled = stripLoadingEnabled,
                        onPageSelected = onPageSelected,
                    )
                },
            ) {
                ReaderBarButton(
                    icon = AppIcons.GridView,
                    contentDescription = "Pages",
                    active = openPanel == ComicsReaderPanel.Pages,
                    onClick = { toggle(ComicsReaderPanel.Pages) },
                )
                ReaderDockProgress(
                    progress = progress,
                    percentLabel = comicPercentLabel(progress),
                    positionLabel = comicDockPositionLabel(currentPageIndexes, pages.size),
                    readingDirection = comicProgressBarDirection(readerMode = readerMode, readingDirection = readingDirection),
                    onProgressSelected = onProgressSelected,
                )
            }
        },
    ) { slot ->
        ComicPagesPanel(
            item = item,
            issueLabel = titles.title,
            pages = pages,
            currentPageIndexes = currentPageIndexes,
            progress = progress,
            pageSource = pageSource,
            readerWorkCoordinator = readerWorkCoordinator,
            // Opened on purpose, so its thumbnails load straight away.
            previewLoadingEnabled = true,
            isOpen = openPanel == ComicsReaderPanel.Pages,
            slot = slot,
            onDismissRequest = { openPanel = ComicsReaderPanel.None },
            onPageSelected = { page ->
                openPanel = ComicsReaderPanel.None
                onPageSelected(page)
            },
        )
        ComicsReaderPreferencesSheet(
            isOpen = openPanel == ComicsReaderPanel.Settings,
            slot = slot,
            title = titles.title,
            readerMode = readerMode,
            readingDirection = readingDirection,
            pageFitMode = pageFitMode,
            spreadMode = spreadMode,
            pageTurnStyle = pageTurnStyle,
            pageNumbersVisible = pageNumbersVisible,
            onDismissRequest = { openPanel = ComicsReaderPanel.None },
            onReaderModeChanged = onReaderModeChanged,
            onReadingDirectionChanged = onReadingDirectionChanged,
            onPageFitModeChanged = onPageFitModeChanged,
            onSpreadModeChanged = onSpreadModeChanged,
            onPageTurnStyleChanged = onPageTurnStyleChanged,
            onPageNumbersVisibleChanged = onPageNumbersVisibleChanged,
        )
    }
}
