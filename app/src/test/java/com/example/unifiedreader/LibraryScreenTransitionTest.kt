package com.example.unifiedreader

import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.shelves.LibraryShelfBrowserKind
import com.example.unifiedreader.ui.components.AppScreenTransition
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScreenTransitionTest {

    private val kavitaRoot = AppUiState(selectedSource = SourceType.KAVITA)

    @Test
    fun `opening a series slides forward and leaving it slides back`() {
        val root = libraryScreenKey(kavitaRoot, shelf = null)
        val series = libraryScreenKey(kavitaRoot.copy(selectedKavitaSeriesKey = "series-1"), shelf = null)

        assertEquals(AppScreenTransition.Forward, libraryScreenTransition(root, series))
        assertEquals(AppScreenTransition.Backward, libraryScreenTransition(series, root))
    }

    @Test
    fun `a shelf browser is one level below the library`() {
        val local = AppUiState(selectedSource = SourceType.LOCAL)
        val root = libraryScreenKey(local, shelf = null)
        val shelf = libraryScreenKey(local, shelf = LibraryShelfBrowserKind.ContinueReading)

        assertEquals(1, shelf.depth)
        assertEquals(AppScreenTransition.Forward, libraryScreenTransition(root, shelf))
        assertEquals(AppScreenTransition.Backward, libraryScreenTransition(shelf, root))
    }

    @Test
    fun `a Kavita list opens below its landing, which is below Kavita home`() {
        val landing = kavitaRoot.copy(kavitaLibrarySection = KavitaLibrarySection.ReadingLists)
        val list = landing.copy(
            kavitaReadingLists = landing.kavitaReadingLists.copy(selectedReadingListId = "list-1"),
        )
        val home = libraryScreenKey(kavitaRoot, shelf = null)
        val landingKey = libraryScreenKey(landing, shelf = null)
        val listKey = libraryScreenKey(list, shelf = null)

        assertEquals(listOf(0, 1, 2), listOf(home.depth, landingKey.depth, listKey.depth))
        assertEquals(AppScreenTransition.Forward, libraryScreenTransition(landingKey, listKey))
        assertEquals(AppScreenTransition.Backward, libraryScreenTransition(listKey, landingKey))
    }

    @Test
    fun `switching between peers fades through`() {
        val collections = libraryScreenKey(
            kavitaRoot.copy(kavitaLibrarySection = KavitaLibrarySection.Collections),
            shelf = null,
        )
        val readingLists = libraryScreenKey(
            kavitaRoot.copy(kavitaLibrarySection = KavitaLibrarySection.ReadingLists),
            shelf = null,
        )

        assertEquals(AppScreenTransition.FadeThrough, libraryScreenTransition(collections, readingLists))
    }

    @Test
    fun `another source fades through whatever the depth`() {
        val kavitaSeries = libraryScreenKey(kavitaRoot.copy(selectedKavitaSeriesKey = "series-1"), shelf = null)
        val local = libraryScreenKey(AppUiState(selectedSource = SourceType.LOCAL), shelf = null)

        assertEquals(AppScreenTransition.FadeThrough, libraryScreenTransition(kavitaSeries, local))
        assertEquals(AppScreenTransition.FadeThrough, libraryScreenTransition(local, kavitaSeries))
    }

    @Test
    fun `a series key from another source does not count`() {
        val local = AppUiState(selectedSource = SourceType.LOCAL, selectedKavitaSeriesKey = "series-1")

        assertEquals(0, libraryScreenKey(local, shelf = null).depth)
    }
}
