package com.example.unifiedreader.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProgressTest {

    @Test
    fun `cover progress is hidden when progress has not started`() {
        val progress = ItemProgressSummary(
            label = "Resume from saved location",
            fraction = 0f,
        )

        assertFalse(progress.shouldShowCoverProgress)
    }

    @Test
    fun `cover progress is shown for active reading progress`() {
        val progress = ItemProgressSummary(
            label = "Resume at 35%",
            fraction = 0.35f,
        )

        assertTrue(progress.shouldShowCoverProgress)
    }

    @Test
    fun `cover progress is hidden for completed items`() {
        val progress = ItemProgressSummary(
            label = "Finished",
            fraction = 1f,
        )

        assertFalse(progress.shouldShowCoverProgress)
    }

    @Test
    fun `cover progress uses the same completion threshold as library summaries`() {
        val progress = ItemProgressSummary(
            label = "Nearly done",
            fraction = 0.999f,
        )

        assertFalse(progress.shouldShowCoverProgress)
    }
}
