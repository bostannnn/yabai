package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderNavigationActions
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.journal.JournalReadingSessionRecorder

fun comicsReaderFactory(
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
): ReaderScreenFactory {
    val supportedFormats = setOf(FormatType.CBZ, FormatType.CBR)
    return object : ReaderScreenFactory {
        override fun supports(item: ReadableItem): Boolean =
            item.format in supportedFormats

        override val chrome: ReaderChrome = ReaderChrome.Immersive

        @Composable
        override fun Content(
            item: ReadableItem,
            onBack: () -> Unit,
            navigationActions: ReaderNavigationActions,
        ) {
            ComicsReaderScreen(
                item = item,
                pageSource = pageSource,
                progressStore = progressStore,
                preferencesStore = preferencesStore,
                journalReadingSessionRecorder = journalReadingSessionRecorder,
                onBack = onBack,
                openMode = navigationActions.currentOpenMode,
                readerWorkCoordinator = navigationActions.readerWorkCoordinator,
                onNextItemRequested = navigationActions.onRequestCompletion,
            )
        }
    }
}
