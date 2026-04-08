package com.example.unifiedreader.core.model

import java.text.Normalizer
import java.util.Locale

data class LibraryFilterState(
    val formats: Set<FormatType> = emptySet(),
    val libraries: Set<String> = emptySet(),
    val fileLocations: Set<LibraryFileLocation> = emptySet(),
    val authors: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            libraries.isEmpty() &&
            fileLocations.isEmpty() &&
            authors.isEmpty() &&
            series.isEmpty()
}

data class LibraryFilterFacets(
    val formats: List<FormatType> = emptyList(),
    val libraries: List<String> = emptyList(),
    val fileLocations: List<LibraryFileLocation> = emptyList(),
    val authors: List<String> = emptyList(),
    val series: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            libraries.isEmpty() &&
            fileLocations.isEmpty() &&
            authors.isEmpty() &&
            series.isEmpty()
}

enum class LibraryFileLocation {
    SERVER,
    LOCAL,
}

enum class LibrarySortMode {
    DATE_ADDED,
    LAST_READ,
    AUTHOR,
    TITLE,
    SERIES,
}

fun applyLibraryFilters(
    items: List<ReadableItem>,
    filters: LibraryFilterState,
    downloadedRemoteItemIds: Set<String> = emptySet(),
): List<ReadableItem> {
    if (filters.isEmpty) return items
    return items.filter { item ->
        filters.formats.matches(item.format) &&
            filters.libraries.matches(item.libraryId) &&
            filters.fileLocations.matches(item.fileLocation(downloadedRemoteItemIds)) &&
            filters.authors.matches(item.normalizedAuthor()) &&
            filters.series.matches(item.normalizedSeries())
    }
}

fun applyLibrarySearch(
    items: List<ReadableItem>,
    query: String,
): List<ReadableItem> {
    val tokens = query.normalizedSearchText()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (tokens.isEmpty()) return items
    return items.filter { item ->
        val searchableValues = item.librarySearchValues()
        tokens.all { token ->
            searchableValues.any { value -> value.normalizedSearchText().contains(token) }
        }
    }
}

fun applyLibrarySort(
    items: List<ReadableItem>,
    sortMode: LibrarySortMode,
    lastReadAtEpochMillisByItemId: Map<String, Long> = emptyMap(),
): List<ReadableItem> {
    return items
        .withIndex()
        .sortedWith(sortComparator(sortMode, lastReadAtEpochMillisByItemId))
        .map { it.value }
}

private fun sortComparator(
    sortMode: LibrarySortMode,
    lastReadAtEpochMillisByItemId: Map<String, Long>,
): Comparator<IndexedValue<ReadableItem>> = when (sortMode) {
    LibrarySortMode.DATE_ADDED -> compareByDescending<IndexedValue<ReadableItem>> {
        it.value.addedAtEpochMillis ?: Long.MIN_VALUE
    }
        .thenBy { it.index }

    LibrarySortMode.LAST_READ -> compareByDescending<IndexedValue<ReadableItem>> {
        lastReadAtEpochMillisByItemId[it.value.id] ?: Long.MIN_VALUE
    }
        .thenBy { it.index }

    LibrarySortMode.AUTHOR,
    LibrarySortMode.TITLE,
    LibrarySortMode.SERIES -> compareBy<IndexedValue<ReadableItem>, String>(String.CASE_INSENSITIVE_ORDER) {
        it.value.librarySortKey(sortMode)
    }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.value.title }
        .thenBy { it.index }
}

fun buildLibraryFilterFacets(
    items: List<ReadableItem>,
    downloadedRemoteItemIds: Set<String> = emptySet(),
): LibraryFilterFacets = LibraryFilterFacets(
    formats = items.map(ReadableItem::format).distinctByEnumOrder(),
    libraries = items.map(ReadableItem::libraryId).distinctByTextOrder(),
    fileLocations = items.map { item -> item.fileLocation(downloadedRemoteItemIds) }.distinctByEnumOrder(),
    authors = items.mapNotNull(ReadableItem::normalizedAuthor).distinctByAuthorOrder(),
    series = items.mapNotNull(ReadableItem::normalizedSeries).distinctByTextOrder(),
)

private fun <T> Set<T>.matches(value: T?): Boolean = isEmpty() || value in this

private fun ReadableItem.normalizedAuthor(): String? =
    bookMetadata?.author?.trim()?.takeIf(String::isNotEmpty)

private fun ReadableItem.normalizedSeries(): String? =
    comicMetadata?.series?.trim()?.takeIf(String::isNotEmpty)

private fun ReadableItem.librarySearchValues(): List<String> = listOfNotNull(
    title,
    libraryDisplayLabel(),
    libraryPrimaryText(),
    librarySecondaryText(),
    description,
    format.name,
    bookMetadata?.author,
    comicMetadata?.series,
    comicMetadata?.issueTitle,
    comicMetadata?.volume,
    comicMetadata?.issueNumber,
)

private fun String.normalizedSearchText(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')

private fun ReadableItem.fileLocation(
    downloadedRemoteItemIds: Set<String>,
): LibraryFileLocation = if (
    isLocalImport ||
    id in downloadedRemoteItemIds
) {
    LibraryFileLocation.LOCAL
} else {
    LibraryFileLocation.SERVER
}

fun ReadableItem.libraryAlphabetKey(
    sortMode: LibrarySortMode,
): String? = when (sortMode) {
    LibrarySortMode.DATE_ADDED -> null
    LibrarySortMode.LAST_READ -> null
    LibrarySortMode.AUTHOR -> normalizedAuthor()?.authorFacetKey() ?: libraryDisplayLabel()
    LibrarySortMode.TITLE -> title.trim().takeIf(String::isNotEmpty)
    LibrarySortMode.SERIES -> normalizedSeries() ?: libraryPrimaryText()
}

private fun ReadableItem.librarySortKey(
    sortMode: LibrarySortMode,
): String = libraryAlphabetKey(sortMode).orEmpty()

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
