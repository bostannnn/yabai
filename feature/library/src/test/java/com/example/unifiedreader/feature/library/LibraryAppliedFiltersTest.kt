package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAppliedFiltersTest {
    @Test
    fun `each applied value gets its own chip, in panel order`() {
        val state = LibraryFilterState(
            formats = setOf(FormatType.EPUB),
            readingStatuses = setOf(LibraryReadingStatus.FINISHED, LibraryReadingStatus.IN_PROGRESS),
            authors = setOf("mira Kalden", "Ines Varga"),
        )

        val labels = appliedLibraryFilters(
            state = state,
            panels = listOf(LibraryFilterPanel.ReadingStatus, LibraryFilterPanel.Format, LibraryFilterPanel.Author),
            libraryTitlesById = emptyMap(),
        ).map(LibraryAppliedFilter::label)

        assertEquals(listOf("In progress", "Finished", "EPUB", "Ines Varga", "mira Kalden"), labels)
    }

    @Test
    fun `removing a chip drops only its value`() {
        val state = LibraryFilterState(
            readingStatuses = setOf(LibraryReadingStatus.IN_PROGRESS, LibraryReadingStatus.FINISHED),
            genres = setOf("Mystery"),
        )

        val inProgress = appliedLibraryFilters(
            state = state,
            panels = listOf(LibraryFilterPanel.ReadingStatus, LibraryFilterPanel.Genre),
            libraryTitlesById = emptyMap(),
        ).first { it.label == "In progress" }

        assertEquals(
            state.copy(readingStatuses = setOf(LibraryReadingStatus.FINISHED)),
            inProgress.without,
        )
    }

    @Test
    fun `libraries show their titles and panels that are not shown add no chips`() {
        val state = LibraryFilterState(libraries = setOf("2", "1"), tags = setOf("hidden"))

        val filters = appliedLibraryFilters(
            state = state,
            panels = listOf(LibraryFilterPanel.Library),
            libraryTitlesById = mapOf("1" to "Manga", "2" to "Comics"),
        )

        assertEquals(listOf("Comics", "Manga"), filters.map(LibraryAppliedFilter::label))
    }
}
