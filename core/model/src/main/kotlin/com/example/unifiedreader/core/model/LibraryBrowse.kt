package com.example.unifiedreader.core.model

import java.util.Locale

/** A field of a book its tags and values come from, as the book page and the search name it ("Set in Japan"). */
enum class LibraryBrowseRow(val label: String) {
    SET_IN("Set in"),
    WHEN("When"),
    ABOUT("About"),
    GENRE("Genre"),
    MOOD("Mood"),
    SHELF("Your shelves"),
    READING("Your reading"),
    AUTHOR("Author"),
    SERIES("Series"),
    PUBLISHER("Publisher"),
    LANGUAGE("Language"),
}

/** One value of a field: "About · Espionage"; a tap on it filters the library by it ([filteringBy]). */
data class LibraryBrowseChip(
    val row: LibraryBrowseRow,
    val value: String,
)

/** The values an item has in [row], its tags placed by [sorting]. [readingStatus] answers Your reading. */
fun ReadableItem.libraryBrowseValues(
    row: LibraryBrowseRow,
    sorting: LibraryTagSorting,
    readingStatus: LibraryReadingStatus? = null,
): List<String> = facetMetadata().browseValues(row, readingStatus, sorting)

private fun ReadableItemFacetMetadata.browseValues(
    row: LibraryBrowseRow,
    readingStatus: LibraryReadingStatus?,
    sorting: LibraryTagSorting,
): List<String> = when (row) {
    // A Location is a place already; a tag is where you put it, About until then.
    LibraryBrowseRow.SET_IN -> (tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.PLACE } + locations)
        .distinctBy(::libraryTagKey)
    LibraryBrowseRow.WHEN -> tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.ERA }
    LibraryBrowseRow.ABOUT -> tags.filter { tag -> sorting.kindOf(tag) == LibraryTagKind.THEME }
    LibraryBrowseRow.GENRE -> genres
    LibraryBrowseRow.MOOD -> moods
    LibraryBrowseRow.SHELF -> shelves
    LibraryBrowseRow.READING -> listOfNotNull(readingStatus?.libraryBrowseLabel)
    LibraryBrowseRow.AUTHOR -> (authors + writers).distinctBy { name -> name.lowercase(Locale.ROOT) }
    LibraryBrowseRow.SERIES -> series
    LibraryBrowseRow.PUBLISHER -> publishers
    LibraryBrowseRow.LANGUAGE -> languages
}

/** How Your reading names a status: the same words as the Reading status filter. */
val LibraryReadingStatus.libraryBrowseLabel: String
    get() = when (this) {
        LibraryReadingStatus.NOT_STARTED -> "Not started"
        LibraryReadingStatus.IN_PROGRESS -> "In progress"
        LibraryReadingStatus.FINISHED -> "Finished"
    }
