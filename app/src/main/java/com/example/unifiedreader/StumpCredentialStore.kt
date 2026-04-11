package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val sessionStore = EncryptedSourceSessionStore(
        preferences = preferences,
        cipher = cipher,
        saveErrorMessage = "Unable to save Stump credentials.",
        clearErrorMessage = "Unable to clear Stump credentials.",
        readSession = readSession@{ decrypt ->
            StumpStoredSession(
                displayName = decrypt(StumpCredentialKeys.DisplayName) ?: return@readSession null,
                baseUrl = decrypt(StumpCredentialKeys.BaseUrl) ?: return@readSession null,
                backupBaseUrl = decrypt(StumpCredentialKeys.BackupBaseUrl),
                activeBaseUrl = decrypt(StumpCredentialKeys.ActiveBaseUrl),
                username = decrypt(StumpCredentialKeys.Username) ?: return@readSession null,
                accessToken = decrypt(StumpCredentialKeys.AccessToken) ?: return@readSession null,
                refreshToken = decrypt(StumpCredentialKeys.RefreshToken),
            )
        },
        writeSession = { session ->
            mapOf(
                StumpCredentialKeys.DisplayName to session.displayName,
                StumpCredentialKeys.BaseUrl to session.baseUrl,
                StumpCredentialKeys.BackupBaseUrl to session.backupBaseUrl,
                StumpCredentialKeys.ActiveBaseUrl to session.activeBaseUrl,
                StumpCredentialKeys.Username to session.username,
                StumpCredentialKeys.AccessToken to session.accessToken,
                StumpCredentialKeys.RefreshToken to session.refreshToken,
            )
        },
    )

    override val sessionFlow: StateFlow<StumpStoredSession?>
        get() = sessionStore.sessionFlow

    override suspend fun save(
        session: StumpStoredSession,
    ) = sessionStore.save(session)

    override suspend fun clear() = sessionStore.clear()
}

private object StumpCredentialKeys {
    const val DisplayName = "display_name"
    const val BaseUrl = "base_url"
    const val BackupBaseUrl = "backup_base_url"
    const val ActiveBaseUrl = "active_base_url"
    const val Username = "username"
    const val AccessToken = "access_token"
    const val RefreshToken = "refresh_token"
}

private const val STUMP_CREDENTIALS_PREFERENCES_NAME = "unified_reader_stump_credentials"
