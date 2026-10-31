package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPagingFeatureTest {
    @Test
    fun `content filters page from SQL`() {
        assertTrue(LibraryPagingFeature.isPageable(kavitaState(LibraryFilterState(formats = setOf(FormatType.CBZ)))))
    }

    @Test
    fun `runtime filters keep the in-memory grid`() {
        assertFalse(LibraryPagingFeature.isPageable(kavitaState(LibraryFilterState(readingStatuses = setOf(LibraryReadingStatus.FINISHED)))))
        // A series takes its length from its issues, which the root tables don't hold.
        assertFalse(LibraryPagingFeature.isPageable(kavitaState(LibraryFilterState(pages = LibraryPageRange(min = 150)))))
    }

    @Test
    fun `the pages sort keeps the in-memory grid`() {
        assertFalse(LibraryPagingFeature.isPageable(kavitaState(LibraryFilterState(), LibrarySortMode.PAGES)))
    }

    private fun kavitaState(
        filters: LibraryFilterState,
        sortMode: LibrarySortMode = LibrarySortMode.LAST_READ,
    ) = AppUiState(
        selectedSource = SourceType.KAVITA,
        libraryFiltersBySource = mapOf(SourceType.KAVITA to filters),
        librarySortsBySource = mapOf(SourceType.KAVITA to sortMode),
    )
}
