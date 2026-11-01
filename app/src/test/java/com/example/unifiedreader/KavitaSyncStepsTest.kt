package com.example.unifiedreader

import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KavitaSyncStepsTest {
    @Test
    fun `book details fill the bar up to the save`() {
        assertEquals(0f, KavitaSyncSteps.bookDetails(loaded = 0, total = 1_450).fraction)
        assertEquals("Book details · 725 of 1,450 series", KavitaSyncSteps.bookDetails(loaded = 725, total = 1_450).message)
        val allLoaded = checkNotNull(KavitaSyncSteps.bookDetails(loaded = 1_450, total = 1_450).fraction)
        assertTrue(allLoaded < checkNotNull(KavitaSyncSteps.saving(0f).fraction))
    }

    @Test
    fun `steps after the save each move the bar on`() {
        val fills = listOf(
            KavitaSyncSteps.readingProgress(loaded = 3, total = 3),
            KavitaSyncSteps.saving(0.92f),
            KavitaSyncSteps.saving(0.97f),
            KavitaSyncSteps.uploadingProgress,
            KavitaSyncSteps.readingHistory,
            KavitaSyncSteps.uploadingJournal,
        ).map { step -> checkNotNull(step.fraction) }

        assertEquals(fills.sorted().distinct(), fills)
    }

    @Test
    fun `saving the stored session again keeps a failed sync's error`() {
        val failed = KavitaConnectionUiState(
            message = "Unable to sync Kavita: Server error",
            isError = true,
        )

        val restored = failed.withStoredSession(
            KavitaStoredSession(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "renewed-access-token",
                refreshToken = "refresh-token",
                apiKey = "api-key",
            ),
        )

        assertTrue(restored.isError)
        assertEquals("Unable to sync Kavita: Server error", restored.message)
    }
}
