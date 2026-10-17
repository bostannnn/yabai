package com.example.unifiedreader.core.model

/**
 * The filter-facet dimensions that are precomputed per grid root into the normalized
 * `library_root_facets` table. [LibraryFileLocation] is intentionally excluded: it depends on the
 * runtime downloaded/available set, so it stays computed at query time rather than persisted.
 */
enum class LibraryFacetDimension {
    FORMAT,
    LIBRARY,
    AUTHOR,
    SERIES,
    PUBLISHER,
    GENRE,
    TAG,
    LANGUAGE,
}

data class LibraryRootFacetEntry(
    val dimension: LibraryFacetDimension,
    val value: String,
)

/**
 * Flattens a grid root into its facet (dimension, value) entries, reusing [facetMetadata] so the
 * persisted facets stay identical to what [buildLibraryFilterFacets] derives in memory. FORMAT is
 * the enum name and LIBRARY is the libraryId (mapped to a title in the UI).
 */
fun ReadableItem.libraryRootFacetEntries(): List<LibraryRootFacetEntry> {
    val metadata = facetMetadata()
    return buildList {
        add(LibraryRootFacetEntry(LibraryFacetDimension.FORMAT, format.name))
        add(LibraryRootFacetEntry(LibraryFacetDimension.LIBRARY, libraryId))
        metadata.authors.forEach { value -> add(LibraryRootFacetEntry(LibraryFacetDimension.AUTHOR, value)) }
        metadata.series.forEach { value -> add(LibraryRootFacetEntry(LibraryFacetDimension.SERIES, value)) }
        metadata.publishers.forEach { value -> add(LibraryRootFacetEntry(LibraryFacetDimension.PUBLISHER, value)) }
        metadata.genres.forEach { value -> add(LibraryRootFacetEntry(LibraryFacetDimension.GENRE, value)) }
        metadata.tags.forEach { value -> add(LibraryRootFacetEntry(LibraryFacetDimension.TAG, value)) }
        metadata.languages.forEach { value -> add(LibraryRootFacetEntry(LibraryFacetDimension.LANGUAGE, value)) }
    }
}
