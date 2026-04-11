package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
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
    Detailed,
    Grid;

    fun gridCells(
        maxWidth: Dp,
        maxHeight: Dp,
    ): androidx.compose.foundation.lazy.grid.GridCells = when (this) {
        Detailed -> if (maxWidth > maxHeight) {
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(3)
        } else {
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(2)
        }
        Grid -> androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 140.dp)
    }
}

internal val LibraryViewMode.label: String
    get() = when (this) {
        LibraryViewMode.Detailed -> "Details"
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

internal fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.STUMP -> "Stump"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> "Local"
}

internal val LibrarySortMode.label: String
    get() = when (this) {
        LibrarySortMode.DATE_ADDED -> "Date added"
        LibrarySortMode.LAST_READ -> "Last read"
        LibrarySortMode.AUTHOR -> "Author"
        LibrarySortMode.TITLE -> "Title"
        LibrarySortMode.SERIES -> "Series"
    }

internal val LibrarySortMode.usesAlphabetRail: Boolean
    get() = when (this) {
        LibrarySortMode.AUTHOR,
        LibrarySortMode.TITLE,
        LibrarySortMode.SERIES -> true
        LibrarySortMode.DATE_ADDED,
        LibrarySortMode.LAST_READ -> false
    }

internal val LibraryFileLocation.label: String
    get() = when (this) {
        LibraryFileLocation.SERVER -> "Server"
        LibraryFileLocation.LOCAL -> "Local"
    }

internal fun ReadableItem.sortFilterLetter(
    sortMode: LibrarySortMode,
): String? = libraryAlphabetKey(sortMode)?.filterLetter { it }

internal fun formatBadgeText(formatType: FormatType): String = when (formatType) {
    FormatType.CBZ -> "CBZ"
    FormatType.CBR -> "CBR"
    FormatType.EPUB -> "EPUB"
    FormatType.PDF -> ""
}

internal fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

internal fun String.filterLetter(
    letterKey: (String) -> String,
): String? {
    val first = letterKey(this).trim().firstOrNull() ?: return null
    return if (first.isLetterOrDigit()) {
        first.uppercaseChar().toString()
    } else {
        "#"
    }
}
