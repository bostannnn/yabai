package com.example.unifiedreader.core.model

fun trackedRemoteDownloadItemIds(
    selectedItem: ReadableItem,
    items: List<ReadableItem>,
): Set<String> = buildSet {
    add(selectedItem.id)
    addAll(
        resolveRemoteDownloadActionItems(
            selectedItem = selectedItem,
            items = items,
        ).map(ReadableItem::id),
    )
    selectedItem.kavitaSeriesGroupKey()?.let(::kavitaSeriesContainerId)?.let(::add)
    selectedItem.stumpSeriesGroupKey()?.let(::stumpSeriesContainerId)?.let(::add)
}
