package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.ui.components.AppScreenTransition
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScreenTransitionTest {

    private val kavitaRoot = AppUiState(selectedSource = SourceType.KAVITA)

    @Test
    fun `opening a series slides forward and leaving it slides back`() {
        val root = libraryScreenKey(kavitaRoot, shelvesOpen = false)
        val series = libraryScreenKey(kavitaRoot.copy(selectedKavitaSeriesKey = "series-1"), shelvesOpen = false)

        assertEquals(AppScreenTransition.Forward, libraryScreenTransition(root, series))
        assertEquals(AppScreenTransition.Backward, libraryScreenTransition(series, root))
    }

    @Test
    fun `a shelf browser is one level below the library`() {
        val local = AppUiState(selectedSource = SourceType.LOCAL)
        val root = libraryScreenKey(local, shelvesOpen = false)
        val shelf = libraryScreenKey(local, shelvesOpen = true)

        assertEquals(1, shelf.depth)
        assertEquals(AppScreenTransition.Forward, libraryScreenTransition(root, shelf))
        assertEquals(AppScreenTransition.Backward, libraryScreenTransition(shelf, root))
    }

    @Test
    fun `switching between peers fades through`() {
        val oneSeries = libraryScreenKey(kavitaRoot.copy(selectedKavitaSeriesKey = "lib::1"), shelvesOpen = false)
        val anotherSeries = libraryScreenKey(kavitaRoot.copy(selectedKavitaSeriesKey = "lib::2"), shelvesOpen = false)

        assertEquals(AppScreenTransition.FadeThrough, libraryScreenTransition(oneSeries, anotherSeries))
    }

    @Test
    fun `another source fades through whatever the depth`() {
        val kavitaSeries = libraryScreenKey(kavitaRoot.copy(selectedKavitaSeriesKey = "series-1"), shelvesOpen = false)
        val local = libraryScreenKey(AppUiState(selectedSource = SourceType.LOCAL), shelvesOpen = false)

        assertEquals(AppScreenTransition.FadeThrough, libraryScreenTransition(kavitaSeries, local))
        assertEquals(AppScreenTransition.FadeThrough, libraryScreenTransition(local, kavitaSeries))
    }

    @Test
    fun `a series key from another source does not count`() {
        val local = AppUiState(selectedSource = SourceType.LOCAL, selectedKavitaSeriesKey = "series-1")

        assertEquals(0, libraryScreenKey(local, shelvesOpen = false).depth)
    }
}
