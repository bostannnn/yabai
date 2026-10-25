package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import java.text.NumberFormat

/** What a source's catalog holds, for the heading over its grid: books, comics, or both. */
enum class LibraryCatalogKind {
    Books,
    Comics,
    Mixed,
}

/** Whether [items] are books, comics or both; books when there are none. */
fun libraryCatalogKind(items: List<ReadableItem>): LibraryCatalogKind {
    var books = false
    var comics = false
    for (item in items) {
        when (item.kind) {
            ReadableItemKind.BOOK -> books = true
            ReadableItemKind.COMIC -> comics = true
        }
        if (books && comics) return LibraryCatalogKind.Mixed
    }
    return if (comics) LibraryCatalogKind.Comics else LibraryCatalogKind.Books
}

/** The heading beside the filter chips: "All books" and "1,474". */
internal data class LibraryGridHeading(
    val title: String,
    // Null while the catalog is still loading, so the heading never shows a count that is too low.
    val count: String?,
)

/**
 * "All books 1,474" over the whole catalog, "Books 304 of 1,474" while filters narrow it, and
 * "Results 2" for a search.
 */
internal fun libraryGridHeading(
    kind: LibraryCatalogKind,
    searchActive: Boolean,
    filtersActive: Boolean,
    shownCount: Int?,
    totalCount: Int?,
): LibraryGridHeading {
    val noun = when (kind) {
        LibraryCatalogKind.Books -> "books"
        LibraryCatalogKind.Comics -> "comics"
        LibraryCatalogKind.Mixed -> "titles"
    }
    return when {
        searchActive -> LibraryGridHeading(title = "Results", count = shownCount?.let(::formatCount))
        filtersActive -> LibraryGridHeading(
            title = noun.replaceFirstChar(Char::uppercaseChar),
            count = shownCount?.let { shown ->
                if (totalCount != null && totalCount > shown) "${formatCount(shown)} of ${formatCount(totalCount)}" else formatCount(shown)
            },
        )
        else -> LibraryGridHeading(title = "All $noun", count = (totalCount ?: shownCount)?.let(::formatCount))
    }
}

private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)
