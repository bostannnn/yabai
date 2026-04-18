package com.example.unifiedreader

import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppGrimmoryCoordinatorTest {
    @Test
    fun `executeConnect persists session and returns sync guidance message`() = runBlocking {
        val credentialStore = TestGrimmoryCredentialStore()
        val syncer = TestGrimmoryCatalogSyncer(
            connectResult = GrimmoryCatalogConnectionResult(
                libraryCount = 3,
                session = com.example.unifiedreader.data.grimmory.GrimmorySession(
                    accessToken = "fresh-access-token",
                    refreshToken = "fresh-refresh-token",
                ),
                activeBaseUrl = "https://backup.grimmory.example",
            ),
        )
        val coordinator = AppGrimmoryCoordinator(
            grimmoryCatalogSyncer = syncer,
            grimmoryCredentialStore = credentialStore,
        )
        val preparation = coordinator.prepareConnect(
            GrimmoryConnectionUiState(
                displayName = "Grimmory",
                baseUrl = "https://primary.grimmory.example",
                backupBaseUrl = "https://backup.grimmory.example",
                username = "reader",
                password = "secret",
            ),
        ) as GrimmorySyncPreparation.Ready

        val result = coordinator.executeConnect(preparation)

        assertTrue(result is GrimmoryConnectExecutionResult.Success)
        val success = result as GrimmoryConnectExecutionResult.Success
        assertEquals(3, success.libraryCount)
        assertEquals(
            "Connected to Grimmory. Tap Sync Grimmory to import the library.",
            success.connection.message,
        )
        assertTrue(success.connection.hasStoredSession)
        assertEquals("", success.connection.password)
        assertEquals("https://backup.grimmory.example", credentialStore.sessionFlow.value?.activeBaseUrl)
        assertEquals(preparation.request, syncer.lastConnectRequest)
    }

    @Test
    fun `execute connect rethrows cancellation`() = runTest {
        val credentialStore = FakeGrimmoryCredentialStore()
        val coordinator = AppGrimmoryCoordinator(
            grimmoryCatalogSyncer = FakeGrimmoryCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            grimmoryCredentialStore = credentialStore,
        )
        val preparation = coordinator.prepareConnect(
            GrimmoryConnectionUiState(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example",
                username = "reader",
                password = "secret",
            ),
        ) as GrimmorySyncPreparation.Ready

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
        val credentialStore = FakeGrimmoryCredentialStore()
        val coordinator = AppGrimmoryCoordinator(
            grimmoryCatalogSyncer = FakeGrimmoryCatalogSyncer(
                failure = CancellationException("cancelled"),
            ),
            grimmoryCredentialStore = credentialStore,
        )
        val preparation = coordinator.prepareConnect(
            GrimmoryConnectionUiState(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example",
                username = "reader",
                password = "secret",
            ),
        ) as GrimmorySyncPreparation.Ready

        try {
            coordinator.executeSync(preparation, clearCoverState = {})
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }

        assertNull(credentialStore.sessionFlow.value)
    }
}

private class TestGrimmoryCatalogSyncer(
    private val connectResult: GrimmoryCatalogConnectionResult,
) : GrimmoryCatalogSyncer {
    var lastConnectRequest: GrimmoryCatalogSyncRequest? = null

    override suspend fun connect(
        request: GrimmoryCatalogSyncRequest,
    ): GrimmoryCatalogConnectionResult {
        lastConnectRequest = request
        return connectResult
    }

    override suspend fun sync(
        request: GrimmoryCatalogSyncRequest,
        onProgress: (GrimmorySyncProgress) -> Unit,
    ): GrimmoryCatalogSyncResult = error("Not used in this test.")
}

private class TestGrimmoryCredentialStore(
    initialSession: GrimmoryStoredSession? = null,
) : GrimmoryCredentialStore {
    private val sessionState = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<GrimmoryStoredSession?> = sessionState

    override suspend fun save(
        session: GrimmoryStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}
