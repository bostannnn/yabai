package com.example.unifiedreader.feature.library.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class VisibleLibraryCoverRegistryTest {
    private val registry = VisibleLibraryCoverRegistry()

    @Test
    fun `resolved cover promotion is deferred while viewport is scrolling when fallback is already displayed`() {
        registry.rememberDisplayedFallbackUri(
            itemId = "item-1",
            variant = LibraryCoverVariant.Grid,
            uri = "https://fallback.example/cover.jpg",
        )
        registry.onViewportScrollingChanged(true)

        registry.promoteResolvedUri(
            itemId = "item-1",
            variant = LibraryCoverVariant.Grid,
            uri = "file:///cached/cover.jpg",
        )

        assertEquals(
            "https://fallback.example/cover.jpg",
            registry.presentedUriFor("item-1", LibraryCoverVariant.Grid),
        )

        registry.onViewportScrollingChanged(false)

        assertEquals(
            "file:///cached/cover.jpg",
            registry.presentedUriFor("item-1", LibraryCoverVariant.Grid),
        )
    }

    @Test
    fun `resolved cover promotion from empty presentation is deferred while viewport is scrolling`() {
        registry.onViewportScrollingChanged(true)

        registry.promoteResolvedUri(
            itemId = "item-2",
            variant = LibraryCoverVariant.Grid,
            uri = "file:///cached/cover.jpg",
        )

        assertNull(registry.presentedUriFor("item-2", LibraryCoverVariant.Grid))

        registry.onViewportScrollingChanged(false)

        assertEquals(
            "file:///cached/cover.jpg",
            registry.presentedUriFor("item-2", LibraryCoverVariant.Grid),
        )
    }

    @Test
    fun `latest pending promotion wins when multiple resolved covers arrive while scrolling`() {
        registry.rememberDisplayedFallbackUri(
            itemId = "item-5",
            variant = LibraryCoverVariant.Grid,
            uri = "https://fallback.example/cover.jpg",
        )
        registry.onViewportScrollingChanged(true)

        registry.promoteResolvedUri(
            itemId = "item-5",
            variant = LibraryCoverVariant.Grid,
            uri = "file:///cached/old-cover.jpg",
        )
        registry.promoteResolvedUri(
            itemId = "item-5",
            variant = LibraryCoverVariant.Grid,
            uri = "file:///cached/new-cover.jpg",
        )

        assertEquals(
            "https://fallback.example/cover.jpg",
            registry.presentedUriFor("item-5", LibraryCoverVariant.Grid),
        )

        registry.onViewportScrollingChanged(false)

        assertEquals(
            "file:///cached/new-cover.jpg",
            registry.presentedUriFor("item-5", LibraryCoverVariant.Grid),
        )
    }

    @Test
    fun `new fallback does not override a resolved cover already being displayed`() {
        registry.promoteResolvedUri(
            itemId = "item-3",
            variant = LibraryCoverVariant.Detail,
            uri = "file:///cached/detail.jpg",
        )

        registry.rememberDisplayedFallbackUri(
            itemId = "item-3",
            variant = LibraryCoverVariant.Detail,
            uri = "https://fallback.example/detail.jpg",
        )

        assertEquals(
            "file:///cached/detail.jpg",
            registry.presentedUriFor("item-3", LibraryCoverVariant.Detail),
        )
    }

    @Test
    fun `querying a missing cover creates an entry for later promotion`() {
        assertFalse(registry.containsEntry("item-4", LibraryCoverVariant.Grid))

        assertNull(registry.presentedUriFor("item-4", LibraryCoverVariant.Grid))

        assertTrue(registry.containsEntry("item-4", LibraryCoverVariant.Grid))
    }

    private fun VisibleLibraryCoverRegistry.containsEntry(
        itemId: String,
        variant: LibraryCoverVariant,
    ): Boolean {
        val entriesField = VisibleLibraryCoverRegistry::class.java.getDeclaredField("entries")
        entriesField.isAccessible = true
        val entries = entriesField.get(this) as Map<*, *>
        return entries.keys.any { key ->
            val keyClass = key?.javaClass ?: return@any false
            val itemIdField = keyClass.getDeclaredField("itemId").apply { isAccessible = true }
            val variantField = keyClass.getDeclaredField("variant").apply { isAccessible = true }
            itemIdField.get(key) == itemId && variantField.get(key) == variant
        }
    }
}
