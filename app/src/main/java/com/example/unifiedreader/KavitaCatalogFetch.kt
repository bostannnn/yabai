package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSeriesUpdateEntry

internal data class KavitaCatalogFetchPlan(
    val entries: List<KavitaChapterCatalogEntry>,
)

internal data class KavitaFastCatalogDeltaPlan(
    val libraries: List<Library>,
    val changedEntries: List<KavitaChapterCatalogEntry>,
    val currentItems: List<ReadableItem>,
    val upsertItems: List<ReadableItem>,
    val removeItemIds: Set<String>,
    val syncGroups: List<RemoteCatalogSyncGroupEntity>,
    val removeSyncGroupIds: Set<String>,
)

internal suspend fun KavitaCatalogSyncService.fetchKavitaCatalog(
    api: KavitaApi,
    accessToken: String,
    libraries: List<Library>,
    series: List<KavitaSeriesEntry>,
    onProgress: (KavitaSyncProgress) -> Unit,
): KavitaCatalogFetchPlan {
    return KavitaCatalogFetchPlan(
        entries = fetchSeriesEntries(
            api = api,
            accessToken = accessToken,
            series = series,
            onProgress = onProgress,
        ),
    )
}

internal fun selectChangedKavitaSeries(
    series: List<KavitaSeriesEntry>,
    existingSyncGroups: List<RemoteCatalogSyncGroup>,
    recentlyUpdatedSeries: List<KavitaSeriesUpdateEntry>,
): List<KavitaSeriesEntry> {
    val existingSyncGroupsById = existingSyncGroups.associateBy(RemoteCatalogSyncGroup::groupId)
    val recentUpdateMarkers = recentlyUpdatedSeries.kavitaRecentUpdateMarkers()
    return series.filter { entry ->
        val groupId = entry.kavitaCatalogSyncGroupId()
        val existingGroup = existingSyncGroupsById[groupId]
        val currentFingerprint = entry.kavitaCatalogFingerprint(
            recentUpdateMarker = recentUpdateMarkers.markerFor(
                entry = entry,
                groupId = groupId,
            ),
        )
        existingGroup == null ||
            existingGroup.fingerprint != currentFingerprint
    }
}

internal fun buildKavitaFastCatalogDeltaPlan(
    libraries: List<Library>,
    series: List<KavitaSeriesEntry>,
    existingItems: List<ReadableItem>,
    existingSyncGroups: List<RemoteCatalogSyncGroup>,
    changedSeries: List<KavitaSeriesEntry>,
    changedEntries: List<KavitaChapterCatalogEntry>,
    recentlyUpdatedSeries: List<KavitaSeriesUpdateEntry>,
    nowEpochMillis: Long,
): KavitaFastCatalogDeltaPlan {
    val currentGroupIds = series
        .mapTo(mutableSetOf(), KavitaSeriesEntry::kavitaCatalogSyncGroupId)
    val existingSyncGroupIds = existingSyncGroups
        .mapTo(mutableSetOf(), RemoteCatalogSyncGroup::groupId)
    val existingItemGroupIds = existingItems
        .mapNotNull(ReadableItem::kavitaCatalogSyncGroupId)
        .toSet()
    val removedItemGroupIds = (existingSyncGroupIds + existingItemGroupIds) - currentGroupIds
    val changedGroupIds = changedSeries
        .mapTo(mutableSetOf(), KavitaSeriesEntry::kavitaCatalogSyncGroupId)
    val changedItems = changedEntries.map(KavitaChapterCatalogEntry::item)
    val changedItemIds = changedItems.mapTo(mutableSetOf(), ReadableItem::id)
    val removeItemIds = existingItems
        .asSequence()
        .filter { item ->
            val groupId = item.kavitaCatalogSyncGroupId()
            groupId in removedItemGroupIds ||
                (groupId in changedGroupIds && item.id !in changedItemIds)
        }
        .mapTo(mutableSetOf(), ReadableItem::id)
    val currentItemsById = linkedMapOf<String, ReadableItem>()
    existingItems
        .asSequence()
        .filterNot { item ->
            val groupId = item.kavitaCatalogSyncGroupId()
            item.id in removeItemIds || groupId in changedGroupIds
        }
        .forEach { item -> currentItemsById[item.id] = item }
    changedItems.forEach { item -> currentItemsById[item.id] = item }
    return KavitaFastCatalogDeltaPlan(
        libraries = libraries,
        changedEntries = changedEntries,
        currentItems = currentItemsById.values.toList(),
        upsertItems = changedItems,
        removeItemIds = removeItemIds,
        syncGroups = buildKavitaSeriesSyncGroups(
            series = series,
            recentlyUpdatedSeries = recentlyUpdatedSeries,
            nowEpochMillis = nowEpochMillis,
        ),
        removeSyncGroupIds = existingSyncGroupIds - currentGroupIds,
    )
}

internal fun KavitaFastCatalogDeltaPlan.itemsNeedingProgressPull(): List<ReadableItem> {
    val changedItemIds = changedEntries
        .mapTo(mutableSetOf()) { entry -> entry.item.id }
    return currentItems.filter { item -> item.id !in changedItemIds }
}

internal fun KavitaFastCatalogDeltaPlan.progressEntriesForFastSync(
    remoteProgressEntries: List<KavitaChapterCatalogEntry>,
): List<KavitaChapterCatalogEntry> {
    val entriesByItemId = linkedMapOf<String, KavitaChapterCatalogEntry>()
    changedEntries.forEach { entry -> entriesByItemId[entry.item.id] = entry }
    remoteProgressEntries.forEach { entry -> entriesByItemId[entry.item.id] = entry }
    return currentItems.map { item ->
        entriesByItemId[item.id] ?: item.toFastSyncProgressEntry(
            progress = null,
            progressWasFetched = false,
        )
    }
}

