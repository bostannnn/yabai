package com.example.unifiedreader.feature.reader.comics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.ReaderPageNumberChoiceRow
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.ui.components.AppChoiceRow
import com.example.unifiedreader.ui.components.AppFloatingPanel
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement
import com.example.unifiedreader.ui.components.AppPanelHeader

/**
 * The comics quick settings: six one-line choices in a card under the settings button (a
 * bottom sheet on phones). Rows that only apply to the paged layout stay in place but
 * disabled while scrolling, so the card never changes height.
 */
@Composable
internal fun ComicsReaderPreferencesSheet(
    isOpen: Boolean,
    maxSheetHeight: Dp,
    title: String,
    readerMode: ComicReaderMode,
    readingDirection: ComicReadingDirection,
    pageFitMode: ComicPageFitMode,
    spreadMode: ComicSpreadMode,
    pageTurnStyle: PageTurnStyle,
    pageNumbersVisible: Boolean,
    onDismissRequest: () -> Unit,
    onReaderModeChanged: (ComicReaderMode) -> Unit,
    onReadingDirectionChanged: (ComicReadingDirection) -> Unit,
    onPageFitModeChanged: (ComicPageFitMode) -> Unit,
    onSpreadModeChanged: (ComicSpreadMode) -> Unit,
    onPageTurnStyleChanged: (PageTurnStyle) -> Unit,
    onPageNumbersVisibleChanged: (Boolean) -> Unit,
) {
    val paged = readerMode == ComicReaderMode.Paged
    AppFloatingPanel(
        visible = isOpen,
        placement = AppFloatingPanelPlacement.TopEnd,
        onDismissRequest = onDismissRequest,
        maxHeight = maxSheetHeight,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        containerColor = readerOverlayTokens().colors.container,
        // ReaderOverlayBar dims the whole page itself; the panel's slot is smaller.
        scrimColor = Color.Transparent,
        testTag = ComicsPreferencesSheetTag,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            AppPanelHeader(
                title = "Reading",
                subtitle = title,
            )
            AppChoiceRow(
                label = "Layout",
                options = ComicReaderMode.entries,
                selected = readerMode,
                onSelected = onReaderModeChanged,
                optionLabel = { it.label },
            )
            AppChoiceRow(
                label = "Direction",
                options = ComicReadingDirection.entries,
                selected = readingDirection,
                onSelected = onReadingDirectionChanged,
                optionLabel = ::comicReadingDirectionLabel,
            )
            AppChoiceRow(
                label = "Pages",
                options = ComicSpreadMode.entries,
                selected = spreadMode,
                onSelected = onSpreadModeChanged,
                optionLabel = { it.label },
                enabled = paged,
            )
            AppChoiceRow(
                label = "Page turn",
                options = PageTurnStyle.entries,
                selected = pageTurnStyle,
                onSelected = onPageTurnStyleChanged,
                optionLabel = { it.label },
                enabled = paged,
            )
            AppChoiceRow(
                label = "Fit",
                options = ComicPageFitMode.entries,
                selected = pageFitMode,
                onSelected = onPageFitModeChanged,
                optionLabel = ::comicPageFitLabel,
                enabled = paged,
            )
            ReaderPageNumberChoiceRow(
                visible = pageNumbersVisible,
                onVisibleChanged = onPageNumbersVisibleChanged,
            )
        }
    }
}

/** Words instead of "LTR"/"RTL": the row's label already says it is the direction. */
internal fun comicReadingDirectionLabel(direction: ComicReadingDirection): String = when (direction) {
    ComicReadingDirection.LTR -> "Left to right"
    ComicReadingDirection.RTL -> "Right to left"
}

/** Short segment labels; the row's label "Fit" carries the verb. */
internal fun comicPageFitLabel(fitMode: ComicPageFitMode): String = when (fitMode) {
    ComicPageFitMode.FitPage -> "Screen"
    ComicPageFitMode.FitWidth -> "Width"
    ComicPageFitMode.FitHeight -> "Height"
}

internal const val ComicsPreferencesSheetTag = "comics-preferences-sheet"
