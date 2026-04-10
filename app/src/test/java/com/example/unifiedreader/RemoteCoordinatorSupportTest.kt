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
    fun `prepare remote connect input allows local http with cleartext warning`() {
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
        assertEquals(LOCAL_HTTP_WARNING_MESSAGE, ready.input.cleartextWarningMessage)
    }

    @Test
    fun `prepare remote connect input blocks public http`() {
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

        val invalid = result as RemoteConnectionInputPreparation.Invalid
        assertEquals(LOCAL_HTTP_ONLY_MESSAGE, invalid.connection.message)
        assertTrue(invalid.connection.isError)
    }

    @Test
    fun `connection success message includes cleartext warning`() {
        assertEquals(
            "Connected. $LOCAL_HTTP_WARNING_MESSAGE",
            connectionSuccessMessage(
                message = "Connected.",
                cleartextWarningMessage = LOCAL_HTTP_WARNING_MESSAGE,
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
    fun `prepare remote refresh input restores saved session before validation error`() {
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
    fun `prepare remote refresh input blocks stored public http session`() {
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

        val invalid = result as StoredSessionRefreshInputPreparation.Invalid
        assertEquals("Saved", invalid.connection.displayName)
        assertEquals("http://example.com", invalid.connection.baseUrl)
        assertEquals("reader", invalid.connection.username)
        assertTrue(invalid.connection.hasStoredSession)
        assertEquals(LOCAL_HTTP_ONLY_MESSAGE, invalid.connection.message)
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
