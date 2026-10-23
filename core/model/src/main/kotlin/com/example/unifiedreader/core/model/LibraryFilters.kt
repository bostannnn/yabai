package com.example.unifiedreader.core.model

import java.text.Normalizer
import java.util.Locale

data class LibraryFilterState(
    val formats: Set<FormatType> = emptySet(),
    val libraries: Set<String> = emptySet(),
    val fileLocations: Set<LibraryFileLocation> = emptySet(),
    val readingStatuses: Set<LibraryReadingStatus> = emptySet(),
    val authors: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val publishers: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            libraries.isEmpty() &&
            fileLocations.isEmpty() &&
            readingStatuses.isEmpty() &&
            authors.isEmpty() &&
            series.isEmpty() &&
            publishers.isEmpty() &&
            genres.isEmpty() &&
            tags.isEmpty() &&
            languages.isEmpty()
}

fun LibraryFilterState.visibleInPanels(
    visiblePanels: Iterable<LibraryFilterPanel>,
): LibraryFilterState {
    val panels = visiblePanels.toSet()
    return copy(
        formats = formats.takeIf { LibraryFilterPanel.Format in panels }.orEmpty(),
        libraries = libraries.takeIf { LibraryFilterPanel.Library in panels }.orEmpty(),
        fileLocations = fileLocations.takeIf { LibraryFilterPanel.Location in panels }.orEmpty(),
        readingStatuses = readingStatuses.takeIf { LibraryFilterPanel.ReadingStatus in panels }.orEmpty(),
        authors = authors.takeIf { LibraryFilterPanel.Author in panels }.orEmpty(),
        series = series.takeIf { LibraryFilterPanel.Series in panels }.orEmpty(),
        publishers = publishers.takeIf { LibraryFilterPanel.Publisher in panels }.orEmpty(),
        genres = genres.takeIf { LibraryFilterPanel.Genre in panels }.orEmpty(),
        tags = tags.takeIf { LibraryFilterPanel.Tag in panels }.orEmpty(),
        languages = languages.takeIf { LibraryFilterPanel.Language in panels }.orEmpty(),
    )
}

data class LibraryFilterFacets(
    val formats: List<FormatType> = emptyList(),
    val libraries: List<String> = emptyList(),
    val fileLocations: List<LibraryFileLocation> = emptyList(),
    val authors: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    // Empty where nothing counted them (the SQL-assembled facets of the unfiltered paged view).
    val counts: LibraryFilterFacetCounts = LibraryFilterFacetCounts(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            libraries.isEmpty() &&
            fileLocations.isEmpty() &&
            authors.isEmpty() &&
            series.isEmpty() &&
            publishers.isEmpty() &&
            genres.isEmpty() &&
            tags.isEmpty() &&
            languages.isEmpty()
}

enum class LibraryFileLocation {
    SERVER,
    LOCAL,
}

enum class LibraryReadingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    FINISHED,
}

enum class LibrarySortMode {
    DATE_ADDED,
    LAST_READ,
    AUTHOR,
    TITLE,
    SERIES,
    ISSUE_COUNT,
}

fun applyLibraryFilters(
    items: List<ReadableItem>,
    filters: LibraryFilterState,
    downloadedRemoteItemIds: Set<String> = emptySet(),
    readingStatusProvider: (ReadableItem) -> LibraryReadingStatus? = { null },
): List<ReadableItem> {
    if (filters.isEmpty) return items
    return items.filter { item ->
        val facetMetadata = item.facetMetadata()
        filters.formats.matches(item.format) &&
            filters.libraries.matches(item.libraryId) &&
            filters.fileLocations.matches(item.fileLocation(downloadedRemoteItemIds)) &&
            filters.readingStatuses.matches(readingStatusProvider(item)) &&
            filters.authors.matchesAny(facetMetadata.authors) &&
            filters.series.matchesAny(facetMetadata.series) &&
            filters.publishers.matchesAny(facetMetadata.publishers) &&
            filters.genres.matchesAny(facetMetadata.genres) &&
            filters.tags.matchesAny(facetMetadata.tags) &&
            filters.languages.matchesAny(facetMetadata.languages)
    }
}

fun applyLibrarySearch(
    items: List<ReadableItem>,
    query: String,
    tieBreaker: Comparator<ReadableItem>? = null,
    preserveInputOrder: Boolean = false,
): List<ReadableItem> {
    val tokens = query.normalizedSearchText()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (tokens.isEmpty()) return items
    val phrase = tokens.joinToString(" ")
    val matches = items
        .mapIndexedNotNull { index, item ->
            item.librarySearchRank(tokens, phrase)?.let { rank ->
                LibrarySearchMatch(
                    item = item,
                    rank = rank,
                    originalIndex = index,
                )
            }
        }
    if (preserveInputOrder) {
        return matches.map(LibrarySearchMatch::item)
    }
    return matches
        .sortedWith(librarySearchMatchComparator(tieBreaker))
        .map(LibrarySearchMatch::item)
}

