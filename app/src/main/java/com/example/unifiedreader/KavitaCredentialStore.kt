package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedPreferencesKavitaCredentialStore @VisibleForTesting internal constructor(
    private val preferences: SharedPreferences,
    private val cipher: GrimmoryCredentialCipher,
) : KavitaCredentialStore {
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            KAVITA_CREDENTIALS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = AndroidKeystoreGrimmoryCredentialCipher(),
    )

    internal constructor(
        @ApplicationContext context: Context,
        cipher: GrimmoryCredentialCipher,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            KAVITA_CREDENTIALS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = cipher,
    )

    private val sessionState = MutableStateFlow(loadStoredSession())

    override val sessionFlow: StateFlow<KavitaStoredSession?>
        get() = sessionState

    override suspend fun save(
        session: KavitaStoredSession,
    ) {
        val committed = preferences.edit()
            .putString(KavitaCredentialKeys.DisplayName, cipher.encrypt(session.displayName))
            .putString(KavitaCredentialKeys.BaseUrl, cipher.encrypt(session.baseUrl))
            .putString(KavitaCredentialKeys.Username, cipher.encrypt(session.username))
            .putString(KavitaCredentialKeys.AccessToken, cipher.encrypt(session.accessToken))
            .putString(KavitaCredentialKeys.RefreshToken, session.refreshToken?.let(cipher::encrypt))
            .putString(KavitaCredentialKeys.ApiKey, session.apiKey?.let(cipher::encrypt))
            .commit()
        if (!committed) {
            throw IOException("Unable to save Kavita credentials.")
        }
        sessionState.value = session
    }

    override suspend fun clear() {
        val committed = preferences.edit().clear().commit()
        if (!committed) {
            throw IOException("Unable to clear Kavita credentials.")
        }
        sessionState.value = null
    }

    private fun loadStoredSession(): KavitaStoredSession? {
        return try {
            KavitaStoredSession(
                displayName = decrypt(KavitaCredentialKeys.DisplayName) ?: return null,
                baseUrl = decrypt(KavitaCredentialKeys.BaseUrl) ?: return null,
                username = decrypt(KavitaCredentialKeys.Username) ?: return null,
                accessToken = decrypt(KavitaCredentialKeys.AccessToken) ?: return null,
                refreshToken = decrypt(KavitaCredentialKeys.RefreshToken),
                apiKey = decrypt(KavitaCredentialKeys.ApiKey),
            )
        } catch (_: Exception) {
            preferences.edit().clear().apply()
            null
        }
    }

    private fun decrypt(
        key: String,
    ): String? = preferences.getString(key, null)?.let(cipher::decrypt)
}

private object KavitaCredentialKeys {
    const val DisplayName = "display_name"
    const val BaseUrl = "base_url"
    const val Username = "username"
    const val AccessToken = "access_token"
    const val RefreshToken = "refresh_token"
    const val ApiKey = "api_key"
}

private const val KAVITA_CREDENTIALS_PREFERENCES_NAME = "unified_reader_kavita_credentials"
