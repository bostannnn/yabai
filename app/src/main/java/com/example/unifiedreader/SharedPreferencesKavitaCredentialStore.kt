package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext

internal class SharedPreferencesKavitaCredentialStore @VisibleForTesting internal constructor(
    preferences: SharedPreferences,
    cipher: GrimmoryCredentialCipher,
) : EncryptedCredentialStore<KavitaStoredSession>(preferences, cipher, sourceName = "Kavita"),
    KavitaCredentialStore {
    constructor(
        @ApplicationContext context: Context,
        cipher: GrimmoryCredentialCipher = AndroidKeystoreGrimmoryCredentialCipher(),
    ) : this(context.credentialPreferences(KAVITA_CREDENTIALS_PREFERENCES_NAME), cipher)

    override fun sessionFrom(fields: StoredSessionFields, decrypt: (String) -> String?) = KavitaStoredSession(
        displayName = fields.displayName,
        baseUrl = fields.baseUrl,
        backupBaseUrl = fields.backupBaseUrl,
        activeBaseUrl = fields.activeBaseUrl,
        username = fields.username,
        accessToken = fields.accessToken,
        refreshToken = fields.refreshToken,
        apiKey = decrypt(API_KEY),
    )

    override fun extraFields(session: KavitaStoredSession) = mapOf(API_KEY to session.apiKey)
}

private const val API_KEY = "api_key"
private const val KAVITA_CREDENTIALS_PREFERENCES_NAME = "unified_reader_kavita_credentials"
