package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalSnapshot

enum class AppPendingRemoteSyncItemState {
    QUEUED,
    FAILED,
}

data class AppRemoteSyncAttempt(
    val attemptedAtEpochMillis: Long,
    val errorMessage: String? = null,
    val artifact: RemoteSyncArtifact? = null,
)

data class AppPendingRemoteSyncItem(
    val artifact: RemoteSyncArtifact,
    val artifactInstanceId: String,
    val itemId: String,
    val itemTitle: String,
    val itemSubtitle: String? = null,
    val sourceType: SourceType,
    val state: AppPendingRemoteSyncItemState,
    val updatedAtEpochMillis: Long?,
) {
    val sortKey: String = listOf(
        sourceType.name,
        state.name,
        artifact.name,
        itemTitle,
        itemId,
        artifactInstanceId,
        (updatedAtEpochMillis ?: 0L).toString(),
    ).joinToString(":")
}

data class AppPendingRemoteSyncSourceGroup(
    val sourceType: SourceType,
    val connectivityState: SourceConnectivityState,
    val items: List<AppPendingRemoteSyncItem>,
    val lastAttempt: AppRemoteSyncAttempt? = null,
) {
    val queuedCount: Int
        get() = items.count { item -> item.state == AppPendingRemoteSyncItemState.QUEUED }

    val failedCount: Int
        get() = items.count { item -> item.state == AppPendingRemoteSyncItemState.FAILED }

    val totalCount: Int
        get() = items.size
}

data class AppPendingRemoteSyncSummary(
    val groups: List<AppPendingRemoteSyncSourceGroup> = emptyList(),
) {
    val queuedCount: Int
        get() = groups.sumOf(AppPendingRemoteSyncSourceGroup::queuedCount)

    val failedCount: Int
        get() = groups.sumOf(AppPendingRemoteSyncSourceGroup::failedCount)

    val totalCount: Int
        get() = groups.sumOf(AppPendingRemoteSyncSourceGroup::totalCount)

    val hasPending: Boolean
        get() = totalCount > 0
}

internal fun buildPendingRemoteSyncSummary(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
    journalSnapshot: JournalSnapshot,
    catalogItems: List<ReadableItem>,
    reachabilityBySource: Map<SourceType, SourceConnectivityState>,
    attemptsBySource: Map<SourceType, AppRemoteSyncAttempt>,
): AppPendingRemoteSyncSummary {
    val catalogItemsById = catalogItems.associateBy(ReadableItem::id)
    val items = buildList {
        progressByItemId.values.forEach { progress ->
            val catalogItem = catalogItemsById[progress.itemId] ?: return@forEach
            val state = progress.syncState.toPendingRemoteSyncItemState() ?: return@forEach
            if (catalogItem.sourceType == SourceType.LOCAL) return@forEach
            add(
                AppPendingRemoteSyncItem(
                    artifact = RemoteSyncArtifact.PROGRESS,
                    artifactInstanceId = progress.itemId,
                    itemId = progress.itemId,
                    itemTitle = catalogItem.libraryPrimaryText(),
                    itemSubtitle = catalogItem.librarySecondaryText(),
                    sourceType = catalogItem.sourceType,
                    state = state,
                    updatedAtEpochMillis = progress.updatedAtEpochMillis,
                ),
            )
        }
        journalSnapshot.entries.forEach { entry ->
            entry.toPendingRemoteSyncItem(catalogItemsById)?.let(::add)
        }
        journalSnapshot.sessions.forEach { session ->
            session.toPendingRemoteSyncItem(catalogItemsById)?.let(::add)
        }
    }
        .distinctBy { item ->
            listOf(
                item.sourceType.name,
                item.artifact.name,
                item.artifactInstanceId,
                item.updatedAtEpochMillis?.toString().orEmpty(),
                item.state.name,
            ).joinToString(":")
        }
        .sortedBy(AppPendingRemoteSyncItem::sortKey)

    val groups = items
        .groupBy(AppPendingRemoteSyncItem::sourceType)
        .toSortedMap(compareBy(SourceType::ordinal))
        .map { (sourceType, sourceItems) ->
            AppPendingRemoteSyncSourceGroup(
                sourceType = sourceType,
                connectivityState = reachabilityBySource[sourceType] ?: SourceConnectivityState.Unknown,
                items = sourceItems,
                lastAttempt = attemptsBySource[sourceType],
            )
        }
    return AppPendingRemoteSyncSummary(groups = groups)
}

internal fun AppPendingRemoteSyncSummary.withAttempts(
    attemptsBySource: Map<SourceType, AppRemoteSyncAttempt>,
): AppPendingRemoteSyncSummary = copy(
    groups = groups.map { group ->
        group.copy(lastAttempt = attemptsBySource[group.sourceType])
    },
)

internal fun ReaderProgressSnapshot.isPendingRemoteSync(): Boolean =
    syncState.toPendingRemoteSyncItemState() != null

internal fun SyncState.toPendingRemoteSyncItemState(): AppPendingRemoteSyncItemState? = when (this) {
    SyncState.DIRTY -> AppPendingRemoteSyncItemState.QUEUED
    SyncState.FAILED -> AppPendingRemoteSyncItemState.FAILED
    SyncState.CLEAN,
    SyncState.SYNCING -> null
}

private fun ItemJournalEntry.toPendingRemoteSyncItem(
    catalogItemsById: Map<String, ReadableItem>,
): AppPendingRemoteSyncItem? {
    val state = syncState.toPendingRemoteSyncItemState() ?: return null
    if (sourceType == SourceType.LOCAL) return null
    val catalogItem = catalogItemsById[itemId]
    return AppPendingRemoteSyncItem(
        artifact = RemoteSyncArtifact.JOURNAL_ENTRY,
        artifactInstanceId = itemId,
        itemId = itemId,
        itemTitle = catalogItem?.libraryPrimaryText() ?: itemTitle,
        itemSubtitle = catalogItem?.librarySecondaryText(),
        sourceType = sourceType,
        state = state,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun JournalReadingSession.toPendingRemoteSyncItem(
    catalogItemsById: Map<String, ReadableItem>,
): AppPendingRemoteSyncItem? {
    val state = syncState.toPendingRemoteSyncItemState() ?: return null
    if (sourceType == SourceType.LOCAL) return null
    val catalogItem = catalogItemsById[itemId]
    return AppPendingRemoteSyncItem(
        artifact = RemoteSyncArtifact.READING_SESSION,
        artifactInstanceId = id,
        itemId = itemId,
        itemTitle = catalogItem?.libraryPrimaryText() ?: itemTitle,
        itemSubtitle = catalogItem?.librarySecondaryText(),
        sourceType = sourceType,
        state = state,
        updatedAtEpochMillis = endedAtEpochMillis,
    )
}
