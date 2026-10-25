package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.ReaderOpenFailure
import com.example.unifiedreader.feature.reader.core.ReaderOpenStatus
import com.example.unifiedreader.feature.reader.core.ReaderOpeningStep
import com.example.unifiedreader.ui.theme.ReaderPageColors

@Composable
internal fun rememberObservedComicPages(
    item: ReadableItem,
    pageSource: ComicPageSource,
    attempt: Int = 0,
): List<ComicPageSpec> {
    val initialPages = remember(item, pageSource, attempt) {
        pageSource.getPages(item)
    }
    val pagesFlow = remember(item, pageSource, attempt) {
        pageSource.observePages(item)
    }
    val pages by pagesFlow.collectAsState(initial = initialPages)
    return pages
}

@Composable
internal fun rememberComicPagesUnavailableDiagnostic(
    item: ReadableItem,
    pageSource: ComicPageSource,
    attempt: Int = 0,
): ComicPagesUnavailableDiagnostic {
    val initialDiagnostic = remember(item, pageSource, attempt) {
        pageSource.pagesUnavailableDiagnostic(item)
    }
    val diagnosticFlow = remember(item, pageSource, attempt) {
        pageSource.observePagesUnavailableDiagnostic(item)
    }
    val diagnostic by diagnosticFlow.collectAsState(initial = initialDiagnostic)
    return diagnostic
}

/** What the reader host shows for a comic without pages: the opening while they come, else why not. */
internal fun ComicPagesUnavailableDiagnostic.openStatus(item: ReadableItem): ReaderOpenStatus = when (kind) {
    ComicPagesUnavailableKind.Loading -> ReaderOpenStatus.Opening(ReaderOpeningStep.Opening)
    ComicPagesUnavailableKind.FileGone -> ReaderOpenStatus.Failed(
        if (item.sourceType == SourceType.LOCAL) ReaderOpenFailure.LocalFileGone else ReaderOpenFailure.DownloadGone,
    )
    ComicPagesUnavailableKind.NoPages -> ReaderOpenStatus.Failed(ReaderOpenFailure.NoComicPages)
    ComicPagesUnavailableKind.Unsupported -> ReaderOpenStatus.Failed(ReaderOpenFailure.Unsupported(item.format))
    ComicPagesUnavailableKind.NotConnected -> ReaderOpenStatus.Failed(ReaderOpenFailure.ServerNotConnected(item.sourceType))
    ComicPagesUnavailableKind.Unavailable -> ReaderOpenStatus.Failed(ReaderOpenFailure.Unknown)
}

/** The comics canvas as the opening and error screens use it. */
internal val ComicReaderPageColors = ReaderPageColors(
    page = ComicReaderBackgroundColor,
    text = Color(0xFFE7EAEE),
    number = Color(0xFF8A9099),
    raised = Color(0xFF1A1F25),
)