private fun librarySearchMatchComparator(
    tieBreaker: Comparator<ReadableItem>?,
): Comparator<LibrarySearchMatch> = Comparator { left, right ->
    left.rank.compareTo(right.rank)
        .takeIf { it != 0 }
        ?: tieBreaker?.compare(left.item, right.item)?.takeIf { it != 0 }
        ?: if (tieBreaker == null) {
            left.item.libraryDisplayLabel().compareTo(right.item.libraryDisplayLabel(), ignoreCase = true)
                .takeIf { it != 0 }
                ?: left.originalIndex.compareTo(right.originalIndex)
        } else {
            left.originalIndex.compareTo(right.originalIndex)
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
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.value.title }
        .thenBy { it.index }

    LibrarySortMode.ISSUE_COUNT -> compareByDescending<IndexedValue<ReadableItem>> {
        it.value.issueCount ?: Int.MIN_VALUE
    }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.value.title }
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
    authors = items.flatMap { item -> item.facetMetadata().authors }.distinctByAuthorOrder(),
    series = items.flatMap { item -> item.facetMetadata().series }.distinctByTextOrder(),
    publishers = items.flatMap { item -> item.facetMetadata().publishers }.distinctByTextOrder(),
    genres = items.flatMap { item -> item.facetMetadata().genres }.distinctByTextOrder(),
    tags = items.flatMap { item -> item.facetMetadata().tags }.distinctByTextOrder(),
    languages = items.flatMap { item -> item.facetMetadata().languages }.distinctByTextOrder(),
)

fun buildContextualLibraryFilterFacets(
    items: List<ReadableItem>,
    filters: LibraryFilterState,
    downloadedRemoteItemIds: Set<String> = emptySet(),
    readingStatusProvider: (ReadableItem) -> LibraryReadingStatus? = { null },
): LibraryFilterFacets {
    // Each dimension's values (and their counts) come from the items every *other* filter leaves.
    fun itemsWithout(dimensionCleared: LibraryFilterState): List<ReadableItem> = applyLibraryFilters(
        items = items,
        filters = dimensionCleared,
        downloadedRemoteItemIds = downloadedRemoteItemIds,
        readingStatusProvider = readingStatusProvider,
    )
    val fileLocationOf = { item: ReadableItem -> item.fileLocation(downloadedRemoteItemIds) }
    val formatItems = itemsWithout(filters.copy(formats = emptySet()))
    val locationItems = itemsWithout(filters.copy(fileLocations = emptySet()))
    val libraries = tallyTextFacet(itemsWithout(filters.copy(libraries = emptySet()))) { item -> listOf(item.libraryId) }
    val authors = tallyTextFacet(itemsWithout(filters.copy(authors = emptySet()))) { item -> item.facetMetadata().authors }
    val series = tallyTextFacet(itemsWithout(filters.copy(series = emptySet()))) { item -> item.facetMetadata().series }
    val publishers = tallyTextFacet(itemsWithout(filters.copy(publishers = emptySet()))) { item ->
        item.facetMetadata().publishers
    }
    val genres = tallyTextFacet(itemsWithout(filters.copy(genres = emptySet()))) { item -> item.facetMetadata().genres }
    val tags = tallyTextFacet(itemsWithout(filters.copy(tags = emptySet()))) { item -> item.facetMetadata().tags }
    val languages = tallyTextFacet(itemsWithout(filters.copy(languages = emptySet()))) { item ->
        item.facetMetadata().languages
    }
    return LibraryFilterFacets(
        formats = formatItems.map(ReadableItem::format).distinctByEnumOrder(),
        libraries = libraries.values.distinctByTextOrder(),
        fileLocations = locationItems.map(fileLocationOf).distinctByEnumOrder(),
        authors = authors.values.distinctByAuthorOrder(),
        series = series.values.distinctByTextOrder(),
        publishers = publishers.values.distinctByTextOrder(),
        genres = genres.values.distinctByTextOrder(),
        tags = tags.values.distinctByTextOrder(),
        languages = languages.values.distinctByTextOrder(),
        counts = LibraryFilterFacetCounts(
            formats = tallyEnumFacet(formatItems, items.mapTo(HashSet(), ReadableItem::format)) { item -> item.format },
            fileLocations = tallyEnumFacet(locationItems, items.mapTo(HashSet(), fileLocationOf), fileLocationOf),
            readingStatuses = tallyEnumFacet(
                items = itemsWithout(filters.copy(readingStatuses = emptySet())),
                domain = LibraryReadingStatus.entries,
                valueOf = readingStatusProvider,
            ),
            libraries = libraries.counts,
            authors = authors.counts,
            series = series.counts,
            publishers = publishers.counts,
            genres = genres.counts,
            tags = tags.counts,
            languages = languages.counts,
        ),
    )
}

/**
 * Assembles [LibraryFilterFacets] from facet values sourced per dimension from SQL
 * (`library_root_facets` via `getDistinctFacetValues`) instead of scanning the in-memory catalog.
 * Applies the exact same ordering/typing as [buildLibraryFilterFacets], and is independent of input
 * order (SQL `SELECT DISTINCT` has no ORDER BY), so for an unfiltered view — the only time the paged
 * path shows facets, since toggling any filter exits the paged path — the result is identical to
 * `buildLibraryFilterFacets(items)`. [fileLocations] stays a caller-provided runtime value: it isn't
 * persisted (see [LibraryFacetDimension]) because it depends on the downloaded/available set.
 */
