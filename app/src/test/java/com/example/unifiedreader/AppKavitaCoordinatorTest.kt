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
    fun `execute fast sync skips cover reset when catalog is unchanged`() = runTest {
        val coordinator = AppKavitaCoordinator(
            kavitaCatalogSyncer = FakeKavitaCatalogSyncer(
                result = KavitaCatalogSyncResult(
                    libraryCount = 1,
                    itemCount = 1,
                    supportedFormatCounts = emptyMap(),
                ),
            ),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
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
        var coverResetCount = 0

        val result = coordinator.executeFastSync(
            preparation = preparation,
            clearCoverState = { coverResetCount += 1 },
        )

        assertTrue(result is KavitaSyncExecutionResult.Success)
        assertEquals(0, coverResetCount)
        assertEquals(false, (result as KavitaSyncExecutionResult.Success).hasCatalogChanges)
    }

    @Test
    fun `execute fast sync resets covers when catalog changed`() = runTest {
        val coordinator = AppKavitaCoordinator(
            kavitaCatalogSyncer = FakeKavitaCatalogSyncer(
                result = KavitaCatalogSyncResult(
                    libraryCount = 1,
                    itemCount = 1,
                    supportedFormatCounts = emptyMap(),
                    catalogChangedItemIds = setOf("kavita:item:99"),
                ),
            ),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
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
        var coverResetCount = 0

        val result = coordinator.executeFastSync(
            preparation = preparation,
            clearCoverState = { coverResetCount += 1 },
        )

        assertTrue(result is KavitaSyncExecutionResult.Success)
        assertEquals(1, coverResetCount)
        assertEquals(true, (result as KavitaSyncExecutionResult.Success).hasCatalogChanges)
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
