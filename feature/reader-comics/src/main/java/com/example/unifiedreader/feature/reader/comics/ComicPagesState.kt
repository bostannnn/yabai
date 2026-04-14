package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.unifiedreader.core.model.ReadableItem

@Composable
internal fun rememberObservedComicPages(
    item: ReadableItem,
    pageSource: ComicPageSource,
): List<ComicPageSpec> {
    val initialPages = remember(item, pageSource) {
        pageSource.getPages(item)
    }
    val pagesFlow = remember(item, pageSource) {
        pageSource.observePages(item)
    }
    val pages by pagesFlow.collectAsState(initial = initialPages)
    return pages
}
