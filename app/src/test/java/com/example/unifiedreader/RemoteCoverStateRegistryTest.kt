package com.example.unifiedreader

import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCoverStateRegistryTest {
    @Test
    fun `updating one item does not affect another item`() {
        val registry = RemoteCoverStateRegistry()

        assertNull(registry.coverUriFor("item-a", LibraryCoverImageVariant.Grid))
        assertNull(registry.coverUriFor("item-b", LibraryCoverImageVariant.Grid))

        registry.update("item-a", LibraryCoverImageVariant.Grid, "file:///tmp/item-a.png")

        assertEquals("file:///tmp/item-a.png", registry.coverUriFor("item-a", LibraryCoverImageVariant.Grid))
        assertNull(registry.coverUriFor("item-b", LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `updating one variant does not affect another variant for the same item`() {
        val registry = RemoteCoverStateRegistry()

        registry.update("item-a", LibraryCoverImageVariant.Grid, "file:///tmp/item-a-grid.jpg")

        assertEquals("file:///tmp/item-a-grid.jpg", registry.coverUriFor("item-a", LibraryCoverImageVariant.Grid))
        assertNull(registry.coverUriFor("item-a", LibraryCoverImageVariant.Detailed))
    }
}
