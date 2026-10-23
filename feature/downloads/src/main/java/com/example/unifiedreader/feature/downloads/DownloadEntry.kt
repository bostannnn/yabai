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

    /**
     * The Downloads button: files finished out of the files in this run, where the run is the
     * active entries plus the entries that completed since the oldest of them was queued.
     */
    val buttonState: DownloadsButtonState
        get() {
            val active = activeEntries
            if (active.isEmpty()) return DownloadsButtonState(hasFailed = failedEntries.isNotEmpty())
            val runStartedAt = active.minOf(DownloadEntry::createdAtEpochMillis)
            val run = active + entries.filter { entry ->
                entry.status == DownloadEntryStatus.Completed &&
                    (entry.completedAtEpochMillis ?: entry.updatedAtEpochMillis) >= runStartedAt
            }
            val total = run.sumOf(DownloadEntry::totalItemCount)
            val completed = run.sumOf { entry -> entry.completedItemCount.coerceAtMost(entry.totalItemCount) }
            val running = active.any { entry -> entry.status == DownloadEntryStatus.Running }
            return DownloadsButtonState(
                activeCount = active.size,
                progress = when {
                    total == 0 -> 0f
                    completed == 0 && running -> null
                    else -> completed.toFloat() / total
                },
                hasFailed = failedEntries.isNotEmpty(),
            )
        }

    val activeTrackedItemIds: Set<String>
        get() = activeEntries.flatMapTo(linkedSetOf()) { entry -> entry.trackedItemIds }
}

/**
 * What the Downloads button shows: a ring while [activeCount] entries are queued or running, full
 * to [progress] (null while the first file of the run is still downloading), and a dot when an
 * entry failed and nothing is running.
 */
data class DownloadsButtonState(
    val activeCount: Int = 0,
    val progress: Float? = 0f,
    val hasFailed: Boolean = false,
)
