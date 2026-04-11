package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.belongsToKavitaSeries
import com.example.unifiedreader.core.model.belongsToStumpSeries
import com.example.unifiedreader.core.model.buildKavitaSeriesContainer
import com.example.unifiedreader.core.model.buildStumpSeriesContainer
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.kavitaSeriesGroupKey
import com.example.unifiedreader.core.model.stumpSeriesContainerId
import com.example.unifiedreader.core.model.stumpSeriesGroupKey

internal data class LibrarySourceIndex(
    private val librariesBySource: Map<SourceType, Map<String, String>>,
    private val rawItemsBySource: Map<SourceType, List<ReadableItem>>,
    private val rootItemsBySource: Map<SourceType, List<ReadableItem>>,
    private val kavitaIssuesBySeriesKey: Map<String, List<ReadableItem>>,
    private val stumpIssuesBySeriesKey: Map<String, List<ReadableItem>>,
    private val itemsById: Map<String, ReadableItem>,
) {
    fun sourceItems(
        selectedSource: SourceType,
        selectedKavitaSeriesKey: String?,
        selectedStumpSeriesKey: String?,
    ): List<ReadableItem> = when (selectedSource) {
        SourceType.KAVITA -> when (selectedKavitaSeriesKey) {
            null -> rootItemsBySource[SourceType.KAVITA].orEmpty()
            else -> kavitaIssuesBySeriesKey[selectedKavitaSeriesKey].orEmpty()
        }

        SourceType.STUMP -> when (selectedStumpSeriesKey) {
            null -> rootItemsBySource[SourceType.STUMP].orEmpty()
            else -> stumpIssuesBySeriesKey[selectedStumpSeriesKey].orEmpty()
        }

        SourceType.GRIMMORY,
        SourceType.LOCAL -> rootItemsBySource[selectedSource].orEmpty()
    }.orEmpty()

    fun selectedSourceLibraryTitlesById(
        sourceType: SourceType,
    ): Map<String, String> = librariesBySource[sourceType].orEmpty()

    fun resolveDownloadActionItems(
        selectedItem: ReadableItem,
    ): List<ReadableItem> = when {
        selectedItem.isKavitaSeriesContainer() -> selectedItem.remoteId?.let(kavitaIssuesBySeriesKey::get).orEmpty()
        selectedItem.isStumpSeriesContainer() -> selectedItem.remoteId?.let(stumpIssuesBySeriesKey::get).orEmpty()
        else -> listOf(selectedItem)
    }

    fun trackedRemoteDownloadItemIds(
        selectedItem: ReadableItem,
    ): Set<String> = buildSet {
        add(selectedItem.id)
        addAll(resolveDownloadActionItems(selectedItem).map(ReadableItem::id))
        selectedItem.kavitaSeriesGroupKey()?.let(::kavitaSeriesContainerId)?.let(::add)
        selectedItem.stumpSeriesGroupKey()?.let(::stumpSeriesContainerId)?.let(::add)
    }

    fun itemById(
        itemId: String,
    ): ReadableItem? = itemsById[itemId]

    fun issueGroupsFor(
        sourceType: SourceType,
    ): Map<String, List<ReadableItem>> = when (sourceType) {
        SourceType.KAVITA -> kavitaIssuesBySeriesKey
        SourceType.STUMP -> stumpIssuesBySeriesKey
        SourceType.GRIMMORY,
        SourceType.LOCAL -> emptyMap()
    }

    companion object {
        fun build(
            libraries: List<Library>,
            items: List<ReadableItem>,
        ): LibrarySourceIndex {
            val librariesBySource = libraries
                .groupBy(Library::sourceType)
                .mapValues { (_, librariesForSource) ->
                    librariesForSource.associate { library -> library.id to library.title }
                }
            val rawItemsBySource = items.groupBy(ReadableItem::sourceType)
            val kavitaItems = rawItemsBySource[SourceType.KAVITA].orEmpty()
            val stumpItems = rawItemsBySource[SourceType.STUMP].orEmpty()
            val kavitaIssuesBySeriesKey = kavitaItems.groupIssuesBySeriesKey(
                seriesKeyProvider = ReadableItem::kavitaSeriesGroupKey,
                membershipCheck = ReadableItem::belongsToKavitaSeries,
            )
            val stumpIssuesBySeriesKey = stumpItems.groupIssuesBySeriesKey(
                seriesKeyProvider = ReadableItem::stumpSeriesGroupKey,
                membershipCheck = ReadableItem::belongsToStumpSeries,
            )
            val rootItemsBySource = mapOf(
                SourceType.KAVITA to buildRootItems(
                    sourceItems = kavitaItems,
                    issuesBySeriesKey = kavitaIssuesBySeriesKey,
                    isIssue = { item -> item.kavitaSeriesGroupKey() != null },
                    seriesContainerFactory = ::buildKavitaSeriesContainer,
                ),
                SourceType.STUMP to buildRootItems(
                    sourceItems = stumpItems,
                    issuesBySeriesKey = stumpIssuesBySeriesKey,
                    isIssue = { item -> item.stumpSeriesGroupKey() != null },
                    seriesContainerFactory = ::buildStumpSeriesContainer,
                ),
                SourceType.GRIMMORY to rawItemsBySource[SourceType.GRIMMORY].orEmpty(),
                SourceType.LOCAL to rawItemsBySource[SourceType.LOCAL].orEmpty(),
            )
            return LibrarySourceIndex(
                librariesBySource = librariesBySource,
                rawItemsBySource = rawItemsBySource,
                rootItemsBySource = rootItemsBySource,
                kavitaIssuesBySeriesKey = kavitaIssuesBySeriesKey,
                stumpIssuesBySeriesKey = stumpIssuesBySeriesKey,
                itemsById = items.associateBy(ReadableItem::id),
            )
        }

        private fun buildRootItems(
            sourceItems: List<ReadableItem>,
            issuesBySeriesKey: Map<String, List<ReadableItem>>,
            isIssue: (ReadableItem) -> Boolean,
            seriesContainerFactory: (String, List<ReadableItem>) -> ReadableItem,
        ): List<ReadableItem> {
            val directItems = sourceItems.filterNot(isIssue)
            val seriesItems = issuesBySeriesKey.map { (seriesKey, issues) ->
                seriesContainerFactory(seriesKey, issues)
            }
            return directItems + seriesItems
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
    }
}
