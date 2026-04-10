package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDownloadBatchOutcomeTest {
    @Test
    fun `batch outcome excludes already local files from downloaded count`() {
        val first = testItem(id = "one")
        val second = testItem(id = "two")

        val outcome = evaluateRemoteDownloadBatchOutcome(
            requestedItems = listOf(first, second),
            initiallyDownloadedItemIds = setOf(first.id),
            downloadedItemIdsAfterRequest = setOf(first.id),
        )

        assertEquals(0, outcome.newlyDownloadedCount)
        assertEquals(1, outcome.alreadyAvailableCount)
        assertEquals(1, outcome.failedCount)
        assertTrue(outcome.hasFailures)
        assertEquals(
            "1 file already available locally. 1 file failed to download.",
            outcome.libraryMessage("Ignored"),
        )
    }
}

private fun testItem(
    id: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "library",
    remoteId = id,
    title = id,
    format = FormatType.CBZ,
)
