package com.example.unifiedreader.feature.reader.pdf

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.formatReaderFactory
import com.example.unifiedreader.journal.JournalReadingSessionRecorder

fun pdfReaderFactory(
    sessionStore: ReaderSessionStore,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    documentUriProvider: suspend (ReadableItem) -> Uri? = { null },
): ReaderScreenFactory = formatReaderFactory(
    FormatType.PDF,
    chrome = ReaderChrome.Standard,
) { item, _, navigationActions ->
        PdfReaderScreen(
            item = item,
            documentUriProvider = documentUriProvider,
            sessionStore = sessionStore,
            journalReadingSessionRecorder = journalReadingSessionRecorder,
            openMode = navigationActions.currentOpenMode,
            onCompletionRequested = navigationActions.onRequestCompletion,
        )
}
