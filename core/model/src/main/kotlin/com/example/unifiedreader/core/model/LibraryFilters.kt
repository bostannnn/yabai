package com.example.unifiedreader.core.model

import java.text.Normalizer
import java.util.Locale

data class LibraryFilterState(
    val formats: Set<FormatType> = emptySet(),
    val libraries: Set<String> = emptySet(),
    val fileLocations: Set<LibraryFileLocation> = emptySet(),
    val readingStatuses: Set<LibraryReadingStatus> = emptySet(),
    // Null when the Pages filter leaves every length in.
    val pages: LibraryPageRange? = null,
    val authors: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val publishers: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    // About: any tag picked by name (the tags not sorted as places or times, and older picks).
    val tags: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    // Set in: places; a country also takes the books set in its cities.
    val places: Set<String> = emptySet(),
    // When: a pile ("1800s", "1930s") takes every era tag in it; a named period ("World War II") itself.
    val times: Set<String> = emptySet(),
    val moods: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = formats.isEmpty() &&
            libraries.isEmpty() &&
            fileLocations.isEmpty() &&
            readingStatuses.isEmpty() &&
            pages == null &&
            authors.isEmpty() &&
            series.isEmpty() &&
            publishers.isEmpty() &&
            genres.isEmpty() &&
            tags.isEmpty() &&
            languages.isEmpty() &&
            places.isEmpty() &&
            times.isEmpty() &&
            moods.isEmpty()
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
        pages = pages.takeIf { LibraryFilterPanel.Pages in panels },
        authors = authors.takeIf { LibraryFilterPanel.Author in panels }.orEmpty(),
        series = series.takeIf { LibraryFilterPanel.Series in panels }.orEmpty(),
        publishers = publishers.takeIf { LibraryFilterPanel.Publisher in panels }.orEmpty(),
        genres = genres.takeIf { LibraryFilterPanel.Genre in panels }.orEmpty(),
        tags = tags.takeIf { LibraryFilterPanel.Tag in panels }.orEmpty(),
        languages = languages.takeIf { LibraryFilterPanel.Language in panels }.orEmpty(),
        places = places.takeIf { LibraryFilterPanel.SetIn in panels }.orEmpty(),
        times = times.takeIf { LibraryFilterPanel.When in panels }.orEmpty(),
        moods = moods.takeIf { LibraryFilterPanel.Mood in panels }.orEmpty(),
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
    // Set in's places, a city's country among them; When's piles and named periods; moods.
    val places: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    // Empty where nothing counted them (the SQL-assembled facets of the unfiltered paged view).
    val counts: LibraryFilterFacetCounts = LibraryFilterFacetCounts(),
    // How long the items are, counted like [counts] with every filter but Pages applied.
    val pages: LibraryPageHistogram = LibraryPageHistogram(),
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
            languages.isEmpty() &&
            places.isEmpty() &&
            times.isEmpty() &&
            moods.isEmpty()
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
    // Shortest first; a series is all its issues together, unknown lengths last.
    PAGES,
}

