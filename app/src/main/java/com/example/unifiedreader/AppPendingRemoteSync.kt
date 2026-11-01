package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.core.model.remoteItemSourceType
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
    /**
     * Uploads for books no longer in their server's library: the server removed the book, so no
     * retry delivers them. Kept out of [groups] and the counts.
     */
    val goneItems: List<AppPendingRemoteSyncItem> = emptyList(),
) {
    val queuedCount: Int
        get() = groups.sumOf(AppPendingRemoteSyncSourceGroup::queuedCount)

    val failedCount: Int
        get() = groups.sumOf(AppPendingRemoteSyncSourceGroup::failedCount)

    val totalCount: Int
        get() = groups.sumOf(AppPendingRemoteSyncSourceGroup::totalCount)

    val hasPending: Boolean
        get() = totalCount > 0

    val goneItemIds: Set<String>
        get() = goneItems.mapTo(linkedSetOf(), AppPendingRemoteSyncItem::itemId)
}

/**
 * What waits to upload, by server. An upload whose book is missing from [catalogItems] is gone
 * only when [isCatalogComplete] and the app holds other books from that server: a catalog still
 * loading, or a server signed out or never synced, proves nothing, so those stay waiting.
 */
internal fun buildPendingRemoteSyncSummary(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
    journalSnapshot: JournalSnapshot,
    catalogItems: List<ReadableItem>,
    reachabilityBySource: Map<SourceType, SourceConnectivityState>,
    attemptsBySource: Map<SourceType, AppRemoteSyncAttempt>,
    isCatalogComplete: Boolean = false,
): AppPendingRemoteSyncSummary {
    val catalogItemsById = catalogItems.associateBy(ReadableItem::id)
    val sourcesWithBooks = if (isCatalogComplete) {
        catalogItems.mapTo(mutableSetOf(), ReadableItem::sourceType)
    } else {
        emptySet()
    }
    val items = buildList {
        progressByItemId.values.forEach { progress ->
            val state = progress.syncState.toPendingRemoteSyncItemState() ?: return@forEach
            val catalogItem = catalogItemsById[progress.itemId]
            // Progress names no book of its own: without the book it shows only once it is gone.
            val sourceType = catalogItem?.sourceType
                ?: remoteItemSourceType(progress.itemId)?.takeIf(sourcesWithBooks::contains)
                ?: return@forEach
            if (sourceType == SourceType.LOCAL) return@forEach
            add(
                AppPendingRemoteSyncItem(
                    artifact = RemoteSyncArtifact.PROGRESS,
                    artifactInstanceId = progress.itemId,
                    itemId = progress.itemId,
                    itemTitle = catalogItem?.libraryPrimaryText() ?: journalSnapshot.titleOf(progress.itemId),
                    itemSubtitle = catalogItem?.librarySecondaryText(),
                    sourceType = sourceType,
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
    val (goneItems, waitingItems) = items.partition { item ->
        item.itemId !in catalogItemsById && item.sourceType in sourcesWithBooks
    }

    val groups = waitingItems
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
    return AppPendingRemoteSyncSummary(groups = groups, goneItems = goneItems)
}

/** The title the journal saved for a book, for one the library no longer has. */
private fun JournalSnapshot.titleOf(itemId: String): String =
    entryByItemId[itemId]?.itemTitle
        ?: sessions.firstOrNull { session -> session.itemId == itemId }?.itemTitle
        ?: events.firstOrNull { event -> event.itemId == itemId }?.itemTitle
        ?: UnknownBookTitle

private const val UnknownBookTitle = "Unknown book"

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
    SyncState.SYNCING,
    SyncState.LOCAL_ONLY -> null
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
