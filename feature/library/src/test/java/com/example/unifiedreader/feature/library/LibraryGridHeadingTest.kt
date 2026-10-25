package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LibraryGridHeadingTest {
    private lateinit var defaultLocale: Locale

    @Before
    fun useEnglishNumbers() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `catalog kind is books, comics, or both`() {
        assertEquals(LibraryCatalogKind.Books, libraryCatalogKind(listOf(item("a", FormatType.EPUB), item("b", FormatType.PDF))))
        assertEquals(LibraryCatalogKind.Comics, libraryCatalogKind(listOf(item("a", FormatType.CBZ))))
        assertEquals(LibraryCatalogKind.Mixed, libraryCatalogKind(listOf(item("a", FormatType.EPUB), item("b", FormatType.CBR))))
        assertEquals(LibraryCatalogKind.Books, libraryCatalogKind(emptyList()))
    }

    @Test
    fun `the whole catalog reads All books with its count`() {
        assertEquals(
            LibraryGridHeading(title = "All books", count = "1,474"),
            heading(LibraryCatalogKind.Books, shown = 180, total = 1474),
        )
        assertEquals("All titles", heading(LibraryCatalogKind.Mixed, shown = 3, total = 3).title)
    }

    @Test
    fun `filters name the kind and count against the whole`() {
        assertEquals(
            LibraryGridHeading(title = "Comics", count = "304 of 1,474"),
            heading(LibraryCatalogKind.Comics, filters = true, shown = 304, total = 1474),
        )
        assertEquals("12", heading(LibraryCatalogKind.Books, filters = true, shown = 12, total = 12).count)
    }

    @Test
    fun `a search reads Results`() {
        assertEquals(
            LibraryGridHeading(title = "Results", count = "2"),
            heading(LibraryCatalogKind.Books, search = true, shown = 2, total = 1474),
        )
    }

    @Test
    fun `no count until the catalog has loaded`() {
        assertEquals(LibraryGridHeading(title = "All comics", count = null), heading(LibraryCatalogKind.Comics, shown = null, total = null))
    }

    private fun heading(
        kind: LibraryCatalogKind,
        search: Boolean = false,
        filters: Boolean = false,
        shown: Int?,
        total: Int?,
    ) = libraryGridHeading(kind = kind, searchActive = search, filtersActive = filters, shownCount = shown, totalCount = total)

    private fun item(id: String, format: FormatType) = ReadableItem(
        id = id,
        sourceType = SourceType.LOCAL,
        libraryId = "local",
        title = "Item $id",
        format = format,
    )
}