fun applyLibraryFilters(
    items: List<ReadableItem>,
    filters: LibraryFilterState,
    downloadedRemoteItemIds: Set<String> = emptySet(),
    readingStatusProvider: (ReadableItem) -> LibraryReadingStatus? = { null },
    pageCountProvider: (ReadableItem) -> Int? = ReadableItem::libraryPageCount,
): List<ReadableItem> {
    if (filters.isEmpty) return items
    return items.filter { item ->
        val facetMetadata = item.facetMetadata()
        filters.formats.matches(item.format) &&
            filters.libraries.matches(item.libraryId) &&
            filters.fileLocations.matches(item.fileLocation(downloadedRemoteItemIds)) &&
            filters.readingStatuses.matches(readingStatusProvider(item)) &&
            (filters.pages?.matches(pageCountProvider(item)) ?: true) &&
            filters.authors.matchesAny(facetMetadata.authors) &&
            filters.series.matchesAny(facetMetadata.series) &&
            filters.publishers.matchesAny(facetMetadata.publishers) &&
            filters.genres.matchesAny(facetMetadata.genres) &&
            filters.tags.matchesAny(facetMetadata.tags) &&
            filters.languages.matchesAny(facetMetadata.languages) &&
            filters.places.matchesPlace(facetMetadata) &&
            filters.times.matchesTime(facetMetadata.tags) &&
            filters.moods.matchesAny(facetMetadata.moods)
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
    // An item's length for the Pages sort (a series tile's is all its issues together).
    pageCountOf: (ReadableItem) -> Int? = ReadableItem::libraryPageCount,
): List<ReadableItem> {
    return items
        .withIndex()
        .sortedWith(sortComparator(sortMode, lastReadAtEpochMillisByItemId, pageCountOf))
        .map { it.value }
}

private fun sortComparator(
    sortMode: LibrarySortMode,
    lastReadAtEpochMillisByItemId: Map<String, Long>,
    pageCountOf: (ReadableItem) -> Int?,
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

    LibrarySortMode.PAGES -> compareBy<IndexedValue<ReadableItem>, Int?>(nullsLast()) { pageCountOf(it.value) }
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
    pageCountProvider: (ReadableItem) -> Int? = ReadableItem::libraryPageCount,
    // Where each tag goes (Settings › Tags): places to Set in, times to When, the rest to About.
    tagKindOf: (String) -> LibraryTagKind = { LibraryTagKind.THEME },
): LibraryFilterFacets {
    // Each dimension's values (and their counts) come from the items every *other* filter leaves.
    fun itemsWithout(dimensionCleared: LibraryFilterState): List<ReadableItem> = applyLibraryFilters(
        items = items,
        filters = dimensionCleared,
        downloadedRemoteItemIds = downloadedRemoteItemIds,
        readingStatusProvider = readingStatusProvider,
        pageCountProvider = pageCountProvider,
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
    val tags = tallyTextFacet(itemsWithout(filters.copy(tags = emptySet()))) { item ->
        item.facetMetadata().tags.filter { tag -> tagKindOf(tag) == LibraryTagKind.THEME }
    }
    val languages = tallyTextFacet(itemsWithout(filters.copy(languages = emptySet()))) { item ->
        item.facetMetadata().languages
    }
    val places = tallyTextFacet(itemsWithout(filters.copy(places = emptySet()))) { item ->
        item.facetMetadata().libraryFilterPlaces(tagKindOf)
    }
    val times = tallyTextFacet(itemsWithout(filters.copy(times = emptySet()))) { item ->
        item.facetMetadata().libraryFilterTimes(tagKindOf)
    }
    val moods = tallyTextFacet(itemsWithout(filters.copy(moods = emptySet()))) { item -> item.facetMetadata().moods }
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
        places = places.values.distinctByTextOrder(),
        times = times.values.distinctByTextOrder(),
        moods = moods.values.distinctByTextOrder(),
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
            places = places.counts,
            times = times.counts,
            moods = moods.counts,
        ),
        pages = buildLibraryPageHistogram(itemsWithout(filters.copy(pages = null)), pageCountProvider),
    )
}

/** The filter section a tag of the book page or the search stands in; null for one Filters has no section for. */
val LibraryBrowseChip.filterPanel: LibraryFilterPanel?
    get() = when (row) {
        LibraryBrowseRow.SET_IN -> LibraryFilterPanel.SetIn
        LibraryBrowseRow.WHEN -> LibraryFilterPanel.When
        LibraryBrowseRow.ABOUT -> LibraryFilterPanel.Tag
        LibraryBrowseRow.GENRE -> LibraryFilterPanel.Genre
        LibraryBrowseRow.MOOD -> LibraryFilterPanel.Mood
        LibraryBrowseRow.READING -> LibraryFilterPanel.ReadingStatus
        LibraryBrowseRow.AUTHOR -> LibraryFilterPanel.Author
        LibraryBrowseRow.SERIES -> LibraryFilterPanel.Series
        LibraryBrowseRow.PUBLISHER -> LibraryFilterPanel.Publisher
        LibraryBrowseRow.LANGUAGE -> LibraryFilterPanel.Language
        LibraryBrowseRow.SHELF -> null
    }

/**
 * The filters narrowed to [chip] (a tag on the book page, one the search names): that section holds
 * just its value, the others stay as they are.
 */
fun LibraryFilterState.filteringBy(chip: LibraryBrowseChip): LibraryFilterState {
    val only = setOf(chip.value)
    return when (chip.row) {
        LibraryBrowseRow.SET_IN -> copy(places = only)
        LibraryBrowseRow.WHEN -> copy(times = only)
        LibraryBrowseRow.ABOUT -> copy(tags = only)
        LibraryBrowseRow.GENRE -> copy(genres = only)
        LibraryBrowseRow.MOOD -> copy(moods = only)
        LibraryBrowseRow.READING -> LibraryReadingStatus.entries.firstOrNull { status -> status.libraryBrowseLabel == chip.value }
            ?.let { status -> copy(readingStatuses = setOf(status)) } ?: this
        LibraryBrowseRow.AUTHOR -> copy(authors = only)
        LibraryBrowseRow.SERIES -> copy(series = only)
        LibraryBrowseRow.PUBLISHER -> copy(publishers = only)
        LibraryBrowseRow.LANGUAGE -> copy(languages = only)
        LibraryBrowseRow.SHELF -> this
    }
}

