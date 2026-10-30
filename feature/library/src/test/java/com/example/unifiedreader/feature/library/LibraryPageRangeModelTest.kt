package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryPageStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPageRangeModelTest {
    private val end = LibraryPagePositionEnd.toFloat()

    @Test
    fun `no filter spans the whole track and a range sits on its stops`() {
        assertEquals(0f..end, libraryPageSliderPositions(null))
        assertEquals(
            LibraryPageStops.indexOf(150).toFloat()..LibraryPageStops.indexOf(300).toFloat(),
            libraryPageSliderPositions(LibraryPageRange(min = 150, max = 300)),
        )
    }

    @Test
    fun `a typed bound sits between the stops around it and past the last stop at the end`() {
        // 120 is two fifths of the way from 100 to 150.
        assertEquals(LibraryPageStops.indexOf(100) + 0.4f, libraryPagePosition(120), 0.0001f)
        assertEquals(end, libraryPagePosition(2_000))
        assertEquals(0f, libraryPagePosition(0))
    }

    @Test
    fun `the moved handle snaps to a stop and the other stays put`() {
        val current = 7.4f..11f

        val snapped = snapLibraryPagePositions(current, 7.4f..12.6f)

        assertEquals(7.4f..13f, snapped)
    }

    @Test
    fun `handles on the first and last stops mean no bound`() {
        assertNull(libraryPageRangeAt(0f..end, current = null))
        assertEquals(LibraryPageRange(min = 150), libraryPageRangeAt(LibraryPageStops.indexOf(150).toFloat()..end, null))
        assertEquals(LibraryPageRange(max = 50), libraryPageRangeAt(0f..LibraryPageStops.indexOf(50).toFloat(), null))
    }

    @Test
    fun `a handle between stops keeps its typed bound and the switch carries over`() {
        val current = LibraryPageRange(min = 120, max = 300, includeUnknown = true)

        val moved = libraryPageRangeAt(libraryPagePosition(120)..LibraryPageStops.indexOf(500).toFloat(), current)

        assertEquals(LibraryPageRange(min = 120, max = 500, includeUnknown = true), moved)
    }

    @Test
    fun `typed bounds swap when the lower is above the upper and empty bounds clear the filter`() {
        assertEquals(LibraryPageRange(min = 100, max = 400), libraryPageRange(min = 400, max = 100, includeUnknown = false))
        assertNull(libraryPageRange(min = null, max = null, includeUnknown = true))
        assertNull(libraryPageRange(min = 0, max = null, includeUnknown = false))
    }

    @Test
    fun `the histogram lights every bar the handles cover`() {
        val stops = LibraryPageStops
        assertEquals(stops.indexOf(150) until stops.indexOf(300), libraryPageHighlightedBars(libraryPageSliderPositions(LibraryPageRange(150, 300))))
        // A typed range inside one bar still lights it.
        assertEquals(stops.indexOf(100)..stops.indexOf(100), libraryPageHighlightedBars(libraryPagePosition(120)..libraryPagePosition(140)))
    }

    @Test
    fun `labels name the range the way the sheet and the chip show it`() {
        assertEquals("Any", libraryPageRangeText(null))
        assertEquals("150–300", libraryPageRangeText(LibraryPageRange(150, 300)))
        assertEquals("1,000+", libraryPageRangeText(LibraryPageRange(min = 1_000)))
        assertEquals("Up to 50", libraryPageRangeText(LibraryPageRange(max = 50)))
        assertEquals("150–300 pages", libraryPageRangeChipLabel(LibraryPageRange(150, 300)))
        assertEquals("Up to 50 pages or unknown", libraryPageRangeChipLabel(LibraryPageRange(max = 50, includeUnknown = true)))
        assertEquals("1 book has no page count", libraryUnknownLengthText(1))
        assertEquals("114 books have no page count", libraryUnknownLengthText(114))
    }

    @Test
    fun `fields take digits only`() {
        assertEquals("120", libraryPageBoundInput("1a2-0"))
        assertEquals(120, parseLibraryPageBound("120"))
        assertNull(parseLibraryPageBound(""))
    }

    @Test
    fun `the applied chip names the range and its x clears it`() {
        val state = LibraryFilterState(pages = LibraryPageRange(150, 300))

        val applied = appliedLibraryFilters(state, listOf(LibraryFilterPanel.Pages), emptyMap())

        assertEquals(listOf("150–300 pages"), applied.map(LibraryAppliedFilter::label))
        assertEquals(LibraryFilterState(), applied.single().without)
        assertEquals(1, LibraryFilterPanel.Pages.selectedCount(state))
    }

    @Test
    fun `a page range survives being saved`() {
        val ranges = listOf(
            LibraryPageRange(min = 150, max = 300),
            LibraryPageRange(max = 50, includeUnknown = true),
            LibraryPageRange(min = 1_000),
        )

        ranges.forEach { range -> assertEquals(range, decodeLibraryPageRange(encodeLibraryPageRange(range))) }
        assertNull(encodeLibraryPageRange(null))
        assertNull(decodeLibraryPageRange("garbage"))
        assertNull(decodeLibraryPageRange("::1"))
    }
}
