package com.example.unifiedreader

import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AppKavitaCoordinatorTest {
    @Test
    fun `execute connect rethrows cancellation`() = runTest {
        val credentialStore = FakeKavitaCredentialStore()
        val coordinator = AppKavitaCoordinator(
            kavitaCatalogSyncer = FakeKavitaCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            kavitaCredentialStore = credentialStore,
        )
        val preparation = coordinator.prepareConnect(
            KavitaConnectionUiState(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                password = "secret",
            ),
        ) as KavitaSyncPreparation.Ready

        try {
            coordinator.executeConnect(preparation)
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        assertNull(credentialStore.sessionFlow.value)
    }

    @Test
    fun `execute sync rethrows cancellation`() = runTest {
        val credentialStore = FakeKavitaCredentialStore()
        val coordinator = AppKavitaCoordinator(
            kavitaCatalogSyncer = FakeKavitaCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            kavitaCredentialStore = credentialStore,
        )
        val preparation = coordinator.prepareConnect(
            KavitaConnectionUiState(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                password = "secret",
            ),
        ) as KavitaSyncPreparation.Ready

        try {
            coordinator.executeSync(preparation, clearCoverState = {})
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        assertNull(credentialStore.sessionFlow.value)
    }
}
