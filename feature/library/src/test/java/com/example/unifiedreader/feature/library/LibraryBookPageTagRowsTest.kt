package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBookPageTagRowsTest {
    @Test
    fun `tags nobody sorted are About, and sorted ones and Locations go to their rows`() {
        val unsorted = libraryBookPageTagRows(
            genres = listOf("Historical Fiction"),
            tags = listOf("China", "World War II", "Espionage"),
            locations = emptyList(),
            sorting = LibraryTagSorting(),
        )
        assertEquals(
            listOf(
                LibraryBrowseRow.ABOUT to listOf("China", "World War II", "Espionage"),
                LibraryBrowseRow.GENRE to listOf("Historical Fiction"),
            ),
            unsorted,
        )

        val sorting = LibraryTagSorting().with(
            listOf(
                LibraryTagSortChange(LibraryTagField.TAG, "china", LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA)),
                LibraryTagSortChange(LibraryTagField.TAG, "World War II", LibraryTagChoice(LibraryTagKind.ERA)),
            ),
        )
        val sorted = libraryBookPageTagRows(emptyList(), listOf("China", "World War II", "Espionage"), listOf("Shanghai"), sorting)
        assertEquals(
            listOf(
                LibraryBrowseRow.SET_IN to listOf("China", "Shanghai"),
                LibraryBrowseRow.WHEN to listOf("World War II"),
                LibraryBrowseRow.ABOUT to listOf("Espionage"),
            ),
            sorted,
        )
    }
}
