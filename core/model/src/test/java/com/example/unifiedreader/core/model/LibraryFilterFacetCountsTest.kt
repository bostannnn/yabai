package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterFacetCountsTest {
    private val epubInProgress = book(id = "e1", format = FormatType.EPUB, authors = listOf("Ruth Amari", "ruth amari"))
    private val epubFinished = book(id = "e2", format = FormatType.EPUB, authors = listOf("Lena Aster"))
    private val pdfInProgress = book(id = "p1", format = FormatType.PDF, authors = listOf("Ruth Amari"))
    private val items = listOf(epubInProgress, epubFinished, pdfInProgress)
    private val statusById = mapOf(
        epubInProgress.id to LibraryReadingStatus.IN_PROGRESS,
        epubFinished.id to LibraryReadingStatus.FINISHED,
        pdfInProgress.id to LibraryReadingStatus.IN_PROGRESS,
    )

    private fun countsFor(filters: LibraryFilterState): LibraryFilterFacetCounts = buildContextualLibraryFilterFacets(
        items = items,
        filters = filters,
        readingStatusProvider = { item -> statusById[item.id] },
    ).counts

    @Test
    fun `unfiltered counts every value once per item`() {
        val counts = countsFor(LibraryFilterState())

        assertEquals(mapOf(FormatType.EPUB to 2, FormatType.PDF to 1), counts.formats)
        assertEquals(
            mapOf(
                LibraryReadingStatus.NOT_STARTED to 0,
                LibraryReadingStatus.IN_PROGRESS to 2,
                LibraryReadingStatus.FINISHED to 1,
            ),
            counts.readingStatuses,
        )
        // "Ruth Amari" twice on one book (in two spellings) still counts that book once.
        assertEquals(2, counts.countOf(LibraryFilterPanel.Author, "Ruth Amari"))
        assertEquals(2, counts.countOf(LibraryFilterPanel.Author, "RUTH AMARI"))
        assertEquals(1, counts.countOf(LibraryFilterPanel.Author, "Lena Aster"))
        assertEquals(0, counts.countOf(LibraryFilterPanel.Author, "Nobody"))
    }

    @Test
    fun `a dimension is counted under the other filters but not its own`() {
        val counts = countsFor(
            LibraryFilterState(
                formats = setOf(FormatType.EPUB),
                readingStatuses = setOf(LibraryReadingStatus.IN_PROGRESS),
            ),
        )

        // Formats: only the status filter applies, so PDF keeps its count and EPUB loses the finished one.
        assertEquals(mapOf(FormatType.EPUB to 1, FormatType.PDF to 1), counts.formats)
        // Statuses: only the format filter applies.
        assertEquals(1, counts.readingStatuses[LibraryReadingStatus.IN_PROGRESS])
        assertEquals(1, counts.readingStatuses[LibraryReadingStatus.FINISHED])
        // Authors: both filters apply, leaving the in-progress EPUB.
        assertEquals(1, counts.countOf(LibraryFilterPanel.Author, "Ruth Amari"))
        assertEquals(0, counts.countOf(LibraryFilterPanel.Author, "Lena Aster"))
    }

    @Test
    fun `a format the other filters rule out stays in the counts at zero`() {
        val counts = countsFor(LibraryFilterState(readingStatuses = setOf(LibraryReadingStatus.FINISHED)))

        assertEquals(mapOf(FormatType.EPUB to 1, FormatType.PDF to 0), counts.formats)
    }

    @Test
    fun `the facet values are unchanged by counting`() {
        val facets = buildContextualLibraryFilterFacets(items = items, filters = LibraryFilterState())

        assertEquals(listOf("Ruth Amari", "Lena Aster"), facets.authors)
        assertEquals(listOf(FormatType.EPUB, FormatType.PDF).sortedBy { it.ordinal }, facets.formats)
    }
}

private fun book(
    id: String,
    format: FormatType,
    authors: List<String>,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.LOCAL,
    libraryId = "local",
    isLocalImport = true,
    title = id,
    format = format,
    bookMetadata = BookMetadata(author = authors.first(), authors = authors.drop(1)),
)
