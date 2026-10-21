package com.example.unifiedreader.feature.reader.epub

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.formatReaderFactory
import com.example.unifiedreader.feature.reader.preferences.InMemoryReaderPageNumberPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ReaderPageNumberPreferencesStore
import com.example.unifiedreader.journal.JournalReadingSessionRecorder

fun epubReaderFactory(
    sessionStore: ReaderSessionStore,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    documentUriProvider: suspend (ReadableItem) -> Uri? = { null },
    pageNumberPreferences: ReaderPageNumberPreferencesStore = InMemoryReaderPageNumberPreferencesStore(),
): ReaderScreenFactory = formatReaderFactory(
    FormatType.EPUB,
    chrome = ReaderChrome.Immersive,
) { item, onBack, navigationActions ->
        EpubReaderScreen(
            item = item,
            sessionStore = sessionStore,
            journalReadingSessionRecorder = journalReadingSessionRecorder,
            documentUriProvider = documentUriProvider,
            pageNumberPreferences = pageNumberPreferences,
            onBack = onBack,
            onCompletionRequested = navigationActions.onRequestCompletion,
        )
}
