package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `after a change of picks a spot above All books' row opens at that row`() {
        val top = LibraryScrollPosition(index = 0, offset = 340)
        assertEquals(LibraryScrollPosition(index = 1, offset = 0), libraryStartScrollPosition(top, picksChanged = true, resultsRowIndex = 1))
        // Any other new context (a sort, the first draw) opens where it was left.
        assertEquals(top, libraryStartScrollPosition(top, picksChanged = false, resultsRowIndex = 1))
    }

    @Test
    fun `a combination left further down still opens there after a change of picks`() {
        val down = LibraryScrollPosition(index = 40, offset = 8)
        assertEquals(down, libraryStartScrollPosition(down, picksChanged = true, resultsRowIndex = 1))
        // Docked open the shelves step aside, so the row is the first item and the top is already it.
        val top = LibraryScrollPosition(index = 0, offset = 0)
        assertEquals(top, libraryStartScrollPosition(top, picksChanged = true, resultsRowIndex = 0))
    }

    @Test
    fun `a new context counts as a change of picks only when its picks differ from the last context's`() {
        val japan = LibraryFilterState(places = setOf("Japan"))
        val picks = LibraryContextPicks()
        assertFalse(picks.changedFor("all", LibraryFilterState()))
        picks.record("all", LibraryFilterState())
        // The picks can arrive a frame before the context they make; the context's own picks still count.
        picks.record("all", japan)
        assertTrue(picks.changedFor("japan", japan))
        picks.record("japan", japan)
        assertFalse(picks.changedFor("japan", japan))
        assertFalse(picks.changedFor("japan-by-title", japan))
    }
}
