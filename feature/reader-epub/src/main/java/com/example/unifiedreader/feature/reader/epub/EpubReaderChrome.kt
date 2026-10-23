package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.unifiedreader.feature.reader.core.ReaderBarButton
import com.example.unifiedreader.feature.reader.core.ReaderChromeFrame
import com.example.unifiedreader.feature.reader.core.ReaderDock
import com.example.unifiedreader.feature.reader.core.ReaderDockProgress
import com.example.unifiedreader.feature.reader.core.ReaderPanelSlot
import com.example.unifiedreader.feature.reader.core.ReaderTopBar
import com.example.unifiedreader.ui.components.AppIcons

/**
 * The EPUB reader's bars and panels: the title over the chapter and author, Aa at the top end;
 * the contents button, the percentage, the slider with its chapter ticks and the page in the
 * dock. [panels] are the contents and settings panels, placed between the bars.
 */
@Composable
internal fun EpubReaderChrome(
    title: String,
    author: String?,
    tocEntries: List<EpubTocEntry>,
    currentHref: String?,
    currentProgress: Double,
    currentPage: Int,
    pageCount: Int,
    chapterMarks: List<Float>,
    isTocOpen: Boolean,
    isPreferencesOpen: Boolean,
    onBack: () -> Unit,
    onTocClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onDismissPanels: () -> Unit,
    onProgressSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
    panels: @Composable BoxScope.(ReaderPanelSlot) -> Unit,
) {
    val chapterTitle = remember(tocEntries, currentHref, currentProgress) {
        tocEntries.epubTocChapterEntry(
            tocEntries.activeEpubTocEntry(currentProgress = currentProgress, currentHref = currentHref),
        )?.title
    }
    ReaderChromeFrame(
        panelOpen = isTocOpen || isPreferencesOpen,
        onDismissPanel = onDismissPanels,
        modifier = modifier,
        topBar = {
            ReaderTopBar(
                title = title,
                subtitle = epubReaderSubtitle(chapterTitle = chapterTitle, author = author),
                onBack = onBack,
            ) {
                ReaderBarButton(
                    icon = AppIcons.FormatSize,
                    contentDescription = "Reader preferences",
                    active = isPreferencesOpen,
                    onClick = onPreferencesClick,
                )
            }
        },
        dock = {
            ReaderDock {
                ReaderBarButton(
                    icon = AppIcons.Toc,
                    contentDescription = "Table of contents",
                    active = isTocOpen,
                    onClick = onTocClick,
                )
                ReaderDockProgress(
                    progress = currentProgress.toFloat(),
                    percentLabel = buildEpubPercentLabel(currentProgress),
                    positionLabel = buildEpubDockPositionLabel(position = currentPage, pageCount = pageCount),
                    chapterMarks = chapterMarks,
                    onProgressSelected = onProgressSelected,
                )
            }
        },
        panels = panels,
    )
}

/** The top bar's second line: where you are, "Глава 3 · Анна Лесная", never the page. */
internal fun epubReaderSubtitle(
    chapterTitle: String?,
    author: String?,
): String? = listOfNotNull(
    chapterTitle?.trim()?.takeIf(String::isNotEmpty),
    author?.trim()?.takeIf(String::isNotEmpty),
).joinToString(" · ").ifEmpty { null }
