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
class StumpCredentialStoreTest {
    @Test
    fun `store saves reloads and clears stump session`() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("stump_credentials_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val session = StumpStoredSession(
            displayName = "Home Stump",
            baseUrl = "https://stump.example",
            username = "reader",
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
        val store = SharedPreferencesStumpCredentialStore(
            preferences = preferences,
            cipher = PrefixStumpCredentialCipher(),
        )

        store.save(session)

        assertEquals(session, store.sessionFlow.value)
        assertTrue(preferences.all.values.all { value -> value.toString().startsWith("encrypted:") })
        assertFalse(preferences.all.values.contains("access-token"))
        val reloadedStore = SharedPreferencesStumpCredentialStore(
            preferences = preferences,
            cipher = PrefixStumpCredentialCipher(),
        )
        assertEquals(session, reloadedStore.sessionFlow.value)

        reloadedStore.clear()

        assertNull(reloadedStore.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun `store clears stored session when credential decrypt fails`() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("stump_credentials_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        SharedPreferencesStumpCredentialStore(
            preferences = preferences,
            cipher = PrefixStumpCredentialCipher(),
        ).save(
            StumpStoredSession(
                displayName = "Home Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "access-token",
                refreshToken = "refresh-token",
            ),
        )

        val store = SharedPreferencesStumpCredentialStore(
            preferences = preferences,
            cipher = FailingStumpDecryptCredentialCipher(),
        )

        assertNull(store.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }
}

private class PrefixStumpCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String = "encrypted:$value"

    override fun decrypt(
        value: String,
    ): String = value.removePrefix("encrypted:")
}

private class FailingStumpDecryptCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String = value

    override fun decrypt(
        value: String,
    ): String {
        throw GeneralSecurityException("Keystore entry is invalid.")
    }
}
