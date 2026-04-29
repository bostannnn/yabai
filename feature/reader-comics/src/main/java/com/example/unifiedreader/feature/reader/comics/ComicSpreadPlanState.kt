package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.unifiedreader.core.model.ReadableItem

internal data class ComicSpreadPlanState(
    val metadataByPageId: Map<String, ComicPageMetadata>,
)

@Composable
internal fun rememberComicReaderPreparationState(
    item: ReadableItem,
    pages: List<ComicPageSpec>,
    pageSource: ComicPageSource,
): ComicReaderPreparationState {
    var preparationState by remember(item.id, pages, pageSource) {
        mutableStateOf<ComicReaderPreparationState>(
            ComicReaderPreparationState.Preparing(
                loadedPageCount = 0,
                totalPageCount = pages.size,
            ),
        )
    }

    LaunchedEffect(item.id, pages, pageSource) {
        preparationState = ComicReaderPreparationState.Preparing(
            loadedPageCount = 0,
            totalPageCount = pages.size,
        )
        val spreadPlanState = prepareComicSpreadPlanState(
            item = item,
            pages = pages,
            pageSource = pageSource,
            onProgress = { progress ->
                preparationState = ComicReaderPreparationState.Preparing(
                    loadedPageCount = progress.loadedPageCount,
                    totalPageCount = progress.totalPageCount,
                )
            },
        )
        preparationState = ComicReaderPreparationState.Ready(spreadPlanState)
    }

    return preparationState
}