fun assembleLibraryFilterFacets(
    formatNames: List<String>,
    libraries: List<String>,
    fileLocations: List<LibraryFileLocation>,
    authors: List<String>,
    series: List<String>,
    publishers: List<String>,
    genres: List<String>,
    tags: List<String>,
    languages: List<String>,
): LibraryFilterFacets = LibraryFilterFacets(
    formats = formatNames.mapNotNull { name -> runCatching { FormatType.valueOf(name) }.getOrNull() }
        .distinctByEnumOrder(),
    libraries = libraries.distinctByTextOrder(),
    fileLocations = fileLocations.distinctByEnumOrder(),
    authors = authors.distinctByAuthorOrder(),
    series = series.distinctByTextOrder(),
    publishers = publishers.distinctByTextOrder(),
    genres = genres.distinctByTextOrder(),
    tags = tags.distinctByTextOrder(),
    languages = languages.distinctByTextOrder(),
)

private fun <T> Set<T>.matches(value: T?): Boolean = isEmpty() || value in this
private fun Set<String>.matchesAny(values: List<String>): Boolean = isEmpty() || values.any(this::contains)

private fun ReadableItem.librarySearchRank(
    tokens: List<String>,
    phrase: String,
): Int? {
    val primaryValues = listOfNotNull(
        title,
        libraryDisplayLabel(),
        libraryPrimaryText(),
        comicMetadata?.series,
        bookMetadata?.seriesName,
    ).normalizedSearchValues()
    val secondaryValues = listOfNotNull(
        librarySecondaryText(),
        comicMetadata?.issueTitle,
        bookMetadata?.primaryAuthor(),
    )
        .plus(facetMetadata().authors)
        .normalizedSearchValues()
    val metadataValues = listOfNotNull(
        description,
        format.name,
        comicMetadata?.volume,
        comicMetadata?.issueNumber,
    )
        .plus(facetMetadata().publishers)
        .plus(facetMetadata().genres)
        .plus(facetMetadata().tags)
        .plus(facetMetadata().languages)
        .normalizedSearchValues()

    return when {
        primaryValues.any { value -> value == phrase } -> 0
        primaryValues.any { value -> value.containsWordPrefixSequence(tokens) } -> 1
        primaryValues.any { value -> tokens.all(value::contains) } -> 2
        secondaryValues.any { value -> tokens.all(value::contains) } -> 3
        metadataValues.any { value -> tokens.all(value::contains) } -> 4
        else -> null
    }
}

private fun Iterable<String>.normalizedSearchValues(): List<String> = map(String::normalizedSearchText)
    .filter(String::isNotBlank)

private fun String.containsWordPrefixSequence(
    tokens: List<String>,
): Boolean {
    if (tokens.isEmpty()) return true
    val words = split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (tokens.size > words.size) return false
    return words.windowed(tokens.size).any { candidate ->
        candidate.zip(tokens).all { (word, token) -> word.startsWith(token) }
    }
}

private data class LibrarySearchMatch(
    val item: ReadableItem,
    val rank: Int,
    val originalIndex: Int,
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
    LibrarySortMode.AUTHOR -> facetMetadata().authors.firstOrNull()?.authorFacetKey() ?: libraryDisplayLabel()
    LibrarySortMode.TITLE -> title.trim().takeIf(String::isNotEmpty)
    LibrarySortMode.SERIES -> facetMetadata().series.firstOrNull() ?: libraryPrimaryText()
    LibrarySortMode.ISSUE_COUNT -> null
}

private fun ReadableItem.librarySortKey(
    sortMode: LibrarySortMode,
): String = libraryAlphabetKey(sortMode).orEmpty()

/**
 * The persisted ORDER BY key for a rail sort (TITLE / AUTHOR / SERIES), so those grid views can page
 * from SQL. It is [librarySortKey] case-folded (`lowercase(ROOT)`), which a plain BINARY `ORDER BY`
 * reproduces the same way the in-memory comparator's `compareBy(CASE_INSENSITIVE_ORDER) { key }` does
 * — the tie-break on `title` stays a separate `ORDER BY` term. AUTHOR keys on the surname
 * (`authorFacetKey`), which SQL can't derive, so the key must be precomputed here at populate time.
 * Null for the non-rail sorts (they order by date / last-read / issue count, no alphabet rail).
 */
fun ReadableItem.librarySortIndexKey(
    sortMode: LibrarySortMode,
): String? = when (sortMode) {
    LibrarySortMode.AUTHOR,
    LibrarySortMode.TITLE,
    LibrarySortMode.SERIES -> librarySortKey(sortMode).trim().lowercase(Locale.ROOT)

    LibrarySortMode.DATE_ADDED,
    LibrarySortMode.LAST_READ,
    LibrarySortMode.ISSUE_COUNT -> null
}

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
