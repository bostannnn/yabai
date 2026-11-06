package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.ReaderOpenFailure
import com.example.unifiedreader.feature.reader.core.ReaderOpenStatus
import com.example.unifiedreader.feature.reader.core.ReaderOpeningStep
import com.example.unifiedreader.ui.theme.ReaderPageColors
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

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
    val pages by pagesFlow.collectAsStateWithLifecycle(initialValue = initialPages)
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
    val diagnostic by diagnosticFlow.collectAsStateWithLifecycle(initialValue = initialDiagnostic)
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

/**
 * The comics canvas as the opening and error screens use it. The canvas is dark in every theme,
 * so the text stays light; the raised surface takes the canvas's hue.
 */
@Composable
internal fun comicReaderPageColors(): ReaderPageColors {
    val canvas = UnifiedReaderThemeTokens.colors.readerCanvas
    return remember(canvas) {
        ReaderPageColors(
            page = canvas,
            text = ComicReaderText,
            number = Color(0xFF8A9099),
            raised = lerp(canvas, ComicReaderText, 0.08f),
        )
    }
}

private val ComicReaderText = Color(0xFFE7EAEE)
