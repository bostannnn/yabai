package com.example.unifiedreader.feature.library.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import kotlinx.coroutines.delay

@Composable
internal fun LibraryShelvesPreviewCoverPrefetcher(
    browseContextKey: String,
    coverLoadingEnabled: Boolean,
    targets: List<ShelfPreviewCoverPrefetchTarget>,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    val signaledPrefetchKeys = remember(browseContextKey) { mutableSetOf<String>() }

    LaunchedEffect(coverLoadingEnabled, targets, onCoverVisible) {
        if (!coverLoadingEnabled || targets.isEmpty()) return@LaunchedEffect
        val unsignaledTargets = targets.filterNot { target ->
            target.prefetchKey in signaledPrefetchKeys
        }
        unsignaledTargets.forEachIndexed { index, target ->
            if (index > 0) {
                delay(SHELF_PREVIEW_PREFETCH_STAGGER_MILLIS)
            }
            signaledPrefetchKeys += target.prefetchKey
            onCoverVisible(target.item, LibraryCoverImageVariant.ShelfPreview)
        }
    }
}

internal fun shelfPreviewCoverPrefetchTargets(
    state: LibraryShelvesState,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<ShelfPreviewCoverPrefetchTarget> = buildList {
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
    .mapNotNull { item ->
        if (item.sourceType == SourceType.LOCAL) return@mapNotNull null
        val prefetchKey = coverPrefetchKeyProvider(item, LibraryCoverImageVariant.ShelfPreview)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        ShelfPreviewCoverPrefetchTarget(
            item = item,
            prefetchKey = prefetchKey,
        )
    }

internal data class ShelfPreviewCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
)

internal const val SHELF_PREVIEW_PREFETCH_LIMIT_PER_SECTION = 4
private const val SHELF_PREVIEW_PREFETCH_STAGGER_MILLIS = 24L
