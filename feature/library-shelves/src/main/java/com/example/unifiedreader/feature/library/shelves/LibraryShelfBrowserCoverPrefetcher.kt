package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LocalLibraryCoverGeneration
import com.example.unifiedreader.feature.library.libraryGridCoverImageVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Asks for the covers See all shows, a few at a time while it scrolls, like the library grid's
 * prefetcher. Row 0 of the grid or list is the tabs row, so entries start at index 1.
 */
@Composable
internal fun ShelfBrowserVisibleCoverPrefetcher(
    browseContextKey: String,
    showListView: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    entries: List<LibraryShelfBrowserEntry>,
    variant: LibraryCoverImageVariant,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onCoverViewportScrollingChanged: (Boolean) -> Unit,
) {
    val signaledPrefetchKeys = remember(browseContextKey, variant) { mutableSetOf<String>() }

    LaunchedEffect(showListView, gridState, listState, onCoverViewportScrollingChanged) {
        snapshotFlow {
            if (showListView) {
                listState.isScrollInProgress
            } else {
                gridState.isScrollInProgress
            }
        }
            .distinctUntilChanged()
            .collect { isScrolling -> onCoverViewportScrollingChanged(isScrolling) }
    }
    DisposableEffect(showListView, onCoverViewportScrollingChanged) {
        onDispose {
            onCoverViewportScrollingChanged(false)
        }
    }

    LaunchedEffect(
        browseContextKey,
        showListView,
        gridState,
        listState,
        entries,
        variant,
        coverPrefetchKeyProvider,
        onCoverVisible,
        LocalLibraryCoverGeneration.current,
    ) {
        snapshotFlow {
            if (showListView) {
                ShelfBrowserCoverPrefetchRequest(
                    visibleItemIndices = listState.layoutInfo.visibleItemsInfo.map { visibleItem -> visibleItem.index },
                    isScrollInProgress = listState.isScrollInProgress,
                    scrollDirection = when {
                        listState.lastScrolledForward -> ShelfBrowserCoverPrefetchScrollDirection.Forward
                        listState.lastScrolledBackward -> ShelfBrowserCoverPrefetchScrollDirection.Backward
                        else -> ShelfBrowserCoverPrefetchScrollDirection.Idle
                    },
                )
            } else {
                ShelfBrowserCoverPrefetchRequest(
                    visibleItemIndices = gridState.layoutInfo.visibleItemsInfo.map { visibleItem -> visibleItem.index },
                    isScrollInProgress = gridState.isScrollInProgress,
                    scrollDirection = when {
                        gridState.lastScrolledForward -> ShelfBrowserCoverPrefetchScrollDirection.Forward
                        gridState.lastScrolledBackward -> ShelfBrowserCoverPrefetchScrollDirection.Backward
                        else -> ShelfBrowserCoverPrefetchScrollDirection.Idle
                    },
                )
            }
        }
            .distinctUntilChanged()
            .collectLatest { request ->
                val targets = shelfBrowserCoverPrefetchTargets(
                    visibleItemIndices = request.visibleItemIndices,
                    entries = entries,
                    variant = variant,
                    isScrollInProgress = request.isScrollInProgress,
                    scrollDirection = request.scrollDirection,
                    maxTargetsWhileScrolling = ShelfBrowserCoverPrefetchMaxTargetsWhileScrolling,
                    coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                )
                    .filterNot { target -> target.prefetchKey in signaledPrefetchKeys }
                if (targets.isEmpty()) return@collectLatest
                delay(
                    if (request.isScrollInProgress) {
                        ShelfBrowserCoverPrefetchScrollDelayMillis
                    } else {
                        ShelfBrowserCoverPrefetchDelayMillis
                    },
                )
                targets.forEach { target ->
                    signaledPrefetchKeys += target.prefetchKey
                    onCoverVisible(target.item, target.variant)
                    delay(
                        if (request.isScrollInProgress) {
                            ShelfBrowserCoverPrefetchScrollStaggerMillis
                        } else {
                            ShelfBrowserCoverPrefetchStaggerMillis
                        },
                    )
                }
            }
    }
}

private fun shelfBrowserCoverPrefetchTargets(
    visibleItemIndices: List<Int>,
    entries: List<LibraryShelfBrowserEntry>,
    variant: LibraryCoverImageVariant,
    isScrollInProgress: Boolean,
    scrollDirection: ShelfBrowserCoverPrefetchScrollDirection,
    maxTargetsWhileScrolling: Int,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
): List<ShelfBrowserCoverPrefetchTarget> = visibleItemIndices
    .asSequence()
    .map { index -> index - ShelfBrowserLeadingRows }
    .filter { index -> index in entries.indices }
    .map { index -> entries[index].item }
    .mapNotNull { item ->
        val prefetchKey = coverPrefetchKeyProvider(item, variant)
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        if (item.sourceType == SourceType.LOCAL) return@mapNotNull null
        ShelfBrowserCoverPrefetchTarget(
            item = item,
            prefetchKey = prefetchKey,
            variant = variant,
        )
    }
    .distinctBy(ShelfBrowserCoverPrefetchTarget::prefetchKey)
    .toList()
    .let { targets ->
        if (!isScrollInProgress || maxTargetsWhileScrolling >= targets.size) {
            targets
        } else {
            when (scrollDirection) {
                ShelfBrowserCoverPrefetchScrollDirection.Forward -> targets.takeLast(maxTargetsWhileScrolling)
                ShelfBrowserCoverPrefetchScrollDirection.Backward -> targets.take(maxTargetsWhileScrolling)
                ShelfBrowserCoverPrefetchScrollDirection.Idle -> targets.take(maxTargetsWhileScrolling)
            }
        }
    }

internal fun shelfBrowserCoverImageVariant(
    showListView: Boolean,
    gridCellSizeDp: Int,
): LibraryCoverImageVariant = if (showListView) {
    LibraryCoverImageVariant.ShelfRow
} else {
    libraryGridCoverImageVariant(gridCellSizeDp)
}

private data class ShelfBrowserCoverPrefetchTarget(
    val item: ReadableItem,
    val prefetchKey: String,
    val variant: LibraryCoverImageVariant,
)

private data class ShelfBrowserCoverPrefetchRequest(
    val visibleItemIndices: List<Int>,
    val isScrollInProgress: Boolean,
    val scrollDirection: ShelfBrowserCoverPrefetchScrollDirection,
)

private enum class ShelfBrowserCoverPrefetchScrollDirection {
    Forward,
    Backward,
    Idle,
}

/** The tabs row above the tiles in See all's grid and list. */
internal const val ShelfBrowserLeadingRows = 1

private const val ShelfBrowserCoverPrefetchMaxTargetsWhileScrolling = 6
private const val ShelfBrowserCoverPrefetchDelayMillis = 48L
private const val ShelfBrowserCoverPrefetchStaggerMillis = 16L
private const val ShelfBrowserCoverPrefetchScrollDelayMillis = 96L
private const val ShelfBrowserCoverPrefetchScrollStaggerMillis = 32L
