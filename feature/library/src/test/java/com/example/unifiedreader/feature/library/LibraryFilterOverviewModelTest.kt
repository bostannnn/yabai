package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterFacetCounts
import com.example.unifiedreader.core.model.LibraryFilterFacets
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageHistogram
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryPageStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterOverviewModelTest {
    @Test
    fun `each kind of choice has its own form`() {
        assertEquals(LibraryFilterSectionStyle.Tiles, LibraryFilterPanel.Library.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Tiles, LibraryFilterPanel.ReadingStatus.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Tiles, LibraryFilterPanel.Mood.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Checks, LibraryFilterPanel.Language.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Checks, LibraryFilterPanel.Location.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.TopChips, LibraryFilterPanel.Genre.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Letters, LibraryFilterPanel.Publisher.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Stacks, LibraryFilterPanel.Series.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Timeline, LibraryFilterPanel.When.sectionStyle)
        assertEquals(LibraryFilterSectionStyle.Length, LibraryFilterPanel.Pages.sectionStyle)
    }

    @Test
    fun `past six libraries the tiles are the five with most books and a tile for the rest`() {
        val ids = (1..9).map(Int::toString)
        val facets = LibraryFilterFacets(
            libraries = ids,
            counts = LibraryFilterFacetCounts(libraries = ids.associateWith { id -> 100 - id.toInt() }),
        )

        val tiles = libraryFilterTiles(
            panel = LibraryFilterPanel.Library,
            state = LibraryFilterState(libraries = setOf("9")),
            facets = facets,
            libraryTitlesById = ids.associateWith { id -> "Library $id" },
            maxTiles = 6,
        )

        assertEquals(listOf("1", "2", "3", "4", "5", "9"), tiles.tiles.map { it.key })
        assertEquals(3, tiles.moreCount)
        assertEquals(listOf("Library 6", "Library 7"), tiles.moreNames)
    }

    @Test
    fun `four libraries all fit as tiles`() {
        val facets = LibraryFilterFacets(libraries = listOf("1", "2", "3", "4"))

        val tiles = libraryFilterTiles(LibraryFilterPanel.Library, LibraryFilterState(), facets, emptyMap(), maxTiles = 6)

        assertEquals(4, tiles.tiles.size)
        assertEquals(0, tiles.moreCount)
    }

    @Test
    fun `the timeline puts named periods after the pile their first year falls in`() {
        val facets = LibraryFilterFacets(
            times = listOf("1940s", "1910s", "World War II", "Cold War", "1800s", "Some Old Days"),
            counts = LibraryFilterFacetCounts(times = mapOf("1940s" to 9, "1910s" to 2, "world war ii" to 5, "cold war" to 1, "1800s" to 4, "some old days" to 3)),
        )

        val rows = libraryFilterTimelineRows(LibraryFilterState(times = setOf("1940s")), facets)

        assertEquals(listOf("1800s", "1910s", "World War II", "1940s", "Cold War", "Some Old Days"), rows.map { it.value })
        assertTrue(rows.single { it.value == "1940s" }.selected)
        assertTrue(rows.single { it.value == "Cold War" }.period)
    }

    @Test
    fun `a whole century leads its decades as a stop of its own`() {
        val facets = LibraryFilterFacets(times = listOf("1910s", "20th century", "1800s"))

        val rows = libraryFilterTimelineRows(LibraryFilterState(), facets)

        assertEquals(listOf("1800s", "20th century", "1910s"), rows.map { it.value })
        assertTrue(rows.none { it.period })
    }

    @Test
    fun `a named period with no known year has no place on the line`() {
        assertNull(libraryNamedPeriodOrder("The long afternoon"))
        assertEquals(3930.5, libraryNamedPeriodOrder("World War II")!!, 0.0)
        assertEquals(1019.5, libraryNamedPeriodOrder("Victorian era")!!, 0.0)
    }

    @Test
    fun `length tiles count the slider's bars they cover`() {
        val bars = LibraryPageStops.dropLast(1).map { 1 }
        val histogram = LibraryPageHistogram(bars = bars)

        // Short covers the bars from 0 to 100 (8 of them), Very long those from 500 and 750.
        assertEquals(8, LibraryLengthBucket.Short.count(histogram))
        assertEquals(3, LibraryLengthBucket.Medium.count(histogram))
        assertEquals(2, LibraryLengthBucket.Long.count(histogram))
        assertEquals(2, LibraryLengthBucket.VeryLong.count(histogram))
    }

    @Test
    fun `tapping lengths builds a span with no holes and drops back to nothing`() {
        val short = libraryLengthRangeToggling(null, LibraryLengthBucket.Short)
        assertEquals(LibraryPageRange(min = null, max = 149), short)

        val shortToLong = libraryLengthRangeToggling(short, LibraryLengthBucket.Long)
        assertEquals(LibraryPageRange(min = null, max = 499), shortToLong)
        assertEquals(LibraryLengthBucket.entries.take(3).toSet(), libraryLengthBuckets(shortToLong))

        assertNull(libraryLengthRangeToggling(short, LibraryLengthBucket.Short))
    }

    @Test
    fun `a slider pick is no run of lengths, and a tile replaces it`() {
        val picked = LibraryPageRange(min = 40, max = 250, includeUnknown = true)

        assertNull(libraryLengthBuckets(picked))
        assertEquals(LibraryPageRange(min = 500, max = null, includeUnknown = true), libraryLengthRangeToggling(picked, LibraryLengthBucket.VeryLong))
    }

    @Test
    fun `a letter opens the page at that letter's label`() {
        val groups = listOf(
            LibraryFacetPageGroup(SelectedGroupTitle, listOf(LibraryFacetPageValue("Bunin", "Bunin", 3, true))),
            LibraryFacetPageGroup("A", listOf(LibraryFacetPageValue("Akhmatova", "Akhmatova", 1, false), LibraryFacetPageValue("Aksakov", "Aksakov", 2, false))),
            LibraryFacetPageGroup("C", listOf(LibraryFacetPageValue("Chekhov", "Chekhov", 5, false))),
        )

        // The top spacer, then Selected's label and value, then A's label and two values.
        assertEquals(3, libraryFacetPageLetterIndex(groups, "A"))
        assertEquals(6, libraryFacetPageLetterIndex(groups, "C"))
        assertNull(libraryFacetPageLetterIndex(groups, "Z"))
    }

    @Test
    fun `a series line says how far you are`() {
        assertEquals("41 · 12 read", libraryFilterSeriesLine(41, 12))
        assertEquals("9 · all read", libraryFilterSeriesLine(9, 9))
        assertEquals("6", libraryFilterSeriesLine(6, 0))
    }
}
