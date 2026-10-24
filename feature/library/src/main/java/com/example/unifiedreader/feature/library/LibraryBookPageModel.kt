package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.comicVolumeLabel
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.normalizedAuthors
import com.example.unifiedreader.core.model.ownIssueTitle
import com.example.unifiedreader.core.model.supportsManualReadStatusAction
import com.example.unifiedreader.core.model.volumeNumber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// What the book page says about one item, apart from how it draws it.

/** The text of a book page, worked out from the item, its progress and the books around it. */
internal data class LibraryBookPageText(
    val title: String,
    /** "Neon Ronin · 2 of 3"; a series container's issue count. */
    val seriesLine: String?,
    /** Authors, or a comic's writers and artists. */
    val creators: String?,
    /** "Continue · page 3 of 8", "Start reading", "Read again", "Show issues". */
    val primaryAction: String,
    /** "38% · read yesterday", while the book is under way or done. */
    val progressLine: String?,
    /** "3 / 8". */
    val progressPages: String?,
    val description: String?,
    val genres: List<String>,
    val tags: List<String>,
    /** Label to value: Format, Pages or Chapters, Language, Publisher, Added. */
    val facts: List<Pair<String, String>>,
)

internal val ReadableItem.isSeriesContainer: Boolean
    get() = isKavitaSeriesContainer() || isStumpSeriesContainer()

