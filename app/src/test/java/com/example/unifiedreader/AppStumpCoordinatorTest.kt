package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppStumpCoordinatorTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute sync non-network failure does not mark source unavailable`() = runTest {
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy()
        val coordinator = AppStumpCoordinator(
            stumpCatalogSyncer = FakeStumpCatalogSyncer(
                failure = IllegalStateException("Sync rejected"),
            ),
            stumpCredentialStore = FakeStumpCredentialStore(),
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
        )
        val preparation = coordinator.prepareConnect(
            StumpConnectionUiState(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                password = "secret",
            ),
        ) as StumpSyncPreparation.Ready

        val result = coordinator.executeSync(preparation, clearCoverState = {})

        // Drain any work scheduled on the test dispatcher so the connectivity write inside
        // executeSync has settled before we read it back, keeping the assertion deterministic
        // under a full suite run instead of racing ambient scheduling.
        advanceUntilIdle()

        assertTrue(result is StumpSyncExecutionResult.Failure)
        assertEquals(RemoteSourceReachability.Reachable, remoteSourceConnectivityPolicy.reachabilityFor(SourceType.STUMP))
    }

    @Test
    fun `execute connect rethrows cancellation`() = runTest {
        val credentialStore = FakeStumpCredentialStore()
        val coordinator = AppStumpCoordinator(
            stumpCatalogSyncer = FakeStumpCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            stumpCredentialStore = credentialStore,
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
        )
        val preparation = coordinator.prepareConnect(
            StumpConnectionUiState(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                password = "secret",
            ),
        ) as StumpSyncPreparation.Ready

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
        val credentialStore = FakeStumpCredentialStore()
        val coordinator = AppStumpCoordinator(
            stumpCatalogSyncer = FakeStumpCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            stumpCredentialStore = credentialStore,
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
        )
        val preparation = coordinator.prepareConnect(
            StumpConnectionUiState(
                displayName = "Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                password = "secret",
            ),
        ) as StumpSyncPreparation.Ready

        try {
            coordinator.executeSync(preparation, clearCoverState = {})
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        assertNull(credentialStore.sessionFlow.value)
    }
}
