package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedPreferencesStumpCredentialStore @VisibleForTesting internal constructor(
    private val preferences: SharedPreferences,
    private val cipher: GrimmoryCredentialCipher,
) : StumpCredentialStore {
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            STUMP_CREDENTIALS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = AndroidKeystoreGrimmoryCredentialCipher(),
    )

    internal constructor(
        @ApplicationContext context: Context,
        cipher: GrimmoryCredentialCipher,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            STUMP_CREDENTIALS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = cipher,
    )

    private val sessionState = MutableStateFlow(loadStoredSession())

    override val sessionFlow: StateFlow<StumpStoredSession?>
        get() = sessionState

    override suspend fun save(
        session: StumpStoredSession,
    ) {
        val committed = preferences.edit()
            .putString(StumpCredentialKeys.DisplayName, cipher.encrypt(session.displayName))
            .putString(StumpCredentialKeys.BaseUrl, cipher.encrypt(session.baseUrl))
            .putString(StumpCredentialKeys.Username, cipher.encrypt(session.username))
            .putString(StumpCredentialKeys.AccessToken, cipher.encrypt(session.accessToken))
            .putString(StumpCredentialKeys.RefreshToken, session.refreshToken?.let(cipher::encrypt))
            .commit()
        if (!committed) {
            throw IOException("Unable to save Stump credentials.")
        }
        sessionState.value = session
    }

    override suspend fun clear() {
        val committed = preferences.edit().clear().commit()
        if (!committed) {
            throw IOException("Unable to clear Stump credentials.")
        }
        sessionState.value = null
    }

    private fun loadStoredSession(): StumpStoredSession? {
        return try {
            StumpStoredSession(
                displayName = decrypt(StumpCredentialKeys.DisplayName) ?: return null,
                baseUrl = decrypt(StumpCredentialKeys.BaseUrl) ?: return null,
                username = decrypt(StumpCredentialKeys.Username) ?: return null,
                accessToken = decrypt(StumpCredentialKeys.AccessToken) ?: return null,
                refreshToken = decrypt(StumpCredentialKeys.RefreshToken),
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

private object StumpCredentialKeys {
    const val DisplayName = "display_name"
    const val BaseUrl = "base_url"
    const val Username = "username"
    const val AccessToken = "access_token"
    const val RefreshToken = "refresh_token"
}

private const val STUMP_CREDENTIALS_PREFERENCES_NAME = "unified_reader_stump_credentials"
