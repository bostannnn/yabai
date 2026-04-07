package com.example.unifiedreader.feature.reader.epub

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.formatReaderFactory

fun epubReaderFactory(
    sessionStore: ReaderSessionStore,
    documentUriProvider: suspend (ReadableItem) -> Uri? = { null },
): ReaderScreenFactory = formatReaderFactory(
    FormatType.EPUB,
    chrome = ReaderChrome.Immersive,
) { item, onBack ->
        EpubReaderScreen(
            item = item,
            sessionStore = sessionStore,
            documentUriProvider = documentUriProvider,
            onBack = onBack,
        )
}
