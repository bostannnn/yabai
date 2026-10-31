package com.example.unifiedreader.core.model

import java.util.Locale

/** Where a tag goes in Browse and on the book page: its row and, for a place, its region (null: Elsewhere). */
data class LibraryTagChoice(
    val kind: LibraryTagKind,
    val region: LibraryPlaceRegion? = null,
)

/** Where a name to sort comes from: a tag, or a server's Locations (Kavita's comics), which are places already. */
enum class LibraryTagField {
    TAG,
    LOCATION,
}

/** You put [name] somewhere: [choice] for a tag, its region (a place) for a Location. */
data class LibraryTagSortChange(
    val field: LibraryTagField,
    val name: String,
    val choice: LibraryTagChoice,
)

/** How tags and Locations are matched: case and outer spaces don't matter. */
fun libraryTagKey(name: String): String = name.trim().lowercase(Locale.ROOT)

/**
 * A source's tags as you sorted them, by [libraryTagKey]. A tag nobody has sorted is About, so
 * Browse never shows a guess as a fact; a Location is always a place, in Elsewhere until it has a region.
 */
data class LibraryTagSorting(
    val tags: Map<String, LibraryTagChoice> = emptyMap(),
    val locationRegions: Map<String, LibraryPlaceRegion> = emptyMap(),
) {
    fun kindOf(tag: String): LibraryTagKind = tags[libraryTagKey(tag)]?.kind ?: LibraryTagKind.THEME

    fun regionOfTag(tag: String): LibraryPlaceRegion = tags[libraryTagKey(tag)]?.region ?: LibraryPlaceRegion.ELSEWHERE

    fun regionOfLocation(name: String): LibraryPlaceRegion = locationRegions[libraryTagKey(name)] ?: LibraryPlaceRegion.ELSEWHERE

    /** Where you put [name], or null while it waits to be sorted. */
    fun choiceOf(field: LibraryTagField, name: String): LibraryTagChoice? = when (field) {
        LibraryTagField.TAG -> tags[libraryTagKey(name)]
        LibraryTagField.LOCATION -> locationRegions[libraryTagKey(name)]?.let { region -> LibraryTagChoice(LibraryTagKind.PLACE, region) }
    }

    /** Whether you have put any tag under Set in or When; until then Browse offers to check the app's guesses. */
    val hasPlacesOrTimes: Boolean
        get() = tags.values.any { choice -> choice.kind != LibraryTagKind.THEME }

    fun with(changes: List<LibraryTagSortChange>): LibraryTagSorting {
        if (changes.isEmpty()) return this
        val nextTags = tags.toMutableMap()
        val nextLocations = locationRegions.toMutableMap()
        changes.forEach { change ->
            val key = libraryTagKey(change.name)
            when (change.field) {
                LibraryTagField.TAG -> nextTags[key] = change.choice.normalized()
                LibraryTagField.LOCATION -> nextLocations[key] = change.choice.region ?: LibraryPlaceRegion.ELSEWHERE
            }
        }
        return LibraryTagSorting(tags = nextTags, locationRegions = nextLocations)
    }
}

/** Only a place keeps a region. */
private fun LibraryTagChoice.normalized(): LibraryTagChoice =
    if (kind == LibraryTagKind.PLACE) this else copy(region = null)

/** A tag or Location of a source's books, for Settings › Tags. */
data class LibraryTagEntry(
    val field: LibraryTagField,
    /** As the books spell it (the first spelling met). */
    val name: String,
    /** How many books have it. */
    val count: Int,
    /** Where you put it; null while it waits under New. */
    val choice: LibraryTagChoice?,
    /** The app's guess: for a tag, a place with its region or a time, null for none; for a Location, its region or null. */
    val guess: LibraryTagChoice?,
) {
    val isNew: Boolean get() = choice == null

    /** Where it shows now: your choice, else About for a tag and Elsewhere for a Location. */
    val shownAs: LibraryTagChoice
        get() = choice ?: when (this.field) {
            LibraryTagField.TAG -> LibraryTagChoice(LibraryTagKind.THEME)
            LibraryTagField.LOCATION -> LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ELSEWHERE)
        }

    /** Where it would go if you took the app's guess: the guess, else where it shows now. */
    val proposed: LibraryTagChoice
        get() = choice ?: guess ?: shownAs

    /** Accepting the guess, or keeping a tag with no guess in About. */
    fun acceptChange(): LibraryTagSortChange = LibraryTagSortChange(field, name, guess ?: shownAs)
}

/**
 * Every tag and Location of [items] with where it is, where the app would put it and how many books
 * have it: most books first, then by name. A name that is both a tag and a Location is listed once
 * for each.
 */
fun libraryTagEntries(items: List<ReadableItem>, sorting: LibraryTagSorting): List<LibraryTagEntry> {
    val counts = linkedMapOf<Pair<LibraryTagField, String>, Pair<String, Int>>()
    fun count(field: LibraryTagField, values: List<String>) {
        values.distinctBy(::libraryTagKey).forEach { value ->
            val key = field to libraryTagKey(value)
            val (name, n) = counts[key] ?: (value to 0)
            counts[key] = name to n + 1
        }
    }
    items.forEach { item ->
        val facets = item.facetMetadata()
        count(LibraryTagField.TAG, facets.tags)
        count(LibraryTagField.LOCATION, facets.locations)
    }
    return counts.map { (key, value) ->
        val (field, _) = key
        val (name, n) = value
        LibraryTagEntry(
            field = field,
            name = name,
            count = n,
            choice = sorting.choiceOf(field, name),
            guess = when (field) {
                LibraryTagField.TAG -> libraryTagGuess(name)
                LibraryTagField.LOCATION -> libraryPlaceRegionGuess(name)?.let { region -> LibraryTagChoice(LibraryTagKind.PLACE, region) }
            },
        )
    }.sortedWith(compareByDescending<LibraryTagEntry> { it.count }.thenBy { libraryTagKey(it.name) })
}
