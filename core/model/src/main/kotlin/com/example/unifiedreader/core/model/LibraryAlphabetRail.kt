package com.example.unifiedreader.core.model

/**
 * One bucket of the alphabet rail as counted in SQL: [firstChar] is the first character of the
 * (case-folded) sort key shared by [count] grid roots, and the buckets are supplied in sort order
 * (ascending by key). Empty/absent keys have a blank [firstChar].
 */
data class LibraryAlphabetBucket(
    val firstChar: String?,
    val count: Int,
)

/**
 * A jump target on the paged alphabet rail: [letter] is the section label (an uppercase letter, or
 * "#" for non-letters) and [itemOffset] is the 0-based index of the first grid root in that section
 * within the full ordered set — so the paged grid can `scrollToItem` there without materializing the
 * whole list.
 */
data class LibraryAlphabetSection(
    val letter: String,
    val itemOffset: Int,
)

/**
 * Builds the paged alphabet rail from SQL bucket counts. Mirrors the in-memory rail
 * (`ReadableItem.sortFilterLetter` + `alphabetSectionStarts` + `alphabetNavigationSections`): each
 * bucket's first character maps to a section label the same way `filterLetter` does (uppercase letter,
 * else "#", blank keys unsectioned), the offset of a label is the cumulative count before its first
 * bucket, and the sections are ordered "#" first then case-insensitively. [buckets] must be in sort
 * order (the SQL `ORDER BY MIN(sortKey)`).
 */
fun assembleLibraryAlphabetSections(
    buckets: List<LibraryAlphabetBucket>,
): List<LibraryAlphabetSection> {
    var runningOffset = 0
    val offsetByLetter = LinkedHashMap<String, Int>()
    buckets.forEach { bucket ->
        bucket.firstChar.railSectionLetter()?.let { letter ->
            if (letter !in offsetByLetter) offsetByLetter[letter] = runningOffset
        }
        runningOffset += bucket.count
    }
    return offsetByLetter.entries
        .sortedWith(
            compareBy<Map.Entry<String, Int>> { entry -> entry.key != NON_LETTER_SECTION }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.key },
        )
        .map { entry -> LibraryAlphabetSection(letter = entry.key, itemOffset = entry.value) }
}

// Same mapping as feature:library's String.filterLetter, kept here so the paged rail and the in-memory
// rail label identically: a letter uppercases, anything else is the "#" section, blank is unsectioned.
private fun String?.railSectionLetter(): String? {
    val first = this?.firstOrNull() ?: return null
    return if (first.isLetter()) first.uppercaseChar().toString() else NON_LETTER_SECTION
}

private const val NON_LETTER_SECTION = "#"
