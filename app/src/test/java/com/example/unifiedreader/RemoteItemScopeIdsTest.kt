package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RemoteItemScopeIdsTest {
    private val remote = sampleItem(id = "book-1", format = FormatType.EPUB)
    private val imported = sampleItem(id = "local-1", format = FormatType.EPUB, isLocalImport = true)

    @Test
    fun `the same catalog list's scope sets are worked out once`() {
        val catalog = listOf(remote, imported)

        val itemIds = catalog.remoteItemLocalStateScopeItemIds()

        assertEquals(setOf(remote.id), itemIds)
        assertSame(itemIds, catalog.remoteItemLocalStateScopeItemIds())
        assertSame(catalog.remoteItemLocalAvailabilityScopeIds(), catalog.remoteItemLocalAvailabilityScopeIds())
        assertSame(catalog.remoteItemLocalStateSourceLibraryScopeIds(), catalog.remoteItemLocalStateSourceLibraryScopeIds())
    }

    @Test
    fun `a new catalog list gets its own scope sets, even with the same books`() {
        val catalog = listOf(remote, imported)
        val itemIds = catalog.remoteItemLocalStateScopeItemIds()

        val refreshed = listOf(remote, imported, sampleItem(id = "book-2", format = FormatType.PDF))

        assertEquals(setOf(remote.id, "book-2"), refreshed.remoteItemLocalStateScopeItemIds())
        assertNotSame(itemIds, listOf(remote, imported).remoteItemLocalStateScopeItemIds())
    }
}
