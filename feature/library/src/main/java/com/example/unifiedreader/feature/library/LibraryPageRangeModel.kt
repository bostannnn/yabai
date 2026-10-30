package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryPageStops
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// The Pages filter's slider, fields and labels, apart from how the sheet draws them. The slider
// runs over positions 0..LibraryPagePositionEnd, one whole position per [LibraryPageStops] entry,
// evenly spaced, so the short lengths most issues and books have get most of the track. A typed
// bound sits between the stops around it.

internal val LibraryPagePositionEnd: Int = LibraryPageStops.lastIndex

/** The marks under the slider: where 0, 100, 300 and 1,000+ are on the uneven scale. */
internal val LibraryPageScaleMarks: List<Pair<Float, String>> = listOf(0, 100, 300, LibraryPageStops.last())
    .map { stop ->
        val label = if (stop == LibraryPageStops.last()) "${formatPageCount(stop)}+" else formatPageCount(stop)
        libraryPagePosition(stop) / LibraryPagePositionEnd to label
    }

/** Where [range] puts the handles; with no filter they span the whole track. */
internal fun libraryPageSliderPositions(range: LibraryPageRange?): ClosedFloatingPointRange<Float> {
    val start = range?.min?.let(::libraryPagePosition) ?: 0f
    val end = range?.max?.let(::libraryPagePosition) ?: LibraryPagePositionEnd.toFloat()
    return start..maxOf(start, end)
}

/** A page count's place on the track, between the stops around it; from the last stop up, the end. */
internal fun libraryPagePosition(pageCount: Int): Float {
    if (pageCount <= 0) return 0f
    if (pageCount >= LibraryPageStops.last()) return LibraryPagePositionEnd.toFloat()
    val upper = LibraryPageStops.indexOfFirst { stop -> stop > pageCount }
    val lower = upper - 1
    return lower + (pageCount - LibraryPageStops[lower]).toFloat() / (LibraryPageStops[upper] - LibraryPageStops[lower])
}

/**
 * The handles after a drag step: the handle that moved snaps to the nearest stop, the other stays
 * where it was (on a typed bound between stops, say).
 */
internal fun snapLibraryPagePositions(
    current: ClosedFloatingPointRange<Float>,
    moved: ClosedFloatingPointRange<Float>,
): ClosedFloatingPointRange<Float> {
    val start = if (moved.start != current.start) moved.start.roundToInt().toFloat() else moved.start
    val end = if (moved.endInclusive != current.endInclusive) moved.endInclusive.roundToInt().toFloat() else moved.endInclusive
    return start..maxOf(start, end)
}

/**
 * The range the handles at [positions] pick. A handle on a stop takes that stop (the first and the
 * last mean no bound); one between stops keeps [current]'s bound, which a typed number put there.
 */
internal fun libraryPageRangeAt(
    positions: ClosedFloatingPointRange<Float>,
    current: LibraryPageRange?,
): LibraryPageRange? {
    val startIndex = positions.start.stopIndex()
    val endIndex = positions.endInclusive.stopIndex()
    val min = if (startIndex != null) LibraryPageStops[startIndex].takeIf { startIndex > 0 } else current?.min
    val max = if (endIndex != null) LibraryPageStops[endIndex].takeIf { endIndex < LibraryPagePositionEnd } else current?.max
    return libraryPageRange(min = min, max = max, includeUnknown = current?.includeUnknown ?: false)
}

/** A range from two bounds, null for none; a lower bound above the upper one swaps them. Null when neither bounds it. */
internal fun libraryPageRange(min: Int?, max: Int?, includeUnknown: Boolean): LibraryPageRange? {
    val low = min?.takeIf { it > 0 }
    if (low == null && max == null) return null
    return if (low != null && max != null && max < low) {
        LibraryPageRange(min = max.takeIf { it > 0 }, max = low, includeUnknown = includeUnknown)
    } else {
        LibraryPageRange(min = low, max = max, includeUnknown = includeUnknown)
    }
}

/** The histogram bars the handles cover, at least in part. */
internal fun libraryPageHighlightedBars(positions: ClosedFloatingPointRange<Float>): IntRange =
    floor(positions.start).toInt() until ceil(positions.endInclusive).toInt()

/** A field's text as a bound: its digits, null when it has none. */
internal fun parseLibraryPageBound(text: String): Int? = text.filter(Char::isDigit).take(MaxBoundDigits).toIntOrNull()

/** What a field types: digits only, five at most. */
internal fun libraryPageBoundInput(text: String): String = text.filter(Char::isDigit).take(MaxBoundDigits)

/** The range at the end of the section's label: "150–300", "150+", "Up to 300", or "Any" with no filter. */
internal fun libraryPageRangeText(range: LibraryPageRange?): String {
    val min = range?.min
    val max = range?.max
    return when {
        min != null && max != null -> if (min == max) formatPageCount(min) else "${formatPageCount(min)}–${formatPageCount(max)}"
        min != null -> "${formatPageCount(min)}+"
        max != null -> "Up to ${formatPageCount(max)}"
        else -> "Any"
    }
}

/** The applied chip above the grid: "150–300 pages", "Up to 50 pages or unknown". */
internal fun libraryPageRangeChipLabel(range: LibraryPageRange): String = buildString {
    append(libraryPageRangeText(range))
    append(" pages")
    if (range.includeUnknown) append(" or unknown")
}

/** The unknown-length row's supporting line: "114 books have no page count". */
internal fun libraryUnknownLengthText(count: Int): String =
    if (count == 1) "1 book has no page count" else "${formatPageCount(count)} books have no page count"

private fun formatPageCount(count: Int): String = "%,d".format(count)

private fun Float.stopIndex(): Int? = roundToInt().takeIf { index -> index.toFloat() == this }

private const val MaxBoundDigits = 5
