package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.downloads.DownloadQueueActionResult
import com.example.unifiedreader.feature.downloads.DownloadQueueOutcome
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.AppToastState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppToastsTest {
    @Test
    fun `a queued or running download offers View, the rest do not`() {
        var viewed = 0
        val queued = AppToasts.download(DownloadQueueActionResult("Downloading 3 files"), onView = { viewed++ })
        val running = AppToasts.download(
            DownloadQueueActionResult("Star Courier #2 is already downloading", isError = true, outcome = DownloadQueueOutcome.AlreadyQueued),
            onView = { viewed++ },
        )
        val onDevice = AppToasts.download(
            DownloadQueueActionResult("Star Courier #2 is already on this device", outcome = DownloadQueueOutcome.AlreadyAvailable),
            onView = { viewed++ },
        )
        val mixed = AppToasts.download(
            DownloadQueueActionResult("Pick books from one source to download them together", isError = true, outcome = DownloadQueueOutcome.MixedSources),
            onView = { viewed++ },
        )

        assertEquals("View", queued.actionLabel)
        assertEquals(AppToastIcon.Download, running.icon)
        assertEquals("View", running.actionLabel)
        assertNull(onDevice.actionLabel)
        assertEquals(AppToastIcon.Done, onDevice.icon)
        assertEquals(AppToastIcon.Error, mixed.icon)
        assertNull(mixed.actionLabel)
        queued.onAction?.invoke()
        assertEquals(1, viewed)
    }

    @Test
    fun `a sync failure offers Retry unless the source can't be reached`() {
        val failed = AppToasts.syncFailure(
            AppProgressSyncNotice(id = 1, message = "Couldn't send your progress to Kavita", reason = "Progress rejected"),
            onRetry = {},
        )
        val unreachable = AppToasts.syncFailure(
            AppProgressSyncNotice(id = 2, message = "Kavita can't be reached", canRetry = false),
            onRetry = {},
        )

        assertEquals("Retry", failed.actionLabel)
        assertEquals("Progress rejected", failed.supportingText)
        assertTrue(failed.isError)
        assertNull(unreachable.actionLabel)
    }

    @Test
    fun `an import names the source it went to`() {
        assertEquals("Added to Local", AppToasts.imported(localBook()).text)
        assertTrue(AppToasts.unreadableFile().isError)
    }

    @Test
    fun `a removed download is deleted when its toast leaves, and Undo keeps it`() {
        val toasts = AppToastState()
        var deleted = 0
        var undone = 0
        toasts.show(AppToasts.removedDownload(localBook(), onUndo = { undone++ }, onGone = { deleted++ }))

        // The next toast replaces it: it has left without Undo, so the file goes.
        toasts.show(AppToastMessage(text = "Marked as finished"))

        assertEquals(1, deleted)
        assertEquals(0, undone)
    }

    private fun localBook(): ReadableItem = ReadableItem(
        id = "local:item:1",
        sourceType = SourceType.LOCAL,
        libraryId = "local:library:1",
        remoteId = null,
        title = "Glass Meridian",
        format = FormatType.EPUB,
        kind = ReadableItemKind.BOOK,
        pageCount = 73,
    )
}
