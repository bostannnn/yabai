package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.libraryAlphabetKey
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import kotlin.math.roundToInt

data class ItemProgressSummary(
    val label: String,
    val fraction: Float,
    val currentPage: Int? = null,
    val pageCount: Int? = null,
    val lastReadAtEpochMillis: Long = 0L,
    val isFinished: Boolean = false,
) {
    val percentLabel: String
        get() = "${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"

    val pageProgressLabel: String?
        get() = pageCount?.takeIf { it > 0 }?.let { count ->
            val page = currentPage
                ?: (fraction.coerceIn(0f, 1f) * count)
                    .roundToInt()
                    .coerceIn(1, count)
            "${page.coerceIn(1, count)}/$count"
        }

    val detailLabel: String
        get() = listOfNotNull(percentLabel, pageProgressLabel).joinToString(" · ")
}

enum class LibraryViewMode {
    Grid;

    fun gridCells(
        maxWidth: Dp,
        maxHeight: Dp,
        gridCellSizeDp: Int = defaultLibraryGridCellSizeDp,
    ): androidx.compose.foundation.lazy.grid.GridCells =
        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(
            minSize = sanitizeLibraryGridCellSizeDp(gridCellSizeDp).dp,
        )
}

internal val LibraryViewMode.label: String
    get() = when (this) {
        LibraryViewMode.Grid -> "Grid"
    }

internal fun itemPrimaryMaxLines(
    item: ReadableItem,
): Int = when (item.kind) {
    ReadableItemKind.BOOK -> 2
    ReadableItemKind.COMIC -> if (item.comicMetadata?.series.isNullOrBlank()) 2 else 1
}

internal fun itemPrimaryText(
    item: ReadableItem,
): String = item.libraryPrimaryText()

internal fun itemSeriesBrowsePrimaryText(
    item: ReadableItem,
): String = itemIssueBrowsePrimaryText(item, includeSeries = false)

internal fun itemMixedIssueBrowsePrimaryText(
    item: ReadableItem,
): String = itemIssueBrowsePrimaryText(item, includeSeries = true)

private fun itemIssueBrowsePrimaryText(
    item: ReadableItem,
    includeSeries: Boolean,
): String {
    if (item.kind != ReadableItemKind.COMIC) return itemPrimaryText(item)
    val metadata = item.comicMetadata ?: return itemPrimaryText(item)
    val series = metadata.series?.trim()?.takeIf(String::isNotBlank)
    val issueNumber = metadata.issueNumber?.trim()?.takeIf(String::isNotBlank)
    val issueNumberLabel = issueNumber?.let { number -> "Issue $number" }
    val issueTitle = metadata.issueTitle
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.withoutSeriesPrefix(series)
        ?.withoutIssueNumberPrefix(issueNumber)
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { title -> series != null && title.equals(series, ignoreCase = true) }
        ?.takeUnless { title -> issueNumberLabel != null && title.equals(issueNumberLabel, ignoreCase = true) }
    val fallbackTitle = item.title
        .trim()
        .takeIf(String::isNotBlank)
        ?.withoutSeriesPrefix(series)
        ?.withoutIssueNumberPrefix(issueNumber)
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { title -> series != null && title.equals(series, ignoreCase = true) }
    val title = issueTitle ?: fallbackTitle
    val label = listOfNotNull(series.takeIf { includeSeries }, issueNumberLabel, title)
        .distinctBy { part -> part.lowercase() }
        .joinToString(" - ")
    return label.ifBlank {
        title ?: itemPrimaryText(item)
    }
}

internal fun itemSecondaryText(
    item: ReadableItem,
): String? = item.librarySecondaryText()

internal fun itemTertiaryText(
    item: ReadableItem,
): String? = when (item.kind) {
    ReadableItemKind.BOOK -> null
    ReadableItemKind.COMIC -> buildComicVolumeIssueLabel(item.comicMetadata)
}

internal fun itemPageCountText(
    item: ReadableItem,
    progress: ItemProgressSummary?,
): String? {
    val pageCount = item.pageCount ?: progress?.pageCount
    return pageCount?.takeIf { it > 0 }?.let { count -> "$count pages" }
}

internal fun buildComicVolumeIssueLabel(
    metadata: ComicMetadata?,
): String? {
    if (metadata == null) return null
    val parts = buildList {
        metadata.volume?.takeIf(String::isNotBlank)?.let { add("Vol. $it") }
        metadata.issueNumber?.takeIf(String::isNotBlank)?.let { add("Issue $it") }
    }
    return parts.takeIf(List<String>::isNotEmpty)?.joinToString(" • ")
}

internal val LibrarySortMode.label: String
    get() = when (this) {
        LibrarySortMode.DATE_ADDED -> "Date added"
        LibrarySortMode.LAST_READ -> "Last read"
        LibrarySortMode.AUTHOR -> "Author"
        LibrarySortMode.TITLE -> "Title"
        LibrarySortMode.SERIES -> "Series"
        LibrarySortMode.ISSUE_COUNT -> "Number of issues"
    }

internal val LibrarySortMode.usesAlphabetRail: Boolean
    get() = when (this) {
        LibrarySortMode.AUTHOR,
        LibrarySortMode.TITLE,
        LibrarySortMode.SERIES -> true
        LibrarySortMode.DATE_ADDED,
        LibrarySortMode.LAST_READ,
        LibrarySortMode.ISSUE_COUNT -> false
    }

internal val LibraryFileLocation.label: String
    get() = when (this) {
        LibraryFileLocation.SERVER -> "Server"
        LibraryFileLocation.LOCAL -> "Local"
    }

internal val LibraryReadingStatus.label: String
    get() = when (this) {
        LibraryReadingStatus.NOT_STARTED -> "Not started"
        LibraryReadingStatus.IN_PROGRESS -> "In progress"
        LibraryReadingStatus.FINISHED -> "Finished"
    }

internal fun ReadableItem.sortFilterLetter(
    sortMode: LibrarySortMode,
): String? = libraryAlphabetKey(sortMode)?.filterLetter { it }

private fun String.withoutSeriesPrefix(
    series: String?,
): String {
    if (series.isNullOrBlank() || !startsWith(series, ignoreCase = true)) return this
    return drop(series.length)
        .trim()
        .trimStart('-', ':', '.', '#')
        .trim()
}

private fun String.withoutIssueNumberPrefix(
    issueNumber: String?,
): String {
    val number = issueNumber?.trim()?.takeIf(String::isNotBlank) ?: return this
    val escapedNumber = Regex.escape(number)
    val patterns = listOf(
        Regex("""(?i)^issue\s+#?$escapedNumber\b\s*[-:.\s]*"""),
        Regex("""^#?$escapedNumber\b\s*[-:.\s]*"""),
    )
    return patterns.fold(this) { value, pattern ->
        value.replaceFirst(pattern, "").trim()
    }
}

internal fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

internal fun String.filterLetter(
    letterKey: (String) -> String,
): String? {
    val first = letterKey(this).trim().firstOrNull() ?: return null
    return if (first.isLetter()) {
        first.uppercaseChar().toString()
    } else {
        "#"
    }
}
