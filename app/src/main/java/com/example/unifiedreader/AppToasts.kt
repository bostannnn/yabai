package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.downloads.DownloadQueueActionResult
import com.example.unifiedreader.feature.downloads.DownloadQueueOutcome
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage

/** The app's toasts: what each action says when it is done. */
internal object AppToasts {
    /** Over the book that just opened, so it's clear the file was copied in and stays. */
    fun imported(item: ReadableItem): AppToastMessage =
        AppToastMessage(text = "Added to ${item.sourceType.displayName()}", icon = AppToastIcon.Added)

    fun unreadableFile(): AppToastMessage = AppToastMessage(
        text = "That file isn't a book yabai can open",
        supportingText = "EPUB, PDF, CBZ or CBR",
        icon = AppToastIcon.Error,
    )

    fun readStatus(markedAsRead: Boolean): AppToastMessage = if (markedAsRead) {
        AppToastMessage(text = "Marked as finished", icon = AppToastIcon.Done)
    } else {
        AppToastMessage(text = "Marked as unread", icon = AppToastIcon.Unread)
    }

    fun removedFromLibrary(item: ReadableItem): AppToastMessage = AppToastMessage(
        text = "Removed ${item.libraryDisplayLabel()} from your library",
        icon = AppToastIcon.Removed,
    )

    /** The file goes when the toast leaves ([onGone]); Undo ([onUndo]) keeps it. */
    fun removedDownload(
        item: ReadableItem,
        onUndo: () -> Unit,
        onGone: () -> Unit,
    ): AppToastMessage = AppToastMessage(
        text = "Removed the download of ${item.libraryDisplayLabel()}",
        icon = AppToastIcon.Removed,
        actionLabel = "Undo",
        onAction = onUndo,
        onDismissed = onGone,
    )

    /** What a download request did; View opens the Downloads sheet while something is downloading. */
    fun download(
        result: DownloadQueueActionResult,
        onView: () -> Unit,
    ): AppToastMessage {
        val inQueue = result.outcome == DownloadQueueOutcome.Queued || result.outcome == DownloadQueueOutcome.AlreadyQueued
        return AppToastMessage(
            text = result.message,
            icon = when (result.outcome) {
                DownloadQueueOutcome.Queued, DownloadQueueOutcome.AlreadyQueued -> AppToastIcon.Download
                DownloadQueueOutcome.AlreadyAvailable -> AppToastIcon.Done
                DownloadQueueOutcome.MixedSources, DownloadQueueOutcome.NothingSelected -> AppToastIcon.Error
            },
            actionLabel = if (inQueue) "View" else null,
            onAction = if (inQueue) onView else null,
        )
    }

    fun syncFailure(
        notice: AppProgressSyncNotice,
        onRetry: () -> Unit,
    ): AppToastMessage = AppToastMessage(
        text = notice.message,
        supportingText = notice.reason,
        icon = AppToastIcon.Error,
        actionLabel = if (notice.canRetry) "Retry" else null,
        onAction = if (notice.canRetry) onRetry else null,
    )
}
