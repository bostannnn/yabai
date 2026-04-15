package com.example.unifiedreader.feature.library.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import kotlinx.coroutines.delay

@Composable
internal fun LibraryShelvesPreviewCoverPrefetcher(
    coverLoadingEnabled: Boolean,
    items: List<ReadableItem>,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    LaunchedEffect(coverLoadingEnabled, items, onCoverVisible) {
        if (!coverLoadingEnabled || items.isEmpty()) return@LaunchedEffect
        items.forEachIndexed { index, item ->
            if (index > 0) {
                delay(SHELF_PREVIEW_PREFETCH_STAGGER_MILLIS)
            }
            onCoverVisible(item, LibraryCoverImageVariant.ShelfPreview)
        }
    }
}

internal fun shelfPreviewCoverPrefetchItems(
    state: LibraryShelvesState,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<ReadableItem> = buildList {
    addAll(
        state.continueReading
            .asSequence()
            .map(ContinueReadingShelfItem::item)
            .take(SHELF_PREVIEW_PREFETCH_LIMIT_PER_SECTION)
            .toList(),
    )
    addAll(
        state.nextInSeries
            .asSequence()
            .map(NextInSeriesShelfItem::item)
            .take(SHELF_PREVIEW_PREFETCH_LIMIT_PER_SECTION)
            .toList(),
    )
    addAll(
        state.recentlyFinished
            .asSequence()
            .map(RecentlyFinishedShelfItem::item)
            .take(SHELF_PREVIEW_PREFETCH_LIMIT_PER_SECTION)
            .toList(),
    )
}
    .distinctBy(ReadableItem::id)
    .filter { item ->
        item.sourceType != SourceType.LOCAL
    }

internal const val SHELF_PREVIEW_PREFETCH_LIMIT_PER_SECTION = 4
private const val SHELF_PREVIEW_PREFETCH_STAGGER_MILLIS = 24L
