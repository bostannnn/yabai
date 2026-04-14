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
                pendingRemoteCoverPrefetchTargets(
                    targets = targets,
                    prefetchedKeys = prefetchedKeys,
                ).forEach { target ->
                    if (prefetchedKeys.add(target.prefetchKey)) {
                        onCoverVisible(target.item)
                        delay(REMOTE_COVER_PREFETCH_STAGGER_MILLIS)
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

internal fun pendingRemoteCoverPrefetchTargets(
    targets: List<RemoteCoverPrefetchTarget>,
    prefetchedKeys: Set<String>,
): List<RemoteCoverPrefetchTarget> = targets.filterNot { target ->
    target.prefetchKey in prefetchedKeys
}

internal data class RemoteCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
)

internal const val REMOTE_COVER_PREFETCH_DELAY_MILLIS = 180L
internal const val REMOTE_COVER_PREFETCH_STAGGER_MILLIS = 24L
