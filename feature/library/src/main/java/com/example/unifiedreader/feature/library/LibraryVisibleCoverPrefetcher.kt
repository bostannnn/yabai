package com.example.unifiedreader.feature.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun VisibleRemoteCoverPrefetcher(
    browseContextKey: String,
    gridState: LazyGridState,
    items: List<ReadableItem>,
    leadingContentItemCount: Int,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    LaunchedEffect(
        browseContextKey,
        gridState,
        items,
        leadingContentItemCount,
        coverPrefetchKeyProvider,
        onCoverVisible,
    ) {
        snapshotFlow {
            visibleRemoteCoverPrefetchTargets(
                visibleGridItemIndices = gridState.layoutInfo.visibleItemsInfo.map { it.index },
                itemIndexOffset = leadingContentItemCount,
                items = items,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            )
        }
            .distinctUntilChanged()
            .collectLatest { targets ->
                if (targets.isEmpty()) return@collectLatest
                delay(REMOTE_COVER_PREFETCH_DELAY_MILLIS)
                targets.forEach { target ->
                    onCoverVisible(target.item, target.variant)
                    delay(REMOTE_COVER_PREFETCH_STAGGER_MILLIS)
                }
            }
    }
}

internal fun visibleRemoteCoverPrefetchTargets(
    visibleGridItemIndices: List<Int>,
    itemIndexOffset: Int,
    items: List<ReadableItem>,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<RemoteCoverPrefetchTarget> = visibleGridItemIndices
    .asSequence()
    .map { visibleIndex -> visibleIndex - itemIndexOffset }
    .filter { itemIndex -> itemIndex in items.indices }
    .map(items::get)
    .mapNotNull { item ->
        val prefetchKey = coverPrefetchKeyProvider(item, LibraryCoverImageVariant.Grid)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        if (!shouldPrefetchRemoteCover(item)) return@mapNotNull null
        RemoteCoverPrefetchTarget(
            item = item,
            prefetchKey = prefetchKey,
            variant = LibraryCoverImageVariant.Grid,
        )
    }
    .distinctBy(RemoteCoverPrefetchTarget::prefetchKey)
    .toList()

internal data class RemoteCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
    val variant: LibraryCoverImageVariant,
)

internal const val REMOTE_COVER_PREFETCH_DELAY_MILLIS = 48L
internal const val REMOTE_COVER_PREFETCH_STAGGER_MILLIS = 16L
