package com.example.unifiedreader.feature.library.tags

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

/**
 * Where you put each source's tags and how to change it, for Browse, the book page, search and
 * Settings › Tags; the app provides it. Without it every tag is in About and nothing can be sorted.
 */
@Immutable
class LibraryTagSortingContext(
    /** Every source's sorting; null until the store has answered, so nothing shows a passing state. */
    val sortingBySource: Map<SourceType, LibraryTagSorting>?,
    /** Every source's books, for counting a tag's books and listing them in its sheet. */
    val items: List<ReadableItem>,
    val onSort: (SourceType, List<LibraryTagSortChange>) -> Unit,
    val coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    /** Opens Settings › Tags on a source; null where Settings can't be reached from. */
    val onOpenTagSettings: ((SourceType) -> Unit)?,
) {
    /** [source]'s sorting, empty when nothing is sorted there; null until the store has answered. */
    fun sortingFor(source: SourceType): LibraryTagSorting? = sortingBySource?.let { sortings -> sortings[source] ?: LibraryTagSorting() }

    companion object {
        val Empty = LibraryTagSortingContext(
            sortingBySource = emptyMap(),
            items = emptyList(),
            onSort = { _, _ -> },
            coverImageUrlProvider = { _, _ -> null },
            onOpenTagSettings = null,
        )
    }
}

val LocalLibraryTagSorting = compositionLocalOf { LibraryTagSortingContext.Empty }