internal fun ReadableItem.toFastSyncProgressEntry(
    progress: ReadingProgress?,
    progressWasFetched: Boolean,
): KavitaChapterCatalogEntry = KavitaChapterCatalogEntry(
    item = this,
    progress = progress,
    isExplicitUnreadReset = progressWasFetched && progress == null,
)

internal fun buildKavitaSeriesSyncGroups(
    series: List<KavitaSeriesEntry>,
    recentlyUpdatedSeries: List<KavitaSeriesUpdateEntry> = emptyList(),
    nowEpochMillis: Long,
): List<RemoteCatalogSyncGroupEntity> {
    val recentUpdateMarkers = recentlyUpdatedSeries.kavitaRecentUpdateMarkers()
    return series.map { entry ->
        val groupId = entry.kavitaCatalogSyncGroupId()
        RemoteCatalogSyncGroupEntity(
            sourceType = SourceType.KAVITA.name,
            groupId = groupId,
            libraryId = SourceType.KAVITA.remoteLibraryId(entry.libraryId),
            fingerprint = entry.kavitaCatalogFingerprint(
                recentUpdateMarker = recentUpdateMarkers.markerFor(
                    entry = entry,
                    groupId = groupId,
                ),
            ),
            updatedAtEpochMillis = nowEpochMillis,
        )
    }
}

internal fun KavitaSeriesEntry.kavitaCatalogSyncGroupId(): String =
    "${SourceType.KAVITA.remoteLibraryId(libraryId)}::$id"

internal fun ReadableItem.kavitaCatalogSyncGroupId(): String? {
    if (sourceType != SourceType.KAVITA) return null
    val seriesId = remoteFileType?.takeIf(String::isNotBlank) ?: return null
    return "$libraryId::$seriesId"
}

private data class KavitaRecentUpdateMarkers(
    val byGroupId: Map<String, String>,
    val bySeriesId: Map<String, String>,
) {
    fun markerFor(
        entry: KavitaSeriesEntry,
        groupId: String,
    ): String? = byGroupId[groupId] ?: bySeriesId[entry.id]
}

private fun List<KavitaSeriesUpdateEntry>.kavitaRecentUpdateMarkers(): KavitaRecentUpdateMarkers {
    val byGroupId = asSequence()
        .mapNotNull { update ->
            val libraryId = update.libraryId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val groupId = "${SourceType.KAVITA.remoteLibraryId(libraryId)}::${update.seriesId}"
            groupId to update.kavitaRecentUpdateMarkerPart()
        }
        .stableMarkerMap()
    val bySeriesId = asSequence()
        .filter { update -> update.libraryId.isNullOrBlank() }
        .map { update -> update.seriesId to update.kavitaRecentUpdateMarkerPart() }
        .stableMarkerMap()
    return KavitaRecentUpdateMarkers(
        byGroupId = byGroupId,
        bySeriesId = bySeriesId,
    )
}

private fun Sequence<Pair<String, String>>.stableMarkerMap(): Map<String, String> = groupBy(
    keySelector = Pair<String, String>::first,
    valueTransform = Pair<String, String>::second,
).mapValues { (_, markers) ->
    markers.distinct()
        .sorted()
        .joinToString(KAVITA_CATALOG_FINGERPRINT_LIST_SEPARATOR)
}

private fun KavitaSeriesUpdateEntry.kavitaRecentUpdateMarkerPart(): String = listOf(
    seriesId,
    libraryId.orEmpty(),
    createdAtEpochMillis?.toString().orEmpty(),
    chapterId.orEmpty(),
    volumeId.orEmpty(),
    count?.toString().orEmpty(),
).joinToString(KAVITA_CATALOG_FINGERPRINT_FIELD_SEPARATOR)

private fun KavitaSeriesEntry.kavitaCatalogFingerprint(
    recentUpdateMarker: String? = null,
): String {
    val baseFingerprint = listOf(
        KAVITA_CATALOG_FINGERPRINT_VERSION,
        id,
        libraryId,
        title,
        format.name,
        pages?.toString().orEmpty(),
        addedAtEpochMillis?.toString().orEmpty(),
        lastChapterAddedAtEpochMillis?.toString().orEmpty(),
        coverUrl?.normalizedKavitaCoverUrlForFingerprint().orEmpty(),
        writers.stableFingerprintPart(),
        pencillers.stableFingerprintPart(),
        publishers.stableFingerprintPart(),
        genres.stableFingerprintPart(),
        tags.stableFingerprintPart(),
        languages.stableFingerprintPart(),
    ).joinToString(KAVITA_CATALOG_FINGERPRINT_FIELD_SEPARATOR)
    val marker = recentUpdateMarker?.takeIf(String::isNotBlank) ?: return baseFingerprint
    return listOf(
        baseFingerprint,
        "recent",
        marker,
    ).joinToString(KAVITA_CATALOG_FINGERPRINT_FIELD_SEPARATOR)
}

private fun List<String>.stableFingerprintPart(): String = asSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .joinToString(KAVITA_CATALOG_FINGERPRINT_LIST_SEPARATOR)

private fun String.normalizedKavitaCoverUrlForFingerprint(): String =
    substringBefore("&apiKey=").substringBefore("?apiKey=")

private const val KAVITA_CATALOG_FINGERPRINT_VERSION = "v1"
private const val KAVITA_CATALOG_FINGERPRINT_FIELD_SEPARATOR = "\u001F"
private const val KAVITA_CATALOG_FINGERPRINT_LIST_SEPARATOR = "\u001E"
