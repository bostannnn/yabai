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
class GrimmoryCredentialStoreTest {
    @Test
    fun `store saves reloads and clears grimmory session`() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("grimmory_credentials_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val session = GrimmoryStoredSession(
            displayName = "Home Grimmory",
            baseUrl = "https://grimmory.example",
            username = "reader",
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
        val store = SharedPreferencesGrimmoryCredentialStore(
            preferences = preferences,
            cipher = PrefixCredentialCipher(),
        )

        store.save(session)

        assertEquals(session, store.sessionFlow.value)
        assertTrue(preferences.all.values.all { value -> value.toString().startsWith("encrypted:") })
        assertFalse(preferences.all.values.contains("access-token"))
        val reloadedStore = SharedPreferencesGrimmoryCredentialStore(
            preferences = preferences,
            cipher = PrefixCredentialCipher(),
        )
        assertEquals(session, reloadedStore.sessionFlow.value)

        reloadedStore.clear()

        assertNull(reloadedStore.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun `store clears stored session when credential decrypt fails`() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("grimmory_credentials_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        SharedPreferencesGrimmoryCredentialStore(
            preferences = preferences,
            cipher = PrefixCredentialCipher(),
        ).save(
            GrimmoryStoredSession(
                displayName = "Home Grimmory",
                baseUrl = "https://grimmory.example",
                username = "reader",
                accessToken = "access-token",
                refreshToken = "refresh-token",
            ),
        )

        val store = SharedPreferencesGrimmoryCredentialStore(
            preferences = preferences,
            cipher = FailingDecryptCredentialCipher(),
        )

        assertNull(store.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
    }
}

private class PrefixCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String = "encrypted:$value"

    override fun decrypt(
        value: String,
    ): String = value.removePrefix("encrypted:")
}

private class FailingDecryptCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String = value

    override fun decrypt(
        value: String,
    ): String {
        throw GeneralSecurityException("Keystore entry is invalid.")
    }
}
