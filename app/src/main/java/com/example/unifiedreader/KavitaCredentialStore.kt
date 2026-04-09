package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val sessionStore = EncryptedSourceSessionStore(
        preferences = preferences,
        cipher = cipher,
        saveErrorMessage = "Unable to save Kavita credentials.",
        clearErrorMessage = "Unable to clear Kavita credentials.",
        readSession = readSession@{ decrypt ->
            KavitaStoredSession(
                displayName = decrypt(KavitaCredentialKeys.DisplayName) ?: return@readSession null,
                baseUrl = decrypt(KavitaCredentialKeys.BaseUrl) ?: return@readSession null,
                username = decrypt(KavitaCredentialKeys.Username) ?: return@readSession null,
                accessToken = decrypt(KavitaCredentialKeys.AccessToken) ?: return@readSession null,
                refreshToken = decrypt(KavitaCredentialKeys.RefreshToken),
                apiKey = decrypt(KavitaCredentialKeys.ApiKey),
            )
        },
        writeSession = { session ->
            mapOf(
                KavitaCredentialKeys.DisplayName to session.displayName,
                KavitaCredentialKeys.BaseUrl to session.baseUrl,
                KavitaCredentialKeys.Username to session.username,
                KavitaCredentialKeys.AccessToken to session.accessToken,
                KavitaCredentialKeys.RefreshToken to session.refreshToken,
                KavitaCredentialKeys.ApiKey to session.apiKey,
            )
        },
    )

    override val sessionFlow: StateFlow<KavitaStoredSession?>
        get() = sessionStore.sessionFlow

    override suspend fun save(
        session: KavitaStoredSession,
    ) = sessionStore.save(session)

    override suspend fun clear() = sessionStore.clear()
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
