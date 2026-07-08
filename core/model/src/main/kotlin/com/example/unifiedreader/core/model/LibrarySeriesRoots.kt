package com.example.unifiedreader.core.model

/**
 * Result of grouping a source's raw catalog items into the library "root" set the grid shows.
 *
 * [rootItems] is the list of grid roots: standalone (non-issue) items plus one synthetic series
 * container per series key. [issuesBySeriesKey] maps each series key to its member issues, used for
 * series browse, progress rollup, and download actions.
 *
 * This is the source-agnostic derivation that both [feature:library]'s in-memory index and the
 * persisted `library_grid_roots` projection reuse, so series grouping stays defined in exactly one
 * place. See [buildKavitaSeriesContainer] / [buildStumpSeriesContainer] for container shape.
 */
data class LibrarySeriesGrouping(
    val rootItems: List<ReadableItem> = emptyList(),
    val issuesBySeriesKey: Map<String, List<ReadableItem>> = emptyMap(),
)

/**
 * The grid root set for a single source's items: Kavita/Stump collapse issues into series
 * containers; Grimmory/Local have no series grouping, so their items are already roots. Input must
 * be the items for exactly [sourceType]. Shared by the in-memory index and the persisted
 * `library_grid_roots` projection.
 */
fun deriveGridRootItems(
    sourceType: SourceType,
    sourceItems: List<ReadableItem>,
): List<ReadableItem> = when (sourceType) {
    SourceType.KAVITA -> deriveKavitaSeriesGrouping(sourceItems).rootItems
    SourceType.STUMP -> deriveStumpSeriesGrouping(sourceItems).rootItems
    SourceType.GRIMMORY,
    SourceType.LOCAL -> sourceItems
}

fun deriveKavitaSeriesGrouping(
    kavitaItems: List<ReadableItem>,
): LibrarySeriesGrouping = kavitaItems.deriveSeriesGrouping(
    seriesKeyProvider = ReadableItem::kavitaSeriesGroupKey,
    membershipCheck = ReadableItem::belongsToKavitaSeries,
    seriesContainerFactory = ::buildKavitaSeriesContainer,
)

fun deriveStumpSeriesGrouping(
    stumpItems: List<ReadableItem>,
): LibrarySeriesGrouping = stumpItems.deriveSeriesGrouping(
    seriesKeyProvider = ReadableItem::stumpSeriesGroupKey,
    membershipCheck = ReadableItem::belongsToStumpSeries,
    seriesContainerFactory = ::buildStumpSeriesContainer,
)

private fun List<ReadableItem>.deriveSeriesGrouping(
    seriesKeyProvider: (ReadableItem) -> String?,
    membershipCheck: ReadableItem.(String) -> Boolean,
    seriesContainerFactory: (String, List<ReadableItem>) -> ReadableItem,
): LibrarySeriesGrouping {
    val issuesBySeriesKey = groupIssuesBySeriesKey(seriesKeyProvider, membershipCheck)
    val directItems = filterNot { item -> seriesKeyProvider(item) != null }
    val seriesItems = issuesBySeriesKey.map { (seriesKey, issues) ->
        seriesContainerFactory(seriesKey, issues)
    }
    return LibrarySeriesGrouping(
        rootItems = directItems + seriesItems,
        issuesBySeriesKey = issuesBySeriesKey,
    )
}

private fun List<ReadableItem>.groupIssuesBySeriesKey(
    seriesKeyProvider: (ReadableItem) -> String?,
    membershipCheck: ReadableItem.(String) -> Boolean,
): Map<String, List<ReadableItem>> = mapNotNull { item ->
    val seriesKey = seriesKeyProvider(item)
    when {
        seriesKey == null -> null
        item.isKavitaSeriesContainer() || item.isStumpSeriesContainer() -> null
        else -> seriesKey to item
    }
}.groupBy(
    keySelector = { (seriesKey, _) -> seriesKey },
    valueTransform = { (_, item) -> item },
).mapValues { (seriesKey, issues) ->
    issues.filter { item -> item.membershipCheck(seriesKey) }
}
