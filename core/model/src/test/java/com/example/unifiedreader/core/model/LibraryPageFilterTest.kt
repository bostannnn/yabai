package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPageFilterTest {
    @Test
    fun `a range holds both of its bounds and an open end holds everything past it`() {
        val range = LibraryPageRange(min = 150, max = 300)

        assertTrue(150 in range)
        assertTrue(300 in range)
        assertFalse(149 in range)
        assertFalse(301 in range)
        assertTrue(5_000 in LibraryPageRange(min = 500))
        assertTrue(1 in LibraryPageRange(max = 50))
    }

    @Test
    fun `an item without a page count fits only when unknown lengths are kept`() {
        assertFalse(LibraryPageRange(min = 10).matches(emptyList()))
        assertTrue(LibraryPageRange(min = 10, includeUnknown = true).matches(emptyList()))
    }

    @Test
    fun `an item with several counts fits when one of them is in range`() {
        val range = LibraryPageRange(min = 150, max = 300)

        assertTrue(range.matches(listOf(24, 26, 210)))
        assertFalse(range.matches(listOf(24, 26)))
    }

    @Test
    fun `page counts are the ones the book page shows`() {
        val comic = item("comic", FormatType.CBZ, pageCount = 24, source = SourceType.KAVITA)
        val kavitaEpub = item("kavita-epub", FormatType.EPUB, pageCount = 12, source = SourceType.KAVITA)
        val grimmoryEpub = item("grimmory-epub", FormatType.EPUB, pageCount = 320, source = SourceType.GRIMMORY)
        val emptyPdf = item("pdf", FormatType.PDF, pageCount = 0, source = SourceType.LOCAL)

        assertEquals(24, comic.libraryPageCount())
        // Kavita's count for an EPUB is its number of files, not pages.
        assertNull(kavitaEpub.libraryPageCount())
        assertEquals(320, grimmoryEpub.libraryPageCount())
        assertNull(emptyPdf.libraryPageCount())
    }

    @Test
    fun `the pages filter keeps items in range and drops unknown lengths unless asked`() {
        val issue = item("issue", FormatType.CBZ, pageCount = 24)
        val volume = item("volume", FormatType.CBZ, pageCount = 190)
        val novel = item("novel", FormatType.EPUB, pageCount = 410, source = SourceType.GRIMMORY)
        val unknown = item("unknown", FormatType.EPUB, pageCount = null)
        val items = listOf(issue, volume, novel, unknown)

        val inRange = applyLibraryFilters(items, LibraryFilterState(pages = LibraryPageRange(min = 150, max = 500)))
        val withUnknown = applyLibraryFilters(
            items,
            LibraryFilterState(pages = LibraryPageRange(max = 50, includeUnknown = true)),
        )

        assertEquals(listOf(volume, novel), inRange)
        assertEquals(listOf(issue, unknown), withUnknown)
    }

    @Test
    fun `a page counts provider decides a series tile's lengths`() {
        val series = item("series", FormatType.CBZ, pageCount = null)

        val kept = applyLibraryFilters(
            items = listOf(series),
            filters = LibraryFilterState(pages = LibraryPageRange(min = 150)),
            pageCountsProvider = { listOf(24, 180) },
        )

        assertEquals(listOf(series), kept)
    }

    @Test
    fun `the histogram counts each item once per bar it reaches and the unknown ones apart`() {
        val short = item("short", FormatType.CBZ, pageCount = 24)
        val long = item("long", FormatType.CBZ, pageCount = 2_400)
        val series = item("series", FormatType.CBZ, pageCount = null)
        val unknown = item("unknown", FormatType.EPUB, pageCount = null)
        val countsById = mapOf(series.id to listOf(22, 28, 210))

        val histogram = buildLibraryPageHistogram(listOf(short, long, series, unknown)) { item ->
            countsById[item.id] ?: item.ownPageCounts()
        }

        val bars = histogram.bars
        assertEquals(LibraryPageStops.size - 1, bars.size)
        assertEquals(2, bars[libraryPageBarIndex(24)])
        assertEquals(1, bars[libraryPageBarIndex(210)])
        // Everything from the last stop up lands in the last bar.
        assertEquals(1, bars.last())
        assertEquals(4, bars.sum())
        assertEquals(1, histogram.unknown)
        assertTrue(histogram.hasKnownLengths)
    }

    @Test
    fun `the histogram is counted with every filter but pages applied`() {
        val comic = item("comic", FormatType.CBZ, pageCount = 24)
        val pdf = item("pdf", FormatType.PDF, pageCount = 300)

        val facets = buildContextualLibraryFilterFacets(
            items = listOf(comic, pdf),
            filters = LibraryFilterState(formats = setOf(FormatType.CBZ), pages = LibraryPageRange(min = 200)),
        )

        assertEquals(1, facets.pages.bars.sum())
        assertEquals(1, facets.pages.bars[libraryPageBarIndex(24)])
        // Format counts see the pages filter: only the PDF is long enough.
        assertEquals(0, facets.counts.formats[FormatType.CBZ])
        assertEquals(1, facets.counts.formats[FormatType.PDF])
    }

    @Test
    fun `a hidden pages panel drops its range`() {
        val state = LibraryFilterState(pages = LibraryPageRange(min = 100))

        assertFalse(state.isEmpty)
        assertTrue(state.visibleInPanels(listOf(LibraryFilterPanel.Format)).isEmpty)
        assertEquals(state, state.visibleInPanels(listOf(LibraryFilterPanel.Pages)))
    }

    @Test
    fun `every source offers the pages panel right after reading status`() {
        SourceType.entries.forEach { source ->
            val panels = availableLibraryFilterPanels(source)
            assertEquals(panels.indexOf(LibraryFilterPanel.ReadingStatus) + 1, panels.indexOf(LibraryFilterPanel.Pages))
        }
    }
}

private fun item(
    id: String,
    format: FormatType,
    pageCount: Int?,
    source: SourceType = SourceType.LOCAL,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = source,
    libraryId = "library",
    title = id,
    format = format,
    pageCount = pageCount,
)
