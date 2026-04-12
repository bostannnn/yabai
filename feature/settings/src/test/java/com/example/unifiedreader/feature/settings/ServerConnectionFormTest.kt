package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionFormTest {
    @Test
    fun `sync button stays hidden until a stored session exists`() {
        val actionState = buildServerConnectionActionState(
            state = formState(hasStoredSession = false, isConnecting = false),
        )

        assertFalse(actionState.showSyncButton)
        assertNull(actionState.activeAction)
        assertFalse(actionState.canSync)
    }

    @Test
    fun `stored session connect uses explicit connect action`() {
        val actionState = buildServerConnectionActionState(
            state = formState(
                hasStoredSession = true,
                isConnecting = true,
                activeAction = ServerConnectionAction.Connect,
            ),
        )

        assertTrue(actionState.showSyncButton)
        assertEquals(ServerConnectionAction.Connect, actionState.activeAction)
        assertFalse(actionState.canSync)
    }

    @Test
    fun `sync button spinner only appears during explicit sync`() {
        val actionState = buildServerConnectionActionState(
            state = formState(
                hasStoredSession = true,
                isConnecting = true,
                activeAction = ServerConnectionAction.Sync,
            ),
        )

        assertEquals(ServerConnectionAction.Sync, actionState.activeAction)
        assertFalse(actionState.canSync)
    }

    @Test
    fun `stored session can sync when idle`() {
        val actionState = buildServerConnectionActionState(
            state = formState(
                hasStoredSession = true,
                isConnecting = false,
            ),
        )

        assertTrue(actionState.canSync)
    }
}

private fun formState(
    hasStoredSession: Boolean,
    isConnecting: Boolean,
    activeAction: ServerConnectionAction? = null,
    password: String = "",
): ServerConnectionFormState = ServerConnectionFormState(
    displayName = "Server",
    baseUrl = "https://server.example",
    backupBaseUrl = "",
    username = "user",
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    activeAction = activeAction,
    syncProgressFraction = null,
    syncProgressLabel = null,
    message = null,
    isError = false,
    canConnect = true,
)
