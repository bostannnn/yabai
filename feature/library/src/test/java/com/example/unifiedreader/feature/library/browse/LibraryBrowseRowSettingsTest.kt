package com.example.unifiedreader.feature.library.browse

import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryBrowseRow.ABOUT
import com.example.unifiedreader.core.model.LibraryBrowseRow.AUTHOR
import com.example.unifiedreader.core.model.LibraryBrowseRow.GENRE
import com.example.unifiedreader.core.model.LibraryBrowseRow.MOOD
import com.example.unifiedreader.core.model.LibraryBrowseRow.READING
import com.example.unifiedreader.core.model.LibraryBrowseRow.SET_IN
import com.example.unifiedreader.core.model.LibraryBrowseRow.SHELF
import com.example.unifiedreader.core.model.LibraryBrowseRow.WHEN
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBrowseRowSettingsTest {
    @Test
    fun `until you move a row, Browse keeps its own order`() {
        assertEquals(listOf(SET_IN, WHEN, ABOUT, GENRE, MOOD, READING), LibraryBrowseRowSettings().shown)
        assertEquals(LibraryBrowseRow.entries, LibraryBrowseRowSettings().ordered)
    }

    @Test
    fun `dragging the shown rows reorders them and leaves the others in place`() {
        val settings = LibraryBrowseRowSettings().arranging(listOf(MOOD, SET_IN, WHEN, ABOUT, GENRE, READING))

        assertEquals(listOf(MOOD, SET_IN, WHEN, ABOUT, GENRE, READING), settings.shown)
        // Your shelves sat between Mood and Your reading, in the sixth place; it stays there.
        assertEquals(SHELF, settings.ordered[5])
        assertEquals(LibraryBrowseRow.entries.size, settings.ordered.size)
    }

    @Test
    fun `a row switched on later shows where it sits in your order`() {
        val settings = LibraryBrowseRowSettings()
            .arranging(listOf(READING, SET_IN, WHEN, ABOUT, GENRE, MOOD))
            .with(SHELF, show = true)

        // Mood took Your reading's place at the end; Your shelves kept its own, after Genre.
        assertEquals(listOf(READING, SET_IN, WHEN, ABOUT, GENRE, SHELF, MOOD), settings.shown)
    }

    @Test
    fun `a row missing from a saved order comes back after the row it follows`() {
        // An order saved before Mood existed, with Genre moved to the top.
        val saved = LibraryBrowseRow.entries.filterNot { it == MOOD || it == GENRE }.let { listOf(GENRE) + it }

        val order = libraryBrowseRowOrder(saved)

        assertEquals(listOf(GENRE, MOOD, SET_IN, WHEN, ABOUT), order.take(5))
        assertEquals(LibraryBrowseRow.entries.toSet(), order.toSet())
        assertEquals(AUTHOR, order[order.indexOf(READING) + 1])
    }
}
