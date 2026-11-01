package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListKind
import com.example.unifiedreader.feature.lists.UserListOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListsCatalogDataTest {
    private val book = sampleItem(id = "book-1", title = "Left Behind", format = FormatType.EPUB)
    private val ownList = UserList(
        id = "list-1",
        name = "Mine",
        description = null,
        kind = UserListKind.User,
        order = UserListOrder.Custom,
        hideFinished = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        lastOpenedAtEpochMillis = 0L,
        entries = emptyList(),
    )
    private val inputs = ListsCatalogInputs(
        stored = listOf(ownList),
        storedShelves = emptyList(),
        items = listOf(book),
        // Read a year ago and left part way: Stalled holds it.
        progress = mapOf(
            book.id to ItemProgressSummary(
                label = "Continue",
                fraction = 0.4f,
                lastReadAtEpochMillis = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000,
            ),
        ),
        onDevice = emptySet(),
        connected = setOf(SourceType.KAVITA),
    )

    @Test
    fun `catalog data holds what the Lists screen used to work out as it opened`() {
        val data = inputs.catalogData()

        assertSame(book, data.itemsById.getValue(book.id))
        assertEquals(listOf(ownList.id), data.stored.map(UserList::id))
        assertEquals(UserListKind.Stalled, data.stalled.kind)
        assertEquals(listOf(book.id), data.stalled.entries.map { entry -> entry.itemId })
        assertTrue(data.shelves.isEmpty())
    }

    @Test
    fun `kept catalog data is reused for the same lists and catalog, and worked out again for a new catalog`() {
        val data = inputs.catalogData()
        val sameInputs = ListsCatalogInputs(
            stored = inputs.stored,
            storedShelves = inputs.storedShelves,
            items = inputs.items,
            progress = inputs.progress,
            onDevice = inputs.onDevice,
            // A new set with the same servers, as each screen update builds.
            connected = setOf(SourceType.KAVITA),
        )
        val refreshedCatalog = ListsCatalogInputs(
            stored = inputs.stored,
            storedShelves = inputs.storedShelves,
            // The same books after a sync: a new list, so its books may have changed.
            items = listOf(book),
            progress = inputs.progress,
            onDevice = inputs.onDevice,
            connected = inputs.connected,
        )

        assertTrue(data.isFor(sameInputs))
        assertFalse(data.isFor(refreshedCatalog))
    }
}
