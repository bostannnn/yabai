package com.example.unifiedreader.feature.reader.core

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem

fun formatReaderFactory(
    vararg formats: FormatType,
    chrome: ReaderChrome,
    content: @Composable (item: ReadableItem, onBack: () -> Unit) -> Unit,
): ReaderScreenFactory {
    val supportedFormats = formats.toSet()
    return object : ReaderScreenFactory {
        override fun supports(item: ReadableItem): Boolean = item.format in supportedFormats

        override val chrome: ReaderChrome = chrome

        @Composable
        override fun Content(
            item: ReadableItem,
            onBack: () -> Unit,
            navigationActions: ReaderNavigationActions,
        ) {
            content(item, onBack)
        }
    }
}
