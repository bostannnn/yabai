package com.example.unifiedreader.feature.reader.pdf

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.formatReaderFactory
import com.example.unifiedreader.feature.reader.preferences.InMemoryReaderPageNumberPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ReaderPageNumberPreferencesStore
import com.example.unifiedreader.journal.JournalReadingSessionRecorder

fun pdfReaderFactory(
    sessionStore: ReaderSessionStore,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    documentUriProvider: suspend (ReadableItem) -> Uri? = { null },
    pageNumberPreferences: ReaderPageNumberPreferencesStore = InMemoryReaderPageNumberPreferencesStore(),
): ReaderScreenFactory = formatReaderFactory(
    FormatType.PDF,
    chrome = ReaderChrome.Immersive,
) { item, onBack, navigationActions ->
        PdfReaderScreen(
            item = item,
            documentUriProvider = documentUriProvider,
            sessionStore = sessionStore,
            journalReadingSessionRecorder = journalReadingSessionRecorder,
            pageNumberPreferences = pageNumberPreferences,
            openMode = navigationActions.currentOpenMode,
            onBack = onBack,
            onCompletionRequested = navigationActions.onRequestCompletion,
        )
}