internal fun libraryBookPageText(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    seriesPosition: LibrarySeriesPosition?,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): LibraryBookPageText {
    val facets = item.facetMetadata()
    val comic = item.comicMetadata
    val series = facets.series.firstOrNull()
    val issueNumber = comic?.issueNumber?.trim()?.takeIf(String::isNotEmpty)
    // A Kavita volume ("Chapter v1", number "v1") reads as the series over "Volume 1".
    val volumeNumber = comic?.volumeNumber()
    val issueTitle = comic?.ownIssueTitle()
        ?.takeUnless { title -> title.equals(series, ignoreCase = true) }
    val title = when {
        item.kind != ReadableItemKind.COMIC || item.isSeriesContainer -> item.title
        issueTitle != null -> issueTitle
        series != null && volumeNumber != null -> series
        series != null && issueNumber != null -> "$series #$issueNumber"
        else -> item.libraryDisplayLabel()
    }
    val seriesLine = when {
        item.isSeriesContainer -> item.issueCount?.let { count -> if (count == 1) "1 issue" else "$count issues" }
        series == null -> null
        else -> listOfNotNull(
            when {
                volumeNumber != null && issueTitle != null -> "$series · ${comicVolumeLabel(volumeNumber)}"
                volumeNumber != null -> comicVolumeLabel(volumeNumber)
                issueTitle != null && issueNumber != null -> "$series #$issueNumber"
                else -> series
            },
            seriesPosition?.let { position -> "${position.index} of ${position.count}" },
        ).joinToString(" · ")
    }
    val creators = when (item.kind) {
        ReadableItemKind.BOOK -> item.bookMetadata?.normalizedAuthors().orEmpty()
        ReadableItemKind.COMIC -> (comic?.writers.orEmpty() + comic?.pencillers.orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
    }.joinToString(", ").ifEmpty { null }
    val started = progress?.hasStarted == true
    val finished = progress?.isComplete == true
    val primaryAction = when {
        item.isSeriesContainer -> "Show issues"
        finished -> "Read again"
        started -> listOfNotNull("Continue", progress?.positionLabel()).joinToString(" · ")
        else -> "Start reading"
    }
    val progressLine = progress?.takeIf { started || finished }?.let { summary ->
        listOfNotNull(
            summary.percentLabel,
            libraryLastReadLabel(summary.lastReadAtEpochMillis, nowMillis, zone),
        ).joinToString(" · ")
    }
    val progressPages = progress?.takeIf { (started || finished) && !item.isSeriesContainer }
        ?.pageProgressLabel
        ?.replace("/", " / ")
    val facts = buildList {
        add("Format" to item.format.name)
        // A comic or a PDF has its pages; an EPUB its chapters, once its reader has counted them.
        if (item.format == FormatType.EPUB) {
            progress?.chapterCount?.takeIf { it > 0 }?.let { count -> add("Chapters" to count.toString()) }
        } else {
            (item.pageCount ?: progress?.pageCount)?.takeIf { it > 0 }?.let { count -> add("Pages" to count.toString()) }
        }
        facets.languages.takeIf(List<String>::isNotEmpty)?.let { languages ->
            add("Language" to languages.map(::libraryLanguageName).distinct().joinToString(", "))
        }
        facets.publishers.takeIf(List<String>::isNotEmpty)?.let { add("Publisher" to it.joinToString(", ")) }
        libraryAddedLabel(item.addedAtEpochMillis, nowMillis, zone)?.let { add("Added" to it) }
    }
    return LibraryBookPageText(
        title = title,
        seriesLine = seriesLine?.ifEmpty { null },
        creators = creators,
        primaryAction = primaryAction,
        progressLine = progressLine,
        progressPages = progressPages,
        description = item.description?.let(::plainTextDescription)?.ifEmpty { null },
        genres = facets.genres,
        tags = facets.tags,
        facts = facts,
    )
}

/** The book page's read-state button, where the source takes one. */
internal enum class LibraryBookReadAction { MarkFinished, MarkUnread }

internal fun libraryBookReadAction(
    item: ReadableItem,
    progress: ItemProgressSummary?,
): LibraryBookReadAction? = when {
    !item.supportsManualReadStatusAction() -> null
    progress?.isComplete == true -> LibraryBookReadAction.MarkUnread
    else -> LibraryBookReadAction.MarkFinished
}

/** The book page's file actions: Download at the top, the removals in a row at the end. */
internal enum class LibraryBookFileAction {
    RemoveImported,
    Downloading,
    Download,
    RemoveDownload,
    ;

    val isRemoval: Boolean
        get() = this == RemoveImported || this == RemoveDownload
}

/**
 * An imported file can be removed; a server book downloads, shows that it is downloading, or
 * has its download removed. A partly downloaded series offers both Download and Remove download.
 */
internal fun libraryBookFileActions(
    item: ReadableItem,
    actionContext: LibraryItemActionContext,
): List<LibraryBookFileAction> = when {
    item.isLocalImport -> listOf(LibraryBookFileAction.RemoveImported)
    item.id in actionContext.downloadingItemIds -> listOf(LibraryBookFileAction.Downloading)
    else -> buildList {
        if (item.id in actionContext.downloadableItemIds) add(LibraryBookFileAction.Download)
        if (item.id in actionContext.deletableDownloadedItemIds) add(LibraryBookFileAction.RemoveDownload)
    }
}

/** Where a book sits in its series: the 2nd of 3. */
internal data class LibrarySeriesPosition(
    val index: Int,
    val count: Int,
)

/** "chapter 4 of 6" for an EPUB its reader placed, else "page 3 of 8". */
private fun ItemProgressSummary.positionLabel(): String? {
    val chapter = chapter
    val chapterCount = chapterCount
    if (chapter != null && chapterCount != null && chapterCount > 0) return "chapter $chapter of $chapterCount"
    val count = pageCount?.takeIf { it > 0 } ?: return null
    val page = (currentPage ?: (fraction.coerceIn(0f, 1f) * count).toInt()).coerceIn(1, count)
    return "page $page of $count"
}

/** "read today", "read yesterday", "read 3 days ago", "read on 12 Oct" (with the year when not this one). */
internal fun libraryLastReadLabel(
    lastReadAtEpochMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
): String? {
    if (lastReadAtEpochMillis <= 0L) return null
    val read = Instant.ofEpochMilli(lastReadAtEpochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(read, today)
    return when {
        days <= 0L -> "read today"
        days == 1L -> "read yesterday"
        days < 7L -> "read $days days ago"
        else -> "read on ${read.format(dateFormatter(read, today))}"
    }
}

/** When a book came into the library: "21 Oct", with the year when not this one. */
internal fun libraryAddedLabel(
    addedAtEpochMillis: Long?,
    nowMillis: Long,
    zone: ZoneId,
): String? {
    val added = addedAtEpochMillis?.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return null
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return added.format(dateFormatter(added, today))
}

private fun dateFormatter(read: LocalDate, today: LocalDate): DateTimeFormatter =
    if (read.year == today.year) {
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    }

/**
 * A language as people read it: "en" is "English", "pt-BR" "Portuguese (Brazil)", in [locale]'s
 * words. A value that is not a language code (already "English", say) stays as it is.
 */
internal fun libraryLanguageName(value: String, locale: Locale = Locale.getDefault()): String {
    val code = value.trim()
    if (!LanguageCodePattern.matches(code)) return code
    val language = Locale.forLanguageTag(code.replace('_', '-'))
    return language.getDisplayName(locale)
        .takeIf { name -> name.isNotBlank() && !name.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { first -> first.titlecase(locale) }
        ?: code
}

private val LanguageCodePattern = Regex("(?i)^[a-z]{2,3}([-_][a-z0-9]{2,8})*$")

/**
 * A server summary or an EPUB's `dc:description` as plain text: paragraphs and line breaks kept
 * as single new lines (a blank line would take one of the four lines the page shows folded),
 * every other tag dropped, the common entities decoded, and Markdown links (Kavita's AniList
 * summaries) reduced to their text.
 */
internal fun plainTextDescription(raw: String): String = raw
    .replace(Regex("\\[([^\\]]+)]\\((?:https?://|www\\.)[^)\\s]*\\)"), "$1")
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?i)</(p|div|li|h[1-6])>"), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .lines()
    .joinToString("\n") { line -> line.trim() }
    .replace(Regex("\n{2,}"), "\n")
    .trim()

/** The book page's three rows of other books, each in its own order. */
internal data class LibraryBookPageRelated(
    val seriesName: String? = null,
    val seriesPosition: LibrarySeriesPosition? = null,
    val moreInSeries: List<ReadableItem> = emptyList(),
    val authorName: String? = null,
    val moreByAuthor: List<ReadableItem> = emptyList(),
    val similar: List<ReadableItem> = emptyList(),
)

/**
 * The rows under a book, from [pool] (the books of its source): the rest of its series in issue
 * order; more by its first author or writer, one book per series; and books that share at least
 * two of its genres and tags, most shared first, one per series. A book shows in one row only. A series container is compared with the
 * other series containers, an issue or a book with the other issues and books.
 */
internal fun libraryBookPageRelated(
    item: ReadableItem,
    pool: List<ReadableItem>,
): LibraryBookPageRelated {
    val candidates = pool.filter { other ->
        other.sourceType == item.sourceType && other.isSeriesContainer == item.isSeriesContainer
    }
    val itemFacets = item.facetMetadata()
    val seriesName = itemFacets.series.firstOrNull()?.takeUnless { item.isSeriesContainer }
    val seriesMembers = seriesName?.let { name ->
        candidates
            .filter { other -> other.facetMetadata().series.any { it.equals(name, ignoreCase = true) } }
            .let { members -> if (members.none { it.id == item.id }) members + item else members }
            .distinctBy(ReadableItem::id)
            .sortedInCanonicalSeriesIssueOrder()
    }.orEmpty()
    val seriesPosition = seriesMembers.indexOfFirst { it.id == item.id }
        .takeIf { seriesMembers.size > 1 && it >= 0 }
        ?.let { index -> LibrarySeriesPosition(index = index + 1, count = seriesMembers.size) }
    val moreInSeries = seriesMembers.filterNot { it.id == item.id }.take(RelatedRowLimit)
    val shown = moreInSeries.mapTo(mutableSetOf(item.id), ReadableItem::id)

    val authorName = (itemFacets.authors + itemFacets.writers).firstOrNull()
    // The author's other works: one book per series (its first), none from this book's series,
    // by name. Kavita titles volumes "Chapter v1", so sorting by title mixed the series up.
    val moreByAuthor = authorName?.let { name ->
        candidates
            .filter { other ->
                val facets = other.facetMetadata()
                other.id !in shown &&
                    facets.series.none { series -> seriesName != null && series.equals(seriesName, ignoreCase = true) } &&
                    (facets.authors + facets.writers).any { it.equals(name, ignoreCase = true) }
            }
            .groupBy { other -> other.facetMetadata().series.firstOrNull()?.lowercase() ?: other.id }
            .values
            .map { works -> works.sortedInCanonicalSeriesIssueOrder().first() }
            .sortedBy { other -> other.libraryPrimaryText().lowercase() }
            .take(RelatedRowLimit)
    }.orEmpty()
    moreByAuthor.mapTo(shown, ReadableItem::id)

    val itemThemes = (itemFacets.genres + itemFacets.tags).mapTo(mutableSetOf(), String::lowercase)
    val similar = if (itemThemes.size < SimilarSharedThemes) {
        emptyList()
    } else {
        candidates
            .asSequence()
            .filter { other -> other.id !in shown }
            .map { other ->
                val facets = other.facetMetadata()
                other to (facets.genres + facets.tags).map(String::lowercase).toSet().count(itemThemes::contains)
            }
            .filter { (_, shared) -> shared >= SimilarSharedThemes }
            .sortedWith(compareByDescending<Pair<ReadableItem, Int>> { it.second }.thenBy { it.first.title.lowercase() })
            .map { it.first }
            // One book per series: four volumes of one manga are one suggestion, not four.
            .distinctBy { other -> other.facetMetadata().series.firstOrNull()?.lowercase() ?: other.id }
            .take(SimilarLimit)
            .toList()
    }
    return LibraryBookPageRelated(
        seriesName = seriesName,
        seriesPosition = seriesPosition,
        moreInSeries = moreInSeries,
        authorName = authorName.takeIf { moreByAuthor.isNotEmpty() },
        moreByAuthor = moreByAuthor,
        similar = similar,
    )
}

private const val RelatedRowLimit = 12
private const val SimilarLimit = 8
private const val SimilarSharedThemes = 2
