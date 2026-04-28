package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSeriesUpdateEntry

internal data class KavitaCatalogFetchPlan(
    val entries: List<KavitaChapterCatalogEntry>,
    val syncGroups: List<RemoteCatalogSyncGroupEntity>,
)

internal suspend fun KavitaCatalogSyncService.fetchKavitaCatalog(
    api: KavitaApi,
    accessToken: String,
    libraries: List<Library>,
    series: List<KavitaSeriesEntry>,
    remoteCatalogWriter: RemoteCatalogWriter,
    forceFullRefresh: Boolean,
    nowEpochMillis: Long,
    onProgress: (KavitaSyncProgress) -> Unit,
): KavitaCatalogFetchPlan {
    val updateMarkers = if (forceFullRefresh) {
        emptyList()
    } else {
        runCatching {
            api.getRecentlyUpdatedSeries(accessToken)
        }.getOrElse {
            return fullKavitaCatalogFetch(
                api = api,
                accessToken = accessToken,
                series = series,
                nowEpochMillis = nowEpochMillis,
                onProgress = onProgress,
            )
        }
    }
    val updatesBySeriesId = updateMarkers.groupBy(KavitaSeriesUpdateEntry::seriesId)
    val syncGroups = series.toKavitaSyncGroups(
        updatesBySeriesId = updatesBySeriesId,
        nowEpochMillis = nowEpochMillis,
    )
    if (forceFullRefresh) {
        return fullKavitaCatalogFetch(
            api = api,
            accessToken = accessToken,
            series = series,
            syncGroups = syncGroups,
            onProgress = onProgress,
        )
    }

    val existingGroupsById = remoteCatalogWriter.getCatalogSyncGroups(SourceType.KAVITA)
        .associateBy(RemoteCatalogSyncGroup::groupId)
    val libraryIds = libraries.mapTo(mutableSetOf(), Library::id)
    val existingItemsBySeriesId = remoteCatalogWriter.getItemsForSourceType(SourceType.KAVITA)
        .filter { item -> item.libraryId in libraryIds }
        .groupBy { item -> item.remoteFileType.orEmpty() }
    val seriesToRefresh = series.filter { seriesEntry ->
        val nextFingerprint = updatesBySeriesId[seriesEntry.id].orEmpty().let(seriesEntry::catalogFingerprint)
        existingGroupsById[seriesEntry.id]?.fingerprint != nextFingerprint ||
            existingItemsBySeriesId[seriesEntry.id].isNullOrEmpty()
    }
    val refreshedEntries = fetchSeriesEntries(
        api = api,
        accessToken = accessToken,
        series = seriesToRefresh,
        onProgress = onProgress,
    )
    val refreshedSeriesIds = seriesToRefresh.mapTo(mutableSetOf(), KavitaSeriesEntry::id)
    val preservedEntries = series
        .asSequence()
        .filterNot { seriesEntry -> seriesEntry.id in refreshedSeriesIds }
        .flatMap { seriesEntry ->
            existingItemsBySeriesId[seriesEntry.id]
                .orEmpty()
                .asSequence()
                .map { item -> preservedKavitaEntry(item, seriesEntry) }
        }
        .toList()
    return KavitaCatalogFetchPlan(
        entries = refreshedEntries + preservedEntries,
        syncGroups = syncGroups,
    )
}

private suspend fun KavitaCatalogSyncService.fullKavitaCatalogFetch(
    api: KavitaApi,
    accessToken: String,
    series: List<KavitaSeriesEntry>,
    nowEpochMillis: Long,
    onProgress: (KavitaSyncProgress) -> Unit,
): KavitaCatalogFetchPlan {
    val syncGroups = series.toKavitaSyncGroups(
        updatesBySeriesId = emptyMap(),
        nowEpochMillis = nowEpochMillis,
    )
    return fullKavitaCatalogFetch(
        api = api,
        accessToken = accessToken,
        series = series,
        syncGroups = syncGroups,
        onProgress = onProgress,
    )
}

private suspend fun KavitaCatalogSyncService.fullKavitaCatalogFetch(
    api: KavitaApi,
    accessToken: String,
    series: List<KavitaSeriesEntry>,
    syncGroups: List<RemoteCatalogSyncGroupEntity>,
    onProgress: (KavitaSyncProgress) -> Unit,
): KavitaCatalogFetchPlan = KavitaCatalogFetchPlan(
    entries = fetchSeriesEntries(
        api = api,
        accessToken = accessToken,
        series = series,
        onProgress = onProgress,
    ),
    syncGroups = syncGroups,
)

private fun preservedKavitaEntry(
    item: ReadableItem,
    series: KavitaSeriesEntry,
): KavitaChapterCatalogEntry = KavitaChapterCatalogEntry(
    item = item.copy(
        addedAtEpochMillis = series.addedAtEpochMillis ?: item.addedAtEpochMillis,
    ),
    progress = null,
    isExplicitUnreadReset = false,
)

private fun List<KavitaSeriesEntry>.toKavitaSyncGroups(
    updatesBySeriesId: Map<String, List<KavitaSeriesUpdateEntry>>,
    nowEpochMillis: Long,
): List<RemoteCatalogSyncGroupEntity> = map { seriesEntry ->
    RemoteCatalogSyncGroupEntity(
        sourceType = SourceType.KAVITA.name,
        groupId = seriesEntry.id,
        libraryId = SourceType.KAVITA.remoteLibraryId(seriesEntry.libraryId),
        fingerprint = seriesEntry.catalogFingerprint(updatesBySeriesId[seriesEntry.id].orEmpty()),
        updatedAtEpochMillis = nowEpochMillis,
    )
}

private fun KavitaSeriesEntry.catalogFingerprint(
    updates: List<KavitaSeriesUpdateEntry>,
): String = listOf(
    id,
    libraryId,
    title,
    format.name,
    pages?.toString().orEmpty(),
    addedAtEpochMillis?.toString().orEmpty(),
    lastChapterAddedAtEpochMillis?.toString().orEmpty(),
    coverUrl.orEmpty(),
    publishers.normalizedFingerprintPart(),
    genres.normalizedFingerprintPart(),
    tags.normalizedFingerprintPart(),
    languages.normalizedFingerprintPart(),
    updates.normalizedUpdateFingerprintPart(),
).joinToString(separator = "\u001F")

private fun List<String>.normalizedFingerprintPart(): String =
    map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .joinToString(separator = "\u001E")

private fun List<KavitaSeriesUpdateEntry>.normalizedUpdateFingerprintPart(): String =
    sortedWith(
        compareBy<KavitaSeriesUpdateEntry>(
            { update -> update.createdAtEpochMillis ?: Long.MIN_VALUE },
            { update -> update.chapterId.orEmpty() },
            { update -> update.volumeId.orEmpty() },
        ),
    ).joinToString(separator = "\u001E") { update ->
        listOf(
            update.libraryId.orEmpty(),
            update.createdAtEpochMillis?.toString().orEmpty(),
            update.chapterId.orEmpty(),
            update.volumeId.orEmpty(),
            update.count?.toString().orEmpty(),
        ).joinToString(separator = "\u001D")
    }
