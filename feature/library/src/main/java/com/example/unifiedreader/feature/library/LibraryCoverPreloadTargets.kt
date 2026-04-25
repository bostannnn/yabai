package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

fun buildLibraryCoverPreloadTargets(
    libraries: List<Library>,
    items: List<ReadableItem>,
    sourceType: SourceType,
): List<ReadableItem> {
    if (sourceType == SourceType.LOCAL) return emptyList()
    val sourceIndex = LibrarySourceIndex.build(
        libraries = libraries,
        items = items,
    )
    val rootItems = sourceIndex.sourceItems(
        selectedSource = sourceType,
        selectedKavitaSeriesKey = null,
        selectedStumpSeriesKey = null,
    )
    val seriesChildren = sourceIndex.issueGroupsFor(sourceType)
        .values
        .flatten()
    return (rootItems + seriesChildren)
        .filter { item -> item.sourceType == sourceType }
        .distinctBy(ReadableItem::id)
}

