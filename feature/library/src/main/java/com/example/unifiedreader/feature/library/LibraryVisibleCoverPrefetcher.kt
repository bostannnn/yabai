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
    variant: LibraryCoverImageVariant,
    maxTargetsWhileScrolling: Int,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
) {
    val signaledPrefetchKeys = remember(browseContextKey) { mutableSetOf<String>() }

    LaunchedEffect(
        browseContextKey,
        gridState,
        items,
        leadingContentItemCount,
        coverPrefetchKeyProvider,
        onCoverVisible,
    ) {
        snapshotFlow {
            VisibleRemoteCoverPrefetchRequest(
                visibleGridItemIndices = gridState.layoutInfo.visibleItemsInfo.map { it.index },
                isScrollInProgress = gridState.isScrollInProgress,
                scrollDirection = when {
                    gridState.lastScrolledForward -> RemoteCoverPrefetchScrollDirection.Forward
                    gridState.lastScrolledBackward -> RemoteCoverPrefetchScrollDirection.Backward
                    else -> RemoteCoverPrefetchScrollDirection.Idle
                },
            )
        }
            .distinctUntilChanged()
            .collectLatest { request ->
                val targets = visibleRemoteCoverPrefetchTargets(
                    visibleGridItemIndices = request.visibleGridItemIndices,
                    itemIndexOffset = leadingContentItemCount,
                    items = items,
                    variant = variant,
                    isScrollInProgress = request.isScrollInProgress,
                    scrollDirection = request.scrollDirection,
                    maxTargetsWhileScrolling = maxTargetsWhileScrolling,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                )
                val unsignaledTargets = unsignaledRemoteCoverPrefetchTargets(
                    targets = targets,
                    signaledPrefetchKeys = signaledPrefetchKeys,
                )
                if (unsignaledTargets.isEmpty()) return@collectLatest
                delay(
                    if (request.isScrollInProgress) {
                        REMOTE_COVER_PREFETCH_SCROLL_DELAY_MILLIS
                    } else {
                        REMOTE_COVER_PREFETCH_DELAY_MILLIS
                    },
                )
                unsignaledTargets.forEach { target ->
                    signaledPrefetchKeys += target.prefetchKey
                    onCoverVisible(target.item, target.variant)
                    delay(
                        if (request.isScrollInProgress) {
                            REMOTE_COVER_PREFETCH_SCROLL_STAGGER_MILLIS
                        } else {
                            REMOTE_COVER_PREFETCH_STAGGER_MILLIS
                        },
                    )
                }
            }
    }
}

internal fun unsignaledRemoteCoverPrefetchTargets(
    targets: List<RemoteCoverPrefetchTarget>,
    signaledPrefetchKeys: Set<String>,
): List<RemoteCoverPrefetchTarget> = targets.filterNot { target ->
    target.prefetchKey in signaledPrefetchKeys
}

internal fun visibleRemoteCoverPrefetchTargets(
    visibleGridItemIndices: List<Int>,
    itemIndexOffset: Int,
    items: List<ReadableItem>,
    variant: LibraryCoverImageVariant,
    isScrollInProgress: Boolean,
    scrollDirection: RemoteCoverPrefetchScrollDirection,
    maxTargetsWhileScrolling: Int,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<RemoteCoverPrefetchTarget> = visibleGridItemIndices
    .asSequence()
    .map { visibleIndex -> visibleIndex - itemIndexOffset }
    .filter { itemIndex -> itemIndex in items.indices }
    .map(items::get)
    .mapNotNull { item ->
        val prefetchKey = coverPrefetchKeyProvider(item, variant)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        if (!shouldPrefetchRemoteCover(item)) return@mapNotNull null
        RemoteCoverPrefetchTarget(
            item = item,
            prefetchKey = prefetchKey,
            variant = variant,
        )
    }
    .distinctBy(RemoteCoverPrefetchTarget::prefetchKey)
    .toList()
    .let { targets ->
        if (!isScrollInProgress || maxTargetsWhileScrolling >= targets.size) {
            targets
        } else {
            when (scrollDirection) {
                RemoteCoverPrefetchScrollDirection.Forward -> targets.takeLast(maxTargetsWhileScrolling)
                RemoteCoverPrefetchScrollDirection.Backward -> targets.take(maxTargetsWhileScrolling)
                RemoteCoverPrefetchScrollDirection.Idle -> targets.take(maxTargetsWhileScrolling)
            }
        }
    }

internal data class RemoteCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
    val variant: LibraryCoverImageVariant,
)

internal data class VisibleRemoteCoverPrefetchRequest(
    val visibleGridItemIndices: List<Int>,
    val isScrollInProgress: Boolean,
    val scrollDirection: RemoteCoverPrefetchScrollDirection,
)

internal enum class RemoteCoverPrefetchScrollDirection {
    Forward,
    Backward,
    Idle,
}

internal const val REMOTE_COVER_PREFETCH_DELAY_MILLIS = 48L
internal const val REMOTE_COVER_PREFETCH_STAGGER_MILLIS = 16L
internal const val REMOTE_COVER_PREFETCH_SCROLL_DELAY_MILLIS = 96L
internal const val REMOTE_COVER_PREFETCH_SCROLL_STAGGER_MILLIS = 32L
