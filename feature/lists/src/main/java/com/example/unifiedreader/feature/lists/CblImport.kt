package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer

/** A reading order from a .cbl file (ComicRack's format, which Kavita imports too): its name and its issues in order. */
data class CblReadingList(
    val name: String?,
    val books: List<CblBook>,
)

data class CblBook(
    val series: String,
    val number: String,
    val volume: String? = null,
    val year: String? = null,
)

/** A .cbl matched to the library: the list's books in order (placeholders for the ones not found), and how many were found. */
data class CblImport(
    val name: String,
    val items: List<ReadableItem>,
    val found: Int,
) {
    val total: Int get() = items.size

    /** The toast: "Imported all 24 issues", "Imported 22 of 24 issues; 2 aren't in your library". */
    val summary: String
        get() = when (val missing = total - found) {
            0 -> if (total == 1) "Imported 1 issue" else "Imported all $total issues"
            else -> "Imported $found of $total issues; $missing ${if (missing == 1) "isn't" else "aren't"} in your library"
        }
}

/** The reading order in [text], or null when it holds no `<Book Series=… Number=…>`. */
fun parseCbl(text: String): CblReadingList? {
    val books = BookTag.findAll(text).mapNotNull { match ->
        val attributes = Attribute.findAll(match.groupValues[1]).associate { it.groupValues[1].lowercase() to xmlText(it.groupValues[2]) }
        val series = attributes["series"]?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val number = attributes["number"]?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        CblBook(series = series, number = number, volume = attributes["volume"]?.trim(), year = attributes["year"]?.trim())
    }.toList()
    if (books.isEmpty()) return null
    val name = NameTag.find(text.substringBefore("<Books"))?.groupValues?.get(1)?.let(::xmlText)?.trim()?.takeIf(String::isNotEmpty)
    return CblReadingList(name = name, books = books)
}

/**
 * Matches [cbl] to the comics in [library], from any source, by series and issue number (a volume
 * or year that agrees wins a tie). An issue not found keeps a placeholder, so the order stays whole.
 */
fun matchCbl(
    cbl: CblReadingList,
    library: Collection<ReadableItem>,
    fallbackName: String,
): CblImport {
    val comics = library
        .filter { item -> item.comicMetadata?.issueNumber != null && !item.isKavitaSeriesContainer() && !item.isStumpSeriesContainer() }
        .groupBy { item -> seriesKey(item.comicMetadata?.series ?: item.title) to numberKey(item.comicMetadata?.issueNumber.orEmpty()) }
    var found = 0
    val items = cbl.books.map { book ->
        val candidates = comics[seriesKey(book.series) to numberKey(book.number)].orEmpty()
        val best = candidates.maxByOrNull { item -> agreement(book, item) }
        if (best != null) found++
        best ?: placeholder(book)
    }
    return CblImport(name = cbl.name ?: fallbackName, items = items, found = found)
}

private fun agreement(book: CblBook, item: ReadableItem): Int {
    var score = 0
    if (book.year != null && item.publishedYear?.toString() == book.year) score += 2
    if (book.volume != null && item.comicMetadata?.volume?.contains(book.volume) == true) score += 1
    return score
}

/** An issue the library doesn't have: its row shows greyed with the series and number. */
private fun placeholder(book: CblBook): ReadableItem = ReadableItem(
    id = "cbl:${seriesKey(book.series)}#${numberKey(book.number)}",
    sourceType = SourceType.LOCAL,
    libraryId = "",
    title = "${book.series} #${book.number}",
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    comicMetadata = ComicMetadata(series = book.series, issueNumber = book.number, volume = book.volume),
)

/** "The Long Eclipse (2016)" and "long eclipse" match: case, a leading "the", a year and punctuation don't count. */
internal fun seriesKey(series: String): String = series
    .lowercase()
    .replace(Regex("\\(\\d{4}\\)"), "")
    .removePrefix("the ")
    .filter(Char::isLetterOrDigit)

/** "007", "7" and "7.0" are the same issue. */
internal fun numberKey(number: String): String {
    val trimmed = number.trim().lowercase()
    val (whole, fraction) = trimmed.split('.', limit = 2).let { it[0] to it.getOrNull(1) }
    val wholeKey = whole.trimStart('0').ifEmpty { if (whole.isNotEmpty()) "0" else whole }
    val fractionKey = fraction?.trimEnd('0')?.takeIf(String::isNotEmpty)
    return if (fractionKey == null) wholeKey else "$wholeKey.$fractionKey"
}

private fun xmlText(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

private val BookTag = Regex("<Book\\b([^>]*)>", RegexOption.IGNORE_CASE)
private val Attribute = Regex("(\\w+)\\s*=\\s*\"([^\"]*)\"")
private val NameTag = Regex("<Name>(.*?)</Name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
