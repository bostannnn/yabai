package com.example.unifiedreader.feature.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    coverPrefetchKeyProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    maxTargetsPerPass: Int = VISIBLE_COVER_PREFETCH_TARGET_LIMIT,
) {
    val prefetchedKeys = remember(browseContextKey) { mutableSetOf<String>() }

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
                targets.take(maxTargetsPerPass).forEach { target ->
                    if (prefetchedKeys.add(target.prefetchKey)) {
                        onCoverVisible(target.item)
                    }
                }
            }
    }
}

internal fun visibleRemoteCoverPrefetchTargets(
    visibleGridItemIndices: List<Int>,
    itemIndexOffset: Int,
    items: List<ReadableItem>,
    coverPrefetchKeyProvider: (ReadableItem) -> String?,
): List<RemoteCoverPrefetchTarget> = visibleGridItemIndices
    .asSequence()
    .map { visibleIndex -> visibleIndex - itemIndexOffset }
    .filter { itemIndex -> itemIndex in items.indices }
    .map(items::get)
    .mapNotNull { item ->
        val prefetchKey = coverPrefetchKeyProvider(item)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        if (!shouldPrefetchRemoteCover(item)) return@mapNotNull null
        RemoteCoverPrefetchTarget(
            item = item,
            prefetchKey = prefetchKey,
        )
    }
    .distinctBy(RemoteCoverPrefetchTarget::prefetchKey)
    .toList()

internal data class RemoteCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
)

internal const val REMOTE_COVER_PREFETCH_DELAY_MILLIS = 180L
internal const val VISIBLE_COVER_PREFETCH_TARGET_LIMIT = 4
