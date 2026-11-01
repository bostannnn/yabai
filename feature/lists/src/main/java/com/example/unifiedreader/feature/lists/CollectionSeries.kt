package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.core.model.kavitaCatalogSeriesKey
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey

private const val KavitaSeriesIdPrefix = "kavita:series:"

/**
 * The library's items by id as the lists look them up, with the series Kavita's collections hold:
 * a collection keeps whole series, and the catalog lists only their issues. A comic series is the
 * series as the library's grid shows it (its issues behind it); a book series, whose books the
 * library shows one by one, is its first book. A series the library doesn't hold stays out.
 */
fun listItemsById(items: List<ReadableItem>, shelves: List<ServerShelf>): Map<String, ReadableItem> {
    val byId = items.associateBy(ReadableItem::id)
    val seriesKeys = shelves.asSequence()
        .filter { shelf -> shelf.kind == UserListKind.ServerCollection }
        .flatMap { shelf -> shelf.itemIds }
        .filter { itemId -> itemId.startsWith(KavitaSeriesIdPrefix) && itemId !in byId }
        .map { itemId -> itemId.removePrefix(KavitaSeriesIdPrefix) }
        .toSet()
    if (seriesKeys.isEmpty()) return byId
    val membersByKey = items.asSequence()
        .filter { item -> item.sourceType == SourceType.KAVITA }
        .mapNotNull { item -> (item.kavitaSeriesGroupKey() ?: item.kavitaCatalogSeriesKey())?.takeIf(seriesKeys::contains)?.let { key -> key to item } }
        .groupBy(keySelector = { (key, _) -> key }, valueTransform = { (_, item) -> item })
    return byId + membersByKey.mapNotNull { (key, members) ->
        val issues = members.filter { item -> item.kavitaSeriesGroupKey() == key }
        val row = if (issues.isNotEmpty()) buildKavitaSeriesContainer(key, issues) else members.firstOrNull()
        row?.let { kavitaSeriesContainerId(key) to it }
    }
}
