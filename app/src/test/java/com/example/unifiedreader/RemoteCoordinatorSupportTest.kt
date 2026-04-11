package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCoordinatorSupportTest {
    @Test
    fun `refreshed remote connection state matches normalized stored session`() {
        val connection = TestConnection(
            displayName = "Server",
            baseUrl = "https://example.com/",
            username = " reader ",
        )
        val refreshed = refreshedRemoteConnectionState(
            connection = connection,
            baseUrl = connection.baseUrl,
            username = connection.username,
            copyHasStoredSession = { currentConnection, hasStoredSession ->
                currentConnection.copy(hasStoredSession = hasStoredSession)
            },
            matchingStoredSession = { baseUrl, username ->
                TestStoredSession(
                    displayName = "Saved",
                    baseUrl = baseUrl,
                    username = username,
                    accessToken = "access-token",
                    apiKey = "api-key",
                )
            },
        )

        assertTrue(refreshed.hasStoredSession)
    }

    @Test
    fun `refreshed remote connection state clears stored flag for invalid input`() {
        val refreshed = refreshedRemoteConnectionState<TestConnection, TestStoredSession>(
            connection = TestConnection(
                displayName = "Server",
                baseUrl = "example.com",
                username = "",
                hasStoredSession = true,
            ),
            baseUrl = "example.com",
            username = "",
            copyHasStoredSession = { currentConnection, hasStoredSession ->
                currentConnection.copy(hasStoredSession = hasStoredSession)
            },
            matchingStoredSession = { _, _ -> null },
        )

        assertFalse(refreshed.hasStoredSession)
    }

    @Test
    fun `refreshed remote connection state matches stored session when address sets overlap`() {
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = "https://backup.example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val refreshed = refreshedRemoteConnectionState(
            connection = TestConnection(
                displayName = "Server",
                baseUrl = "https://backup.example.com",
                username = " reader ",
            ),
            baseUrl = "https://backup.example.com",
            backupBaseUrl = "https://tailscale.example.com",
            username = " reader ",
            copyHasStoredSession = { currentConnection, hasStoredSession ->
                currentConnection.copy(hasStoredSession = hasStoredSession)
            },
            matchingStoredSession = { baseUrl, backupBaseUrl, username ->
                savedSession.takeIf {
                    it.matchesServerIdentity(
                        baseUrl = baseUrl,
                        backupBaseUrl = backupBaseUrl,
                        username = username,
                    )
                }
            },
        )

        assertTrue(refreshed.hasStoredSession)
    }

    @Test
    fun `refreshed remote connection state ignores overlap through active address only`() {
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = "https://tailscale-old.example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val refreshed = refreshedRemoteConnectionState(
            connection = TestConnection(
                displayName = "Server",
                baseUrl = "https://tailscale-old.example.com",
                username = " reader ",
            ),
            baseUrl = "https://tailscale-old.example.com",
            backupBaseUrl = "https://tailscale-new.example.com",
            username = " reader ",
            copyHasStoredSession = { currentConnection, hasStoredSession ->
                currentConnection.copy(hasStoredSession = hasStoredSession)
            },
            matchingStoredSession = { baseUrl, backupBaseUrl, username ->
                savedSession.takeIf {
                    it.matchesServerIdentity(
                        baseUrl = baseUrl,
                        backupBaseUrl = backupBaseUrl,
                        username = username,
                    )
                }
            },
        )

        assertFalse(refreshed.hasStoredSession)
    }

    @Test
    fun `prepare remote connect input reuses saved session when password is blank`() {
        val connection = TestConnection(
            displayName = " Home Server ",
            baseUrl = "https://example.com/",
            username = " reader ",
        )
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            username = connection.username,
            password = "",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { _, _ -> savedSession },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
        )

        val ready = result as RemoteConnectionInputPreparation.Ready
        assertEquals("Home Server", ready.input.displayName)
        assertEquals("https://example.com", ready.input.baseUrl)
        assertEquals("reader", ready.input.username)
        assertNull(ready.input.password)
        assertEquals(savedSession, ready.input.savedSession)
    }

    @Test
    fun `prepare remote connect input reuses saved session when primary and backup are swapped`() {
        val connection = TestConnection(
            displayName = " Home Server ",
            baseUrl = "https://backup.example.com/",
            username = " reader ",
        )
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://backup.example.com",
            activeBaseUrl = "https://backup.example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            backupBaseUrl = "https://primary.example.com/",
            username = connection.username,
            password = "",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { baseUrl, backupBaseUrl, username ->
                savedSession.takeIf {
                    it.matchesServerIdentity(
                        baseUrl = baseUrl,
                        backupBaseUrl = backupBaseUrl,
                        username = username,
                    )
                }
            },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
        )

        val ready = result as RemoteConnectionInputPreparation.Ready
        assertEquals("https://backup.example.com", ready.input.baseUrl)
        assertEquals("https://primary.example.com", ready.input.backupBaseUrl)
        assertEquals("https://backup.example.com", ready.input.activeBaseUrl)
        assertEquals(savedSession, ready.input.savedSession)
        assertNull(ready.input.password)
    }

    @Test
    fun `prepare remote connect input drops saved active address when it is no longer configured`() {
        val connection = TestConnection(
            displayName = " Home Server ",
            baseUrl = "https://primary.example.com/",
            username = " reader ",
        )
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://primary.example.com",
            backupBaseUrl = "https://tailscale-old.example.com",
            activeBaseUrl = "https://tailscale-old.example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            backupBaseUrl = "https://tailscale-new.example.com/",
            username = connection.username,
            password = "",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { baseUrl, backupBaseUrl, username ->
                savedSession.takeIf {
                    it.matchesServerIdentity(
                        baseUrl = baseUrl,
                        backupBaseUrl = backupBaseUrl,
                        username = username,
                    )
                }
            },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
        )

        val ready = result as RemoteConnectionInputPreparation.Ready
        assertEquals("https://primary.example.com", ready.input.baseUrl)
        assertEquals("https://tailscale-new.example.com", ready.input.backupBaseUrl)
        assertNull(ready.input.activeBaseUrl)
        assertEquals(savedSession, ready.input.savedSession)
    }

    @Test
    fun `prepare remote connect input allows http with cleartext warning`() {
        val connection = TestConnection(
            displayName = " Home Server ",
            baseUrl = "http://192.168.1.20:8080/",
            username = " reader ",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            username = connection.username,
            password = "secret",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { _, _ -> null },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
        )

        val ready = result as RemoteConnectionInputPreparation.Ready
        assertEquals("http://192.168.1.20:8080", ready.input.baseUrl)
        assertEquals(HTTP_WARNING_MESSAGE, ready.input.cleartextWarningMessage)
    }

    @Test
    fun `prepare remote connect input allows public http`() {
        val connection = TestConnection(
            displayName = "Public Server",
            baseUrl = "http://example.com",
            username = "reader",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            username = connection.username,
            password = "secret",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { _, _ -> null },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
        )

        val ready = result as RemoteConnectionInputPreparation.Ready
        assertEquals("http://example.com", ready.input.baseUrl)
        assertEquals(HTTP_WARNING_MESSAGE, ready.input.cleartextWarningMessage)
    }

    @Test
    fun `prepare remote connect input warns when backup url uses http`() {
        val connection = TestConnection(
            displayName = "Public Server",
            baseUrl = "https://example.com",
            username = "reader",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            backupBaseUrl = "http://backup.example.com",
            username = connection.username,
            password = "secret",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { _, _, _ -> null },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
        )

        val ready = result as RemoteConnectionInputPreparation.Ready
        assertEquals("https://example.com", ready.input.baseUrl)
        assertEquals("http://backup.example.com", ready.input.backupBaseUrl)
        assertEquals(HTTP_WARNING_MESSAGE, ready.input.cleartextWarningMessage)
    }

    @Test
    fun `connection success message includes cleartext warning`() {
        assertEquals(
            "Connected. $HTTP_WARNING_MESSAGE",
            connectionSuccessMessage(
                message = "Connected.",
                cleartextWarningMessage = HTTP_WARNING_MESSAGE,
            ),
        )
    }

    @Test
    fun `prepare remote connect input surfaces saved session validation failure`() {
        val connection = TestConnection(
            displayName = "Server",
            baseUrl = "https://example.com",
            username = "reader",
        )

        val result = prepareRemoteConnectInput(
            connection = connection,
            displayName = connection.displayName,
            baseUrl = connection.baseUrl,
            username = connection.username,
            password = "",
            invalidConnection = { message ->
                connection.copy(message = message, isError = true)
            },
            matchingStoredSession = { _, _ ->
                TestStoredSession(
                    displayName = "Saved",
                    baseUrl = "https://example.com",
                    username = "reader",
                    accessToken = "saved-access-token",
                    apiKey = null,
                )
            },
            missingRequiredFieldsMessage = "Missing required fields.",
            missingCredentialMessage = "Missing credentials.",
            validateSavedSession = { savedSession, password ->
                if (password == null && savedSession?.apiKey.isNullOrBlank()) {
                    "Saved session needs refresh."
                } else {
                    null
                }
            },
        )

        val invalid = result as RemoteConnectionInputPreparation.Invalid
        assertEquals("Saved session needs refresh.", invalid.connection.message)
        assertTrue(invalid.connection.isError)
    }

    @Test
    fun `prepare remote refresh input accepts saved public http session`() {
        val connection = TestConnection()
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = null,
        )

        val result = prepareRemoteRefreshInput(
            connection = connection,
            storedSession = savedSession,
            restoreConnection = { currentConnection, session ->
                currentConnection.copy(
                    displayName = session.displayName,
                    baseUrl = session.baseUrl,
                    username = session.username,
                    hasStoredSession = true,
                )
            },
            refreshedConnectionState = { restoredConnection ->
                restoredConnection.copy(message = "Refreshed")
            },
            invalidConnection = { currentConnection, message ->
                currentConnection.copy(message = message, isError = true)
            },
            validateStoredSession = { session ->
                if (session.apiKey.isNullOrBlank()) {
                    "Saved session needs refresh."
                } else {
                    null
                }
            },
        )

        val invalid = result as StoredSessionRefreshInputPreparation.Invalid
        assertEquals("Saved", invalid.connection.displayName)
        assertEquals("https://example.com", invalid.connection.baseUrl)
        assertEquals("reader", invalid.connection.username)
        assertTrue(invalid.connection.hasStoredSession)
        assertEquals("Saved session needs refresh.", invalid.connection.message)
    }

    @Test
    fun `prepare remote refresh input warns for stored public http session`() {
        val connection = TestConnection()
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "http://example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val result = prepareRemoteRefreshInput(
            connection = connection,
            storedSession = savedSession,
            restoreConnection = { currentConnection, session ->
                currentConnection.copy(
                    displayName = session.displayName,
                    baseUrl = session.baseUrl,
                    username = session.username,
                    hasStoredSession = true,
                )
            },
            refreshedConnectionState = { restoredConnection ->
                restoredConnection.copy(message = "Refreshed")
            },
            invalidConnection = { currentConnection, message ->
                currentConnection.copy(message = message, isError = true)
            },
        )

        val ready = result as StoredSessionRefreshInputPreparation.Ready
        assertEquals("Saved", ready.connection.displayName)
        assertEquals("http://example.com", ready.connection.baseUrl)
        assertEquals("reader", ready.connection.username)
        assertTrue(ready.connection.hasStoredSession)
        assertEquals("Refreshed", ready.connection.message)
        assertEquals(savedSession, ready.session)
        assertEquals(HTTP_WARNING_MESSAGE, ready.cleartextWarningMessage)
    }

    @Test
    fun `prepare remote refresh input warns when stored backup url uses http`() {
        val connection = TestConnection()
        val savedSession = TestStoredSession(
            displayName = "Saved",
            baseUrl = "https://example.com",
            backupBaseUrl = "http://backup.example.com",
            username = "reader",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        val result = prepareRemoteRefreshInput(
            connection = connection,
            storedSession = savedSession,
            restoreConnection = { currentConnection, session ->
                currentConnection.copy(
                    displayName = session.displayName,
                    baseUrl = session.baseUrl,
                    username = session.username,
                    hasStoredSession = true,
                )
            },
            refreshedConnectionState = { restoredConnection ->
                restoredConnection.copy(message = "Refreshed")
            },
            invalidConnection = { currentConnection, message ->
                currentConnection.copy(message = message, isError = true)
            },
        )

        val ready = result as StoredSessionRefreshInputPreparation.Ready
        assertEquals(savedSession, ready.session)
        assertEquals(HTTP_WARNING_MESSAGE, ready.cleartextWarningMessage)
    }

    @Test
    fun `persist remote session falls back to updated saved session metadata`() = runTest {
        val store = TestStoredSessionStore()
        val fallbackSession = TestStoredSession(
            displayName = "Old Name",
            baseUrl = "https://old.example.com",
            username = "old-user",
            accessToken = "saved-access-token",
            apiKey = "saved-api-key",
        )

        persistRemoteSession(
            sessionStore = store,
            session = null,
            fallbackSession = fallbackSession,
            displayName = "New Name",
            baseUrl = "https://new.example.com",
            username = "new-user",
            toStoredSession = { _, _, _, _ -> error("Unexpected") },
            restoreFallbackSession = { savedSession, displayName, baseUrl, username ->
                savedSession.copy(
                    displayName = displayName,
                    baseUrl = baseUrl,
                    username = username,
                )
            },
        )

        assertEquals("New Name", store.sessionFlow.value?.displayName)
        assertEquals("https://new.example.com", store.sessionFlow.value?.baseUrl)
        assertEquals("new-user", store.sessionFlow.value?.username)
        assertEquals("saved-access-token", store.sessionFlow.value?.accessToken)
        assertEquals("saved-api-key", store.sessionFlow.value?.apiKey)
    }
}

private data class TestConnection(
    val displayName: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val hasStoredSession: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

private data class TestStoredSession(
    override val displayName: String,
    override val baseUrl: String,
    override val backupBaseUrl: String? = null,
    override val activeBaseUrl: String? = null,
    override val username: String,
    override val accessToken: String,
    val apiKey: String?,
    override val refreshToken: String? = null,
) : AccessTokenStoredSession {
    override val sourceType: SourceType = SourceType.KAVITA
}

private class TestStoredSessionStore : SourceSessionStore<TestStoredSession> {
    private val sessionState = MutableStateFlow<TestStoredSession?>(null)

    override val sessionFlow: StateFlow<TestStoredSession?> = sessionState

    override suspend fun save(
        session: TestStoredSession,
    ) {
        sessionState.value = session
    }

    override suspend fun clear() {
        sessionState.value = null
    }
}
