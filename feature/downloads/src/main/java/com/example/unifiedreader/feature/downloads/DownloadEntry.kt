package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.SourceType

data class QueuedDownloadItem(
    val id: String,
    val sourceType: SourceType,
    val libraryId: String,
    val remoteId: String?,
    val remoteFileId: String?,
    val remoteFileType: String?,
    val title: String,
    val formatName: String,
    val pageCount: Int?,
    val coverUrl: String? = null,
    val comicSeries: String? = null,
    val comicIssueTitle: String? = null,
    val comicVolume: String? = null,
    val comicIssueNumber: String? = null,
)

enum class DownloadEntryStatus {
    Queued,
    PausedConnectivity,
    Running,
    Canceling,
    Completed,
    Failed,
    Canceled,
}

data class DownloadEntry(
    val id: String,
    val selectedItemId: String,
    val selectedItemTitle: String,
    val sourceType: SourceType,
    val actionItems: List<QueuedDownloadItem>,
    val trackedItemIds: Set<String>,
    val completedItemIds: Set<String>,
    val status: DownloadEntryStatus,
    val currentItemId: String? = null,
    val currentItemTitle: String? = null,
    val errorMessage: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
) {
    val actionItemIds: List<String>
        get() = actionItems.map(QueuedDownloadItem::id)

    val totalItemCount: Int
        get() = actionItems.size

    val completedItemCount: Int
        get() = completedItemIds.size

    val progressLabel: String
        get() = when {
            totalItemCount <= 1 && status == DownloadEntryStatus.Completed -> "Ready"
            totalItemCount <= 1 && status == DownloadEntryStatus.Running -> "Downloading"
            totalItemCount <= 1 && status == DownloadEntryStatus.PausedConnectivity -> "Waiting for server"
            totalItemCount <= 1 && status == DownloadEntryStatus.Canceling -> "Canceling"
            else -> "${completedItemCount.coerceAtMost(totalItemCount)}/$totalItemCount files"
        }

    val canRetry: Boolean
        get() = status == DownloadEntryStatus.Failed || status == DownloadEntryStatus.Canceled

    val canCancel: Boolean
        get() = status == DownloadEntryStatus.Queued ||
            status == DownloadEntryStatus.PausedConnectivity ||
            status == DownloadEntryStatus.Running

    val canRemove: Boolean
        get() = status == DownloadEntryStatus.Failed || status == DownloadEntryStatus.Canceled

    val canOpen: Boolean
        get() = status == DownloadEntryStatus.Completed
}

data class DownloadEntriesSnapshot(
    val entries: List<DownloadEntry>,
) {
    val activeEntries: List<DownloadEntry>
        get() = entries.filter { entry ->
            entry.status == DownloadEntryStatus.Queued ||
                entry.status == DownloadEntryStatus.PausedConnectivity ||
                entry.status == DownloadEntryStatus.Running ||
                entry.status == DownloadEntryStatus.Canceling
        }

    val failedEntries: List<DownloadEntry>
        get() = entries.filter { entry -> entry.status == DownloadEntryStatus.Failed }

    val completedEntries: List<DownloadEntry>
        get() = entries.filter { entry ->
            entry.status == DownloadEntryStatus.Completed || entry.status == DownloadEntryStatus.Canceled
        }

    val badgeCount: Int
        get() = activeEntries.size + failedEntries.size

    val activeTrackedItemIds: Set<String>
        get() = activeEntries.flatMapTo(linkedSetOf()) { entry -> entry.trackedItemIds }
}
