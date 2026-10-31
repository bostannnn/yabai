package com.example.unifiedreader.core.model

import java.util.Locale

/** Where a search found a book when it wasn't by its title or a name on it. */
enum class LibrarySearchMatchSource(val label: String) {
    DESCRIPTION("In the description"),
    TAG("Tag"),
    GENRE("Genre"),
}

/**
 * Why a search result matched: the sentence of its description (or the tag) that holds the
 * searched words, with [matches] marking each word in [text].
 */
data class LibrarySearchSnippet(
    val text: String,
    val matches: List<IntRange>,
    val source: LibrarySearchMatchSource,
)

/** A tag a search matches, for Browse: "Japan" in Set in, on 96 books. */
data class LibrarySearchTagSuggestion(
    val chip: LibraryBrowseChip,
    val count: Int,
)

/**
 * The reason [item] matches [query], or null when its title, series or a name on it already
 * shows why (or nothing matches). A tag or genre comes before the description.
 */
fun librarySearchSnippet(item: ReadableItem, query: String): LibrarySearchSnippet? {
    val tokens = searchMatchTokens(query)
    if (tokens.isEmpty()) return null
    val facets = item.facetMetadata()
    val named = listOfNotNull(item.title, item.libraryPrimaryText(), item.librarySecondaryText()) +
        facets.authors + facets.writers + facets.series
    if (named.any { value -> value.holdsAll(tokens) }) return null
    facets.tags.firstOrNull { tag -> tag.holdsAll(tokens) }?.let { tag ->
        return LibrarySearchSnippet(text = tag, matches = tag.matchRanges(tokens), source = LibrarySearchMatchSource.TAG)
    }
    facets.genres.firstOrNull { genre -> genre.holdsAll(tokens) }?.let { genre ->
        return LibrarySearchSnippet(text = genre, matches = genre.matchRanges(tokens), source = LibrarySearchMatchSource.GENRE)
    }
    val description = item.description?.takeIf(String::isNotBlank) ?: return null
    val sentences = descriptionSentences(description)
    val sentence = sentences.firstOrNull { it.holdsAll(tokens) }
        ?: sentences.firstOrNull { sentence -> tokens.any { token -> sentence.searchKey().contains(token) } }
        ?: return null
    val shown = sentence.aroundFirstMatch(tokens)
    return LibrarySearchSnippet(text = shown, matches = shown.matchRanges(tokens), source = LibrarySearchMatchSource.DESCRIPTION)
}

/**
 * The tags, genres, moods and shelves of [items] that [query] names, for opening Browse from a
 * search, tags in the rows [sorting] puts them in: whole-name matches first, then names that start
 * with it, then the rest, most books first; at most [limit].
 */
fun librarySearchTagSuggestions(
    items: List<ReadableItem>,
    query: String,
    sorting: LibraryTagSorting,
    limit: Int = 6,
): List<LibrarySearchTagSuggestion> {
    val phrase = searchMatchTokens(query).joinToString(" ")
    if (phrase.length < MinSuggestionLength) return emptyList()
    val rows = listOf(
        LibraryBrowseRow.SET_IN,
        LibraryBrowseRow.WHEN,
        LibraryBrowseRow.ABOUT,
        LibraryBrowseRow.GENRE,
        LibraryBrowseRow.MOOD,
        LibraryBrowseRow.SHELF,
    )
    val counts = linkedMapOf<Pair<LibraryBrowseRow, String>, Pair<String, Int>>()
    items.forEach { item ->
        rows.forEach { row ->
            item.libraryBrowseValues(row, sorting)
                .filter { value -> value.searchKey().startsWithWord(phrase) }
                .distinctBy { value -> value.searchKey() }
                .forEach { value ->
                    val key = row to value.searchKey()
                    val (name, count) = counts[key] ?: (value to 0)
                    counts[key] = name to count + 1
                }
        }
    }
    return counts.map { (key, value) -> LibrarySearchTagSuggestion(LibraryBrowseChip(key.first, value.first), value.second) }
        .sortedWith(
            compareBy<LibrarySearchTagSuggestion> { suggestion ->
                val name = suggestion.chip.value.searchKey()
                when {
                    name == phrase -> 0
                    name.startsWith(phrase) -> 1
                    else -> 2
                }
            }.thenByDescending { suggestion -> suggestion.count },
        )
        .take(limit)
}

private const val MinSuggestionLength = 2
private const val SnippetMaxLength = 220
private const val SnippetLeadIn = 70
private val SentenceAbbreviations = setOf("mr", "mrs", "ms", "dr", "st", "jr", "sr", "vs", "no", "vol")

/** Lowercase with ё read as е, the way search reads; every character keeps its place. */
private fun String.searchKey(): String = lowercase(Locale.ROOT).replace('ё', 'е')

private fun searchMatchTokens(query: String): List<String> =
    query.searchKey().split(Regex("""\s+""")).map(String::trim).filter(String::isNotEmpty)

private fun String.holdsAll(tokens: List<String>): Boolean {
    val key = searchKey()
    return tokens.all(key::contains)
}

private fun String.startsWithWord(phrase: String): Boolean =
    startsWith(phrase) || contains(" $phrase") || contains("-$phrase")

private fun String.matchRanges(tokens: List<String>): List<IntRange> {
    val key = searchKey()
    val ranges = tokens.flatMap { token ->
        generateSequence(key.indexOf(token).takeIf { it >= 0 }) { from ->
            key.indexOf(token, from + token.length).takeIf { it >= 0 }
        }.map { start -> start until start + token.length }.toList()
    }
    // Overlapping words ("war", "warsaw") join into one mark.
    return ranges.sortedBy(IntRange::first).fold(mutableListOf()) { merged, range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
        merged
    }
}

/** The description's sentences: a full stop, "!" or "?" before a space ends one, but not "Mr." or "Dr.". */
private fun descriptionSentences(description: String): List<String> {
    val sentences = mutableListOf<String>()
    var start = 0
    var index = 0
    while (index < description.length) {
        val char = description[index]
        val atEnd = index + 1 >= description.length || description[index + 1].isWhitespace()
        if ((char == '.' || char == '!' || char == '?') && atEnd) {
            val word = description.substring(start, index).trimEnd().substringAfterLast(' ').lowercase(Locale.ROOT)
            if (char != '.' || word !in SentenceAbbreviations) {
                sentences += description.substring(start, index + 1).trim()
                start = index + 1
            }
        }
        index++
    }
    description.substring(start).trim().takeIf(String::isNotEmpty)?.let(sentences::add)
    return sentences.filter(String::isNotEmpty)
}

/** A long sentence cut to a window that starts a little before its first searched word. */
private fun String.aroundFirstMatch(tokens: List<String>): String {
    if (length <= SnippetMaxLength) return this
    val first = tokens.mapNotNull { token -> searchKey().indexOf(token).takeIf { it >= 0 } }.minOrNull() ?: 0
    val from = (first - SnippetLeadIn).coerceAtLeast(0).let { cut -> if (cut == 0) 0 else indexOf(' ', cut).takeIf { it in 0 until first } ?: cut }
    val to = (from + SnippetMaxLength).coerceAtMost(length).let { cut -> if (cut == length) cut else lastIndexOf(' ', cut).takeIf { it > first } ?: cut }
    return buildString {
        if (from > 0) append("…")
        append(this@aroundFirstMatch.substring(from, to).trim())
        if (to < this@aroundFirstMatch.length) append("…")
    }
}
