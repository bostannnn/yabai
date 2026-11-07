package com.example.unifiedreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KavitaCredentialStoreTest {
    @Test
    fun `store saves and reloads the API key with the shared fields`() = runBlocking {
        val preferences = testPreferences()
        val session = KavitaStoredSession(
            displayName = "Home Kavita",
            baseUrl = "https://kavita.example",
            backupBaseUrl = "https://backup.kavita.example",
            activeBaseUrl = "https://backup.kavita.example",
            username = "reader",
            accessToken = "access-token",
            refreshToken = null,
            apiKey = "api-key",
        )

        SharedPreferencesKavitaCredentialStore(preferences, PrefixKavitaCredentialCipher()).save(session)

        assertEquals("encrypted:api-key", preferences.getString("api_key", null))
        assertNull(preferences.getString("refresh_token", null))
        val reloaded = SharedPreferencesKavitaCredentialStore(preferences, PrefixKavitaCredentialCipher())
        assertEquals(session, reloaded.sessionFlow.value)

        reloaded.clear()

        assertNull(reloaded.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun `a saved sign-in without its access token reads as none`() {
        val preferences = testPreferences()
        preferences.edit()
            .putString("display_name", "encrypted:Home Kavita")
            .putString("base_url", "encrypted:https://kavita.example")
            .putString("username", "encrypted:reader")
            .commit()

        val store = SharedPreferencesKavitaCredentialStore(preferences, PrefixKavitaCredentialCipher())

        assertNull(store.sessionFlow.value)
    }

    private fun testPreferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("kavita_credentials_test_${System.nanoTime()}", Context.MODE_PRIVATE)
}

private class PrefixKavitaCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(value: String): String = "encrypted:$value"

    override fun decrypt(value: String): String = value.removePrefix("encrypted:")
}
