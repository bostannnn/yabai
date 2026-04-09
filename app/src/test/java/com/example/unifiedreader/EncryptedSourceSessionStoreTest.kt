package com.example.unifiedreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.security.GeneralSecurityException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedSourceSessionStoreTest {
    @Test
    fun `store saves reloads and clears encrypted session`() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("encrypted_store_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val session = TestEncryptedSession(
            displayName = "Server",
            accessToken = "access-token",
            optionalSecret = null,
        )
        val store = createStore(
            preferences = preferences,
            cipher = PrefixTestCredentialCipher(),
        )

        store.save(session)

        assertEquals(session, store.sessionFlow.value)
        assertTrue(preferences.all.values.all { value -> value.toString().startsWith("encrypted:") })
        assertFalse(preferences.all.values.contains("access-token"))
        assertFalse(preferences.contains(TEST_OPTIONAL_SECRET_KEY))

        val reloadedStore = createStore(
            preferences = preferences,
            cipher = PrefixTestCredentialCipher(),
        )
        assertEquals(session, reloadedStore.sessionFlow.value)

        reloadedStore.clear()

        assertNull(reloadedStore.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun `store removes persisted optional value when saving null`() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("encrypted_store_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val store = createStore(
            preferences = preferences,
            cipher = PrefixTestCredentialCipher(),
        )

        store.save(
            TestEncryptedSession(
                displayName = "Server",
                accessToken = "access-token",
                optionalSecret = "secret",
            ),
        )
        assertTrue(preferences.contains(TEST_OPTIONAL_SECRET_KEY))

        store.save(
            TestEncryptedSession(
                displayName = "Server",
                accessToken = "access-token",
                optionalSecret = null,
            ),
        )

        assertFalse(preferences.contains(TEST_OPTIONAL_SECRET_KEY))
        assertEquals(
            TestEncryptedSession(
                displayName = "Server",
                accessToken = "access-token",
                optionalSecret = null,
            ),
            store.sessionFlow.value,
        )
        val reloadedStore = createStore(
            preferences = preferences,
            cipher = PrefixTestCredentialCipher(),
        )
        assertEquals(
            TestEncryptedSession(
                displayName = "Server",
                accessToken = "access-token",
                optionalSecret = null,
            ),
            reloadedStore.sessionFlow.value,
        )
    }

    @Test
    fun `store clears persisted session when decrypt fails`() {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("encrypted_store_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        val seededStore = createStore(
            preferences = preferences,
            cipher = PrefixTestCredentialCipher(),
        )

        runBlocking {
            seededStore.save(
                TestEncryptedSession(
                    displayName = "Server",
                    accessToken = "access-token",
                    optionalSecret = "secret",
                ),
            )
        }

        val store = createStore(
            preferences = preferences,
            cipher = FailingTestCredentialCipher(),
        )

        assertNull(store.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }
}

private fun createStore(
    preferences: android.content.SharedPreferences,
    cipher: GrimmoryCredentialCipher,
): EncryptedSourceSessionStore<TestEncryptedSession> = EncryptedSourceSessionStore(
    preferences = preferences,
    cipher = cipher,
    saveErrorMessage = "Unable to save test credentials.",
    clearErrorMessage = "Unable to clear test credentials.",
    readSession = readSession@{ decrypt ->
        TestEncryptedSession(
            displayName = decrypt(TEST_DISPLAY_NAME_KEY) ?: return@readSession null,
            accessToken = decrypt(TEST_ACCESS_TOKEN_KEY) ?: return@readSession null,
            optionalSecret = decrypt(TEST_OPTIONAL_SECRET_KEY),
        )
    },
    writeSession = { session ->
        mapOf(
            TEST_DISPLAY_NAME_KEY to session.displayName,
            TEST_ACCESS_TOKEN_KEY to session.accessToken,
            TEST_OPTIONAL_SECRET_KEY to session.optionalSecret,
        )
    },
)

private data class TestEncryptedSession(
    val displayName: String,
    val accessToken: String,
    val optionalSecret: String?,
)

private class PrefixTestCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String = "encrypted:$value"

    override fun decrypt(
        value: String,
    ): String = value.removePrefix("encrypted:")
}

private class FailingTestCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String = value

    override fun decrypt(
        value: String,
    ): String {
        throw GeneralSecurityException("Invalid keystore entry.")
    }
}

private const val TEST_DISPLAY_NAME_KEY = "display_name"
private const val TEST_ACCESS_TOKEN_KEY = "access_token"
private const val TEST_OPTIONAL_SECRET_KEY = "optional_secret"
