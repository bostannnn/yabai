package com.example.unifiedreader.feature.library

/**
 * The lines under the reading card's title. On its own the card has one: the author and where the
 * reader is ("Oona Fitch · Chapter 4 of 6", a comic's "Page 5 of 8"). [tall] (beside the shelves) it
 * has room for two: the author, then the place with the chapter's name when the name says more
 * than its number ("Chapter 4 of 6 · The Lighthouse Ledger"). A [footnote] (a reading order's
 * "Then ...") takes the place's spot.
 */
internal fun libraryReadingCardLines(
    author: String?,
    progress: ItemProgressSummary?,
    footnote: String?,
    tall: Boolean,
): List<String> {
    val place = footnote ?: if (tall) progress?.namedPlace() else progress?.shortPlace()
    return if (tall) listOfNotNull(author, place) else listOfNotNull(listOfNotNull(author, place).joinToString(" · ").ifEmpty { null })
}

/**
 * The line beside the card's bar: the percentage, with the time left when the journal knows the
 * pace ("47% · 25 min left"); "Not started" without progress.
 */
internal fun libraryReadingCardBarLabel(progress: ItemProgressSummary?, timeLeftMillis: Long?): String {
    if (progress == null) return "Not started"
    return listOfNotNull(progress.percentLabel, timeLeftMillis?.let(::libraryReadingTimeLeftLabel)).joinToString(" · ")
}

/** "25 min left", "1 h 20 min left", whole hours from ten on, "Under a minute left". */
internal fun libraryReadingTimeLeftLabel(millis: Long): String {
    val minutes = (millis + 30_000L) / 60_000L
    if (minutes < 1L) return "Under a minute left"
    if (minutes < 60L) return "$minutes min left"
    val hours = minutes / 60L
    val rest = minutes % 60L
    return if (rest == 0L || hours >= 10L) "${(minutes + 30L) / 60L} h left" else "$hours h $rest min left"
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

/** "Chapter 4 of 6" while the reader has placed the book in a chapter, else the page. */
private fun ItemProgressSummary.shortPlace(): String? = chapterLabel() ?: pageLabel()

/** The chapter with its name ("Chapter 4 of 6 · The Lighthouse Ledger") unless the name only numbers it, else the page. */
private fun ItemProgressSummary.namedPlace(): String? {
    val chapter = chapterLabel() ?: return pageLabel()
    val title = chapterTitle?.trim()?.takeUnless { it.isEmpty() || GenericChapterTitle.matches(it) }
    return if (title != null) "$chapter · $title" else chapter
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
