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
    variant: LibraryCoverImageVariant,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    val signaledPrefetchKeys = remember(browseContextKey) { mutableSetOf<String>() }

    LaunchedEffect(coverLoadingEnabled, targets, variant, onCoverVisible) {
        if (!coverLoadingEnabled || targets.isEmpty()) return@LaunchedEffect
        val unsignaledTargets = targets.filterNot { target ->
            target.prefetchKey in signaledPrefetchKeys
        }
        unsignaledTargets.forEachIndexed { index, target ->
            if (index > 0) {
                delay(SHELF_PREVIEW_PREFETCH_STAGGER_MILLIS)
            }
            signaledPrefetchKeys += target.prefetchKey
            onCoverVisible(target.item, variant)
        }
    }
}

/** The covers each visible shelf row will show (at most [itemLimitPerSection] each), deduped across rows. */
internal fun shelfPreviewCoverPrefetchTargets(
    sections: List<List<ReadableItem>>,
    itemLimitPerSection: Int,
    variant: LibraryCoverImageVariant,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<ShelfPreviewCoverPrefetchTarget> = sections
    .flatMap { items -> items.take(itemLimitPerSection) }
    .distinctBy(ReadableItem::id)
    .mapNotNull { item ->
        if (item.sourceType == SourceType.LOCAL) return@mapNotNull null
        val prefetchKey = coverPrefetchKeyProvider(item, variant)
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

private const val SHELF_PREVIEW_PREFETCH_STAGGER_MILLIS = 24L
