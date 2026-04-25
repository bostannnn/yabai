package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppKavitaCoordinatorTest {
    @Test
    fun `execute sync non-network failure does not mark source unavailable`() = runTest {
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy()
        val coordinator = AppKavitaCoordinator(
            kavitaCatalogSyncer = FakeKavitaCatalogSyncer(
                failure = IllegalStateException("Sync rejected"),
            ),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val preparation = coordinator.prepareConnect(
            KavitaConnectionUiState(
                displayName = "Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                password = "secret",
            ),
        ) as KavitaSyncPreparation.Ready

        val result = coordinator.executeSync(preparation, clearCoverState = {})

        assertTrue(result is KavitaSyncExecutionResult.Failure)
        assertEquals(RemoteSourceReachability.Reachable, remoteSourceConnectivityPolicy.reachabilityFor(SourceType.KAVITA))
    }

    @Test
    fun `execute connect rethrows cancellation`() = runTest {
        val credentialStore = FakeKavitaCredentialStore()
        val coordinator = AppKavitaCoordinator(
            kavitaCatalogSyncer = FakeKavitaCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            kavitaCredentialStore = credentialStore,
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
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
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
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
