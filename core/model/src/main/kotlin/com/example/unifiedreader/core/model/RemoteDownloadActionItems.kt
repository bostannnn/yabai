package com.example.unifiedreader.core.model

fun resolveRemoteDownloadActionItems(
    selectedItem: ReadableItem,
    items: List<ReadableItem>,
): List<ReadableItem> = when {
    selectedItem.isKavitaSeriesContainer() -> selectedItem.remoteId?.let { seriesKey ->
        items.filter { item -> item.belongsToKavitaSeries(seriesKey) }
    }.orEmpty()

    selectedItem.isStumpSeriesContainer() -> selectedItem.remoteId?.let { seriesKey ->
        items.filter { item -> item.belongsToStumpSeries(seriesKey) }
    }.orEmpty()

    else -> listOf(selectedItem)
}
