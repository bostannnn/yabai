package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryDownloadSelectionTest {
    @Test
    fun `a paged grid keeps picks whose page was dropped`() {
        // Picked A and Z, then jumped on the rail: the loaded window is now M-P, and P just finished downloading.
        val selection = sanitizeLibraryDownloadSelection(
            selectedIds = listOf("A", "Z", "P"),
            renderedIds = setOf("M", "N", "O", "P"),
            selectableIds = setOf("M", "N", "O"),
            isPaged = true,
        )

        assertEquals(listOf("A", "Z"), selection)
    }

    @Test
    fun `an in-memory grid drops picks it no longer renders`() {
        val selection = sanitizeLibraryDownloadSelection(
            selectedIds = listOf("A", "Z"),
            renderedIds = setOf("A", "B"),
            selectableIds = setOf("A", "B"),
            isPaged = false,
        )

        assertEquals(listOf("A"), selection)
    }

    @Test
    fun `toggling a card in a paged window keeps the picks outside it`() {
        assertEquals(
            listOf("N", "A", "Z"),
            orderLibraryDownloadSelection(
                selectedIds = setOf("A", "Z", "N"),
                renderedIds = listOf("M", "N", "O"),
                isPaged = true,
            ),
        )
        assertEquals(
            listOf("N"),
            orderLibraryDownloadSelection(
                selectedIds = setOf("A", "Z", "N"),
                renderedIds = listOf("M", "N", "O"),
                isPaged = false,
            ),
        )
    }

    @Test
    fun `select all in a paged view covers the whole view, not the loaded window`() {
        val window = listOf(item("M"), item("N"))
        val wholeView = listOf(item("A"), item("M"), item("N"), item("Z"))

        assertEquals(
            listOf("M", "N", "A", "Z"),
            libraryDownloadSelectableItems(window, wholeView, isPaged = true).map(ReadableItem::id),
        )
        // Before the recompute lands the view list is empty and the window is all there is.
        assertEquals(
            listOf("M", "N"),
            libraryDownloadSelectableItems(window, emptyList(), isPaged = true).map(ReadableItem::id),
        )
        assertEquals(
            listOf("M", "N"),
            libraryDownloadSelectableItems(window, wholeView, isPaged = false).map(ReadableItem::id),
        )
        assertEquals(
            listOf("A", "M", "N", "Z"),
            libraryDownloadSelectableItems(null, wholeView, isPaged = true).map(ReadableItem::id),
        )
    }

    private fun item(id: String) = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:1",
        remoteId = id,
        title = id,
        format = FormatType.CBZ,
    )
}
