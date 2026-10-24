package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.libraryPrimaryText

/** One tile in See all: the home strip's tile, with what the sort modes need. */
internal data class LibraryShelfBrowserEntry(
    val item: ReadableItem,
    val title: String,
    val supportingText: String,
    val trailingText: String,
    val progressFraction: Float?,
    val isRead: Boolean,
    val activityAtEpochMillis: Long,
    val originalIndex: Int,
)

internal enum class ShelfBrowserSortMode {
    ShelfOrder,
    RecentActivity,
    Progress,
    Title,
    Series,
    Author,
    DateAdded,
}

internal fun LibraryShelfBrowserState.visibleShelfBrowserEntries(
    nowEpochMillis: Long,
    searchQuery: String,
    sortMode: ShelfBrowserSortMode,
): List<LibraryShelfBrowserEntry> = browserEntries(nowEpochMillis = nowEpochMillis)
    .searchedBy(searchQuery)
    .sortedFor(sortMode)

internal fun LibraryShelfBrowserState.browserEntries(
    nowEpochMillis: Long,
): List<LibraryShelfBrowserEntry> = when (this) {
    is LibraryShelfBrowserState.ContinueReading -> items.mapIndexed { index, shelfItem ->
        LibraryShelfBrowserEntry(
            item = shelfItem.item,
            title = shelfItem.item.libraryPrimaryText(),
            supportingText = shelfItem.tileSupportingText(),
            trailingText = shelfItem.tileMetaText(),
            progressFraction = shelfItem.progress.tileBarFraction,
            isRead = false,
            activityAtEpochMillis = shelfItem.progress.lastReadAtEpochMillis,
            originalIndex = index,
        )
    }

    is LibraryShelfBrowserState.NextInSeries -> items.mapIndexed { index, shelfItem ->
        LibraryShelfBrowserEntry(
            item = shelfItem.item,
            title = shelfItem.item.libraryPrimaryText(),
            supportingText = shelfItem.tileSupportingText(),
            trailingText = shelfItem.tileMetaText(),
            progressFraction = null,
            isRead = false,
            activityAtEpochMillis = shelfItem.lastActivityAtEpochMillis,
            originalIndex = index,
        )
    }

    is LibraryShelfBrowserState.RecentlyFinished -> items.mapIndexed { index, shelfItem ->
        LibraryShelfBrowserEntry(
            item = shelfItem.item,
            title = shelfItem.item.libraryPrimaryText(),
            supportingText = shelfItem.tileSupportingText(),
            trailingText = shelfItem.tileMetaText(nowEpochMillis),
            progressFraction = null,
            isRead = true,
            activityAtEpochMillis = shelfItem.progress.lastReadAtEpochMillis,
            originalIndex = index,
        )
    }
}

internal fun List<LibraryShelfBrowserEntry>.searchedBy(
    query: String,
): List<LibraryShelfBrowserEntry> {
    if (query.isBlank()) return this
    val byId = associateBy { entry -> entry.item.id }
    return applyLibrarySearch(
        items = map(LibraryShelfBrowserEntry::item),
        query = query,
    ).mapNotNull { item -> byId[item.id] }
}

internal fun List<LibraryShelfBrowserEntry>.sortedFor(
    sortMode: ShelfBrowserSortMode,
): List<LibraryShelfBrowserEntry> = when (sortMode) {
    ShelfBrowserSortMode.ShelfOrder -> this
    ShelfBrowserSortMode.RecentActivity -> sortedWith(
        compareByDescending<LibraryShelfBrowserEntry> { entry -> entry.activityAtEpochMillis }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Progress -> sortedWith(
        compareByDescending<LibraryShelfBrowserEntry> { entry -> entry.progressFraction ?: -1f }
            .thenByDescending { entry -> entry.activityAtEpochMillis }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Title -> sortedWith(
        compareBy<LibraryShelfBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { entry -> entry.title }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.item.title }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Series -> sortedWith(
        compareBy<LibraryShelfBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { entry ->
            entry.item.facetMetadata().series.firstOrNull().orEmpty()
        }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.title }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.Author -> sortedWith(
        compareBy<LibraryShelfBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { entry ->
            entry.item.facetMetadata().authors.firstOrNull()
                ?: entry.item.facetMetadata().writers.firstOrNull()
                ?: ""
        }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.title }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )

    ShelfBrowserSortMode.DateAdded -> sortedWith(
        compareByDescending<LibraryShelfBrowserEntry> { entry -> entry.item.addedAtEpochMillis ?: Long.MIN_VALUE }
            .thenBy(LibraryShelfBrowserEntry::originalIndex),
    )
}

/** The sorts that mean something for [kind]'s books. */
internal fun shelfBrowserSortModes(
    kind: LibraryShelfBrowserKind,
    items: List<ReadableItem>,
): List<ShelfBrowserSortMode> = buildList {
    add(ShelfBrowserSortMode.ShelfOrder)
    add(ShelfBrowserSortMode.RecentActivity)
    if (kind == LibraryShelfBrowserKind.ContinueReading) {
        add(ShelfBrowserSortMode.Progress)
    }
    add(ShelfBrowserSortMode.Title)
    if (items.any { item -> item.facetMetadata().series.isNotEmpty() }) {
        add(ShelfBrowserSortMode.Series)
    }
    if (items.any { item ->
            val metadata = item.facetMetadata()
            metadata.authors.isNotEmpty() || metadata.writers.isNotEmpty()
        }
    ) {
        add(ShelfBrowserSortMode.Author)
    }
    if (items.any { item -> item.addedAtEpochMillis != null }) {
        add(ShelfBrowserSortMode.DateAdded)
    }
}

internal fun ShelfBrowserSortMode.label(
    kind: LibraryShelfBrowserKind,
): String = when (this) {
    // The names the library's own sort menu uses.
    ShelfBrowserSortMode.ShelfOrder -> when (kind) {
        LibraryShelfBrowserKind.ContinueReading -> "Last read"
        LibraryShelfBrowserKind.NextInSeries -> "Shelf order"
        LibraryShelfBrowserKind.RecentlyFinished -> "Recently finished"
    }

    ShelfBrowserSortMode.RecentActivity -> "Recent activity"
    ShelfBrowserSortMode.Progress -> "Progress"
    ShelfBrowserSortMode.Title -> "Title"
    ShelfBrowserSortMode.Series -> "Series"
    ShelfBrowserSortMode.Author -> "Author"
    ShelfBrowserSortMode.DateAdded -> "Date added"
}
