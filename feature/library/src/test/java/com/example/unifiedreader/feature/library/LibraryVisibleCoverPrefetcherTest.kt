package com.example.unifiedreader.feature.library

import androidx.paging.ItemSnapshotList
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryVisibleCoverPrefetcherTest {
    @Test
    fun `scrolling forward only prefetches trailing visible items`() {
        val items = List(6) { index -> remoteItem(index) }

        val targets = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = listOf(0, 1, 2, 3, 4, 5),
            itemIndexOffset = 0,
            items = items,
            variant = LibraryCoverImageVariant.Grid,
            isScrollInProgress = true,
            scrollDirection = RemoteCoverPrefetchScrollDirection.Forward,
            maxTargetsWhileScrolling = 2,
            coverPrefetchKeyProvider = { item, _ -> item.id },
        )

        assertEquals(listOf("item-4", "item-5"), targets.map { target -> target.item.id })
    }

    @Test
    fun `idle prefetch keeps all visible items`() {
        val items = List(4) { index -> remoteItem(index) }

        val targets = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = listOf(0, 1, 2, 3),
            itemIndexOffset = 0,
            items = items,
            variant = LibraryCoverImageVariant.Grid,
            isScrollInProgress = false,
            scrollDirection = RemoteCoverPrefetchScrollDirection.Idle,
            maxTargetsWhileScrolling = 2,
            coverPrefetchKeyProvider = { item, _ -> item.id },
        )

        assertEquals(listOf("item-0", "item-1", "item-2", "item-3"), targets.map { target -> target.item.id })
    }

    @Test
    fun `paged grid slots map past unloaded placeholders after a rail jump`() {
        // A rail jump to slot 200 loads one page there; every slot before it is still a placeholder.
        val snapshot = ItemSnapshotList(
            placeholdersBefore = 200,
            placeholdersAfter = 100,
            items = List(6) { index -> remoteItem(200 + index) },
        )
        // One leading header row: grid indices 199..206 are slots 198..205.
        val visibleGridItemIndices = (199..206).toList()

        val targets = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = visibleGridItemIndices,
            itemIndexOffset = 1,
            items = snapshot,
            variant = LibraryCoverImageVariant.Grid,
            isScrollInProgress = false,
            scrollDirection = RemoteCoverPrefetchScrollDirection.Idle,
            maxTargetsWhileScrolling = 2,
            coverPrefetchKeyProvider = { item, _ -> item.id },
        )
        // The loaded items alone lose the slot positions and match nothing on screen.
        val targetsFromLoadedItemsOnly = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = visibleGridItemIndices,
            itemIndexOffset = 1,
            items = snapshot.items,
            variant = LibraryCoverImageVariant.Grid,
            isScrollInProgress = false,
            scrollDirection = RemoteCoverPrefetchScrollDirection.Idle,
            maxTargetsWhileScrolling = 2,
            coverPrefetchKeyProvider = { item, _ -> item.id },
        )

        assertEquals((200..205).map { index -> "item-$index" }, targets.map { target -> target.item.id })
        assertEquals(emptyList<String>(), targetsFromLoadedItemsOnly.map { target -> target.item.id })
    }

    @Test
    fun `unsignaled targets exclude already requested keys`() {
        val targets = listOf(
            RemoteCoverPrefetchTarget(
                item = remoteItem(0),
                prefetchKey = "item-0",
                variant = LibraryCoverImageVariant.Grid,
            ),
            RemoteCoverPrefetchTarget(
                item = remoteItem(1),
                prefetchKey = "item-1",
                variant = LibraryCoverImageVariant.Grid,
            ),
            RemoteCoverPrefetchTarget(
                item = remoteItem(2),
                prefetchKey = "item-2",
                variant = LibraryCoverImageVariant.Grid,
            ),
        )

        val unsignaledTargets = unsignaledRemoteCoverPrefetchTargets(
            targets = targets,
            signaledPrefetchKeys = setOf("item-0", "item-2"),
        )

        assertEquals(listOf("item-1"), unsignaledTargets.map { target -> target.prefetchKey })
    }

    private fun remoteItem(
        index: Int,
    ): ReadableItem = ReadableItem(
        id = "item-$index",
        sourceType = SourceType.STUMP,
        libraryId = "library",
        remoteId = "remote-$index",
        remoteFileId = "file-$index",
        title = "Item $index",
        format = FormatType.CBZ,
        coverUrl = "https://example.com/$index.jpg",
    )
}
