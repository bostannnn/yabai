package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

@Composable
fun rememberComicsReaderState(
    itemId: String,
    pageCount: Int,
    initialPage: Int = 0,
    initialReaderMode: ComicReaderMode = ComicReaderMode.Paged,
    initialReadingDirection: ComicReadingDirection = ComicReadingDirection.LTR,
    initialPageFitMode: ComicPageFitMode = ComicPageFitMode.FitPage,
    initialSpreadMode: ComicSpreadMode = ComicSpreadMode.Auto,
    initialPageTurnStyle: PageTurnStyle = PageTurnStyle.Slide,
    initialSplitWidePages: Boolean = false,
    initialResumeAnchorPage: Int? = null,
    initialResumeSegment: ComicPageSegment? = null,
    initialResumeScrollOffsetRatio: Float? = null,
): ComicsReaderState {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safeInitialPage = initialPage.coerceIn(0, safePageCount - 1)
    val state = rememberSaveable(
        itemId,
        saver = ComicsReaderState.saver(pageCount = safePageCount),
    ) {
        ComicsReaderState(
            initialPage = safeInitialPage,
            pageCount = safePageCount,
            initialReaderMode = initialReaderMode,
            initialReadingDirection = initialReadingDirection,
            initialPageFitMode = initialPageFitMode,
            initialSpreadMode = initialSpreadMode,
            initialPageTurnStyle = initialPageTurnStyle,
            initialSplitWidePages = initialSplitWidePages,
            initialResumeAnchorPage = initialResumeAnchorPage,
            initialResumeSegment = initialResumeSegment,
            initialResumeScrollOffsetRatio = initialResumeScrollOffsetRatio,
        )
    }
    SideEffect {
        state.syncPageCount(safePageCount)
    }
    return state
}