/**
 * Set in, When, About and Mood alone, counted as [buildContextualLibraryFilterFacets] counts them: the
 * library screen works these out with your tag sorting ([tagKindOf]) over the facets the rest came with.
 */
fun buildLibraryTagFilterFacets(
    items: List<ReadableItem>,
    filters: LibraryFilterState,
    tagKindOf: (String) -> LibraryTagKind,
    downloadedRemoteItemIds: Set<String> = emptySet(),
    readingStatusProvider: (ReadableItem) -> LibraryReadingStatus? = { null },
    pageCountProvider: (ReadableItem) -> Int? = ReadableItem::libraryPageCount,
): LibraryFilterFacets {
    fun itemsWithout(dimensionCleared: LibraryFilterState): List<ReadableItem> = applyLibraryFilters(
        items = items,
        filters = dimensionCleared,
        downloadedRemoteItemIds = downloadedRemoteItemIds,
        readingStatusProvider = readingStatusProvider,
        pageCountProvider = pageCountProvider,
    )
    val tags = tallyTextFacet(itemsWithout(filters.copy(tags = emptySet()))) { item ->
        item.facetMetadata().tags.filter { tag -> tagKindOf(tag) == LibraryTagKind.THEME }
    }
    val places = tallyTextFacet(itemsWithout(filters.copy(places = emptySet()))) { item ->
        item.facetMetadata().libraryFilterPlaces(tagKindOf)
    }
    val times = tallyTextFacet(itemsWithout(filters.copy(times = emptySet()))) { item ->
        item.facetMetadata().libraryFilterTimes(tagKindOf)
    }
    val moods = tallyTextFacet(itemsWithout(filters.copy(moods = emptySet()))) { item -> item.facetMetadata().moods }
    return LibraryFilterFacets(
        tags = tags.values.distinctByTextOrder(),
        places = places.values.distinctByTextOrder(),
        times = times.values.distinctByTextOrder(),
        moods = moods.values.distinctByTextOrder(),
        counts = LibraryFilterFacetCounts(tags = tags.counts, places = places.counts, times = times.counts, moods = moods.counts),
    )
}

/** [this] with Set in, When, About and Mood taken from [tagged] ([buildLibraryTagFilterFacets]). */
fun LibraryFilterFacets.withTagFacets(tagged: LibraryFilterFacets): LibraryFilterFacets = copy(
    tags = tagged.tags,
    places = tagged.places,
    times = tagged.times,
    moods = tagged.moods,
    counts = counts.copy(
        tags = tagged.counts.tags,
        places = tagged.counts.places,
        times = tagged.counts.times,
        moods = tagged.counts.moods,
    ),
)

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

// A picked country takes its cities: "Japan" matches a book tagged "Tokyo".
private fun Set<String>.matchesPlace(metadata: ReadableItemFacetMetadata): Boolean = isEmpty() ||
    (metadata.tags + metadata.locations).any { value -> value in this || libraryPlaceCountry(value) in this }

// A picked pile takes the era tags in it: "1800s" matches "19th century" and "1850s".
private fun Set<String>.matchesTime(tags: List<String>): Boolean = isEmpty() ||
    tags.any { tag -> tag in this || libraryEraSlot(tag)?.label in this }

/** A book's places for Set in: the tags sorted as places and its Locations, each city's country with it. */
fun ReadableItemFacetMetadata.libraryFilterPlaces(tagKindOf: (String) -> LibraryTagKind): List<String> =
    (tags.filter { tag -> tagKindOf(tag) == LibraryTagKind.PLACE } + locations)
        .flatMap { place -> listOfNotNull(place, libraryPlaceCountry(place)) }

/** A book's times for When: each era tag's pile ("19th century" is in "1800s"), a named period as itself. */
fun ReadableItemFacetMetadata.libraryFilterTimes(tagKindOf: (String) -> LibraryTagKind): List<String> =
    tags.filter { tag -> tagKindOf(tag) == LibraryTagKind.ERA }
        .map { tag -> libraryEraSlot(tag)?.label ?: tag }

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
    LibrarySortMode.ISSUE_COUNT,
    LibrarySortMode.PAGES -> null
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
    LibrarySortMode.ISSUE_COUNT,
    LibrarySortMode.PAGES -> null
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
