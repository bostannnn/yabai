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
 * A grid root plus the ids of the catalog items it represents: [root]'s own id for a standalone
 * item, or every issue id for a series container. The membership powers progress rollup (e.g.
 * LAST_READ = MAX over members) and series browse from SQL without the in-memory index.
 */
data class LibraryGridRoot(
    val root: ReadableItem,
    val memberItemIds: List<String>,
)

/**
 * The grid root set for a single source's items, each with its member item ids. Kavita/Stump
 * collapse issues into series containers; Grimmory/Local items are already roots (member = self).
 * Input must be the items for exactly [sourceType]. Shared by the persisted projection and the
 * in-memory index.
 */
fun deriveGridRootsWithMembers(
    sourceType: SourceType,
    sourceItems: List<ReadableItem>,
): List<LibraryGridRoot> = when (sourceType) {
    SourceType.KAVITA -> deriveKavitaSeriesGrouping(sourceItems).toGridRoots()
    SourceType.STUMP -> deriveStumpSeriesGrouping(sourceItems).toGridRoots()
    SourceType.GRIMMORY,
    SourceType.LOCAL -> sourceItems.map { item -> LibraryGridRoot(item, listOf(item.id)) }
}

/** Roots only (drops membership); kept for callers that don't need the member mapping. */
fun deriveGridRootItems(
    sourceType: SourceType,
    sourceItems: List<ReadableItem>,
): List<ReadableItem> = deriveGridRootsWithMembers(sourceType, sourceItems).map(LibraryGridRoot::root)

private fun LibrarySeriesGrouping.toGridRoots(): List<LibraryGridRoot> = rootItems.map { root ->
    val seriesKey = root.remoteId?.takeIf {
        root.isKavitaSeriesContainer() || root.isStumpSeriesContainer()
    }
    val memberItemIds = seriesKey
        ?.let { key -> issuesBySeriesKey[key]?.map(ReadableItem::id) }
        ?: listOf(root.id)
    LibraryGridRoot(root, memberItemIds)
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
