package com.example.unifiedreader.feature.reader.pdf

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderChrome
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.formatReaderFactory

fun pdfReaderFactory(
    documentUriProvider: (ReadableItem) -> Uri? = { null },
): ReaderScreenFactory = formatReaderFactory(
    FormatType.PDF,
    chrome = ReaderChrome.Standard,
) { item, _ ->
        PdfReaderScreen(
            item = item,
            documentUri = documentUriProvider(item),
        )
}
