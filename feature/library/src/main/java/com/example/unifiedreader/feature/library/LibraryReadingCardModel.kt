package com.example.unifiedreader.feature.library

/**
 * Where the reader is, under the reading card's author: "Chapter 4 of 6" over the chapter's name,
 * the chapter alone until its name is saved (or when the name only numbers it), or the page for a
 * comic or a PDF.
 */
internal data class LibraryReadingCardPlace(
    val label: String?,
    val line: String,
)

internal fun libraryReadingCardPlace(progress: ItemProgressSummary?): LibraryReadingCardPlace? {
    if (progress == null) return null
    val chapter = progress.chapterLabel()
    if (chapter != null) {
        val title = progress.chapterTitle?.trim()?.takeUnless { it.isEmpty() || GenericChapterTitle.matches(it) }
        return if (title != null) LibraryReadingCardPlace(label = chapter, line = title) else LibraryReadingCardPlace(label = null, line = chapter)
    }
    return progress.pageLabel()?.let { page -> LibraryReadingCardPlace(label = null, line = page) }
}

/**
 * The line over the card's bar: the percentage, with the page when the place above names the
 * chapter ("47% · Page 47 of 73"); "Not started" without progress.
 */
internal fun libraryReadingCardProgressLabel(progress: ItemProgressSummary?): String {
    if (progress == null) return "Not started"
    val page = progress.pageLabel().takeIf { progress.chapterLabel() != null }
    return listOfNotNull(progress.percentLabel, page).joinToString(" · ")
}

/** "About 25 min left", "About 1 h 20 min left", whole hours from ten on, "Less than a minute left". */
internal fun libraryReadingTimeLeftLabel(millis: Long): String {
    val minutes = (millis + 30_000L) / 60_000L
    if (minutes < 1L) return "Less than a minute left"
    if (minutes < 60L) return "About $minutes min left"
    val hours = minutes / 60L
    val rest = minutes % 60L
    return if (rest == 0L || hours >= 10L) "About ${(minutes + 30L) / 60L} h left" else "About $hours h $rest min left"
}

/**
 * Where the card's bar breaks, from 0 to 1: at each chapter's start when the reader saved them,
 * else at each page of a short comic. Null for one unbroken bar (no breaks known, or too many to
 * draw).
 */
internal fun libraryReadingCardSegments(progress: ItemProgressSummary?): List<Float>? {
    if (progress == null) return null
    val breaks = if (progress.chapterStarts.isNotEmpty()) {
        progress.chapterStarts.filter { start -> start > SegmentEdge && start < 1f - SegmentEdge }
    } else {
        val pages = progress.pageCount ?: return null
        if (progress.chapterCount != null || pages !in 2..MaxReadingCardSegments) return null
        (1 until pages).map { page -> page / pages.toFloat() }
    }
    if (breaks.isEmpty() || breaks.size >= MaxReadingCardSegments) return null
    return listOf(0f) + breaks + 1f
}

/** "Chapter 4 of 6", while the reader has placed the book in a chapter. */
private fun ItemProgressSummary.chapterLabel(): String? {
    val chapter = chapter ?: return null
    val count = chapterCount?.takeIf { it > 0 } ?: return null
    return "Chapter $chapter of $count"
}

/** "Page 47 of 73", when the page count is known. */
private fun ItemProgressSummary.pageLabel(): String? {
    val count = pageCount?.takeIf { it > 0 } ?: return null
    val page = (currentPage ?: (fraction.coerceIn(0f, 1f) * count).toInt().coerceAtLeast(1)).coerceIn(1, count)
    return "Page $page of $count"
}

// A title that only numbers the chapter ("Chapter 4", "Part II", "IV."), which the label already does.
private val GenericChapterTitle = Regex("""^(?:(?iu:chapter|ch\.|part|section|глава|часть)\s+)?(?:\d+|[IVXLCDM]+)\.?$""")

// A chapter that starts this close to either end of the book makes no segment of its own.
private const val SegmentEdge = 0.001f

// Past this many the segments are too thin to read, and the bar is drawn whole.
private const val MaxReadingCardSegments = 30
