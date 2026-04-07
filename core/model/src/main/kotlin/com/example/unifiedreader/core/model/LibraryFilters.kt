package com.example.unifiedreader.core.model

data class LibraryFilterState(
    val formats: Set<FormatType> = emptySet(),
    val authors: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            authors.isEmpty() &&
            series.isEmpty()
}

data class LibraryFilterFacets(
    val formats: List<FormatType> = emptyList(),
    val authors: List<String> = emptyList(),
    val series: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            authors.isEmpty() &&
            series.isEmpty()
}

fun applyLibraryFilters(
    items: List<ReadableItem>,
    filters: LibraryFilterState,
): List<ReadableItem> {
    if (filters.isEmpty) return items
    return items.filter { item ->
        filters.formats.matches(item.format) &&
            filters.authors.matches(item.normalizedAuthor()) &&
            filters.series.matches(item.normalizedSeries())
    }
}

fun buildLibraryFilterFacets(
    items: List<ReadableItem>,
): LibraryFilterFacets = LibraryFilterFacets(
    formats = items.map(ReadableItem::format).distinctByEnumOrder(),
    authors = items.mapNotNull(ReadableItem::normalizedAuthor).distinctByAuthorOrder(),
    series = items.mapNotNull(ReadableItem::normalizedSeries).distinctByTextOrder(),
)

private fun <T> Set<T>.matches(value: T?): Boolean = isEmpty() || value in this

private fun ReadableItem.normalizedAuthor(): String? =
    bookMetadata?.author?.trim()?.takeIf(String::isNotEmpty)

private fun ReadableItem.normalizedSeries(): String? =
    comicMetadata?.series?.trim()?.takeIf(String::isNotEmpty)

private inline fun <reified T : Enum<T>> List<T>.distinctByEnumOrder(): List<T> =
    distinct().sortedBy { it.ordinal }

private fun List<String>.distinctByTextOrder(): List<String> =
    distinctBy(String::lowercase).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

private fun List<String>.distinctByAuthorOrder(): List<String> =
    distinctBy(String::lowercase)
        .sortedWith(
            compareBy<String> { it.authorFacetKey().lowercase() }
                .thenBy { it.lowercase() },
        )

fun String.authorFacetKey(): String {
    val tokens = trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (tokens.size >= 2 && tokens[tokens.lastIndex - 1].lowercase() in surnameParticles) {
        return "${tokens[tokens.lastIndex - 1]} ${tokens.last()}"
    }
    return tokens.lastOrNull() ?: trim()
}

private val surnameParticles = setOf(
    "da",
    "de",
    "del",
    "der",
    "di",
    "du",
    "la",
    "le",
    "van",
    "von",
)
