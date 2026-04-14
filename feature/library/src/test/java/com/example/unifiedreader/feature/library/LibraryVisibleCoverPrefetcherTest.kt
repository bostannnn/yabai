package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryVisibleCoverPrefetcherTest {
    @Test
    fun `visible prefetch targets skip leading content items and dedupe by key`() {
        val first = remoteItem(id = "kavita:item:1", key = "remote-cover:kavita:1")
        val second = remoteItem(id = "kavita:item:2", key = "remote-cover:kavita:2")
        val duplicateKey = remoteItem(id = "kavita:item:1b", key = "remote-cover:kavita:1")

        val targets = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = listOf(0, 1, 2, 3, 4),
            itemIndexOffset = 2,
            items = listOf(first, second, duplicateKey),
            coverPrefetchKeyProvider = { item -> item.description },
        )

        assertEquals(listOf("kavita:item:1", "kavita:item:2"), targets.map { it.item.id })
    }

    @Test
    fun `visible prefetch targets ignore local items and blank keys`() {
        val local = ReadableItem(
            id = "local:item:1",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            title = "Local",
            format = FormatType.EPUB,
            description = "ignored",
        )
        val remoteWithoutKey = remoteItem(id = "kavita:item:3", key = "")

        val targets = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = listOf(0, 1),
            itemIndexOffset = 0,
            items = listOf(local, remoteWithoutKey),
            coverPrefetchKeyProvider = { item -> item.description },
        )

        assertEquals(emptyList<RemoteCoverPrefetchTarget>(), targets)
    }

    @Test
    fun `visible prefetch targets include every visible remote item`() {
        val items = (1..8).map { index ->
            remoteItem(id = "kavita:item:$index", key = "remote-cover:kavita:$index")
        }

        val targets = visibleRemoteCoverPrefetchTargets(
            visibleGridItemIndices = items.indices.toList(),
            itemIndexOffset = 0,
            items = items,
            coverPrefetchKeyProvider = { item -> item.description },
        )

        assertEquals(items.map(ReadableItem::id), targets.map { target -> target.item.id })
    }

    @Test
    fun `pending prefetch targets skip only keys already requested`() {
        val first = remoteItem(id = "kavita:item:1", key = "remote-cover:kavita:1")
        val second = remoteItem(id = "kavita:item:2", key = "remote-cover:kavita:2")
        val targets = listOf(
            RemoteCoverPrefetchTarget(first, first.description.orEmpty()),
            RemoteCoverPrefetchTarget(second, second.description.orEmpty()),
        )

        val pending = pendingRemoteCoverPrefetchTargets(
            targets = targets,
            prefetchedKeys = setOf("remote-cover:kavita:1"),
        )

        assertEquals(listOf("kavita:item:2"), pending.map { target -> target.item.id })
    }
}

private fun remoteItem(
    id: String,
    key: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:k-comics",
    remoteId = id,
    title = id,
    format = FormatType.CBZ,
    description = key,
    coverUrl = "https://example.com/$id.jpg",
)
