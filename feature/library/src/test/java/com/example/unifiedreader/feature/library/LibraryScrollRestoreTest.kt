package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScrollRestoreTest {
    private val saved = LibraryScrollPosition(index = 500, offset = 12)

    @Test
    fun `a fresh pager with no items yet waits instead of settling on the clamped spot`() {
        assertEquals(
            LibraryScrollRestoreStep.Wait,
            libraryScrollRestoreStep(saved, gridItemCount = 1, isLoading = true, canLoadMore = true),
        )
    }

    @Test
    fun `a saved index past the first pages loads more until the grid can reach it`() {
        // Last read / Date added page without placeholders: the first load is about 180 roots.
        assertEquals(
            LibraryScrollRestoreStep.LoadMore,
            libraryScrollRestoreStep(saved, gridItemCount = 181, isLoading = false, canLoadMore = true),
        )
        assertEquals(
            LibraryScrollRestoreStep.Wait,
            libraryScrollRestoreStep(saved, gridItemCount = 181, isLoading = true, canLoadMore = true),
        )
        assertEquals(
            LibraryScrollRestoreStep.ScrollToSaved,
            libraryScrollRestoreStep(saved, gridItemCount = 541, isLoading = false, canLoadMore = true),
        )
    }

    @Test
    fun `placeholders give the full index space as soon as the first page lands`() {
        assertEquals(
            LibraryScrollRestoreStep.ScrollToSaved,
            libraryScrollRestoreStep(saved, gridItemCount = 2_001, isLoading = false, canLoadMore = true),
        )
    }

    @Test
    fun `the saved slot itself must exist`() {
        assertEquals(
            LibraryScrollRestoreStep.LoadMore,
            libraryScrollRestoreStep(saved, gridItemCount = 500, isLoading = false, canLoadMore = true),
        )
    }

    @Test
    fun `a view that shrank below the saved index gives up once everything is loaded`() {
        assertEquals(
            LibraryScrollRestoreStep.GiveUp,
            libraryScrollRestoreStep(saved, gridItemCount = 301, isLoading = false, canLoadMore = false),
        )
    }
}
