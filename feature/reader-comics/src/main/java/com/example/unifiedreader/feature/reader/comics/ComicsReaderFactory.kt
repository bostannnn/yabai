package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.formatReaderFactory

fun comicsReaderFactory(
    pageSource: ComicPageSource,
    progressStore: ComicReaderProgressStore,
    preferencesStore: ComicReaderPreferencesStore,
): ReaderScreenFactory = formatReaderFactory(
    FormatType.CBZ,
    FormatType.CBR,
    chrome = ReaderChrome.Immersive,
) { item, onBack ->
        ComicsReaderScreen(
            item = item,
            pageSource = pageSource,
            progressStore = progressStore,
            preferencesStore = preferencesStore,
            onBack = onBack,
        )
}
