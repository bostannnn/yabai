package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext

internal class SharedPreferencesStumpCredentialStore @VisibleForTesting internal constructor(
    preferences: SharedPreferences,
    cipher: GrimmoryCredentialCipher,
) : EncryptedCredentialStore<StumpStoredSession>(preferences, cipher, sourceName = "Stump"),
    StumpCredentialStore {
    constructor(
        @ApplicationContext context: Context,
        cipher: GrimmoryCredentialCipher = AndroidKeystoreGrimmoryCredentialCipher(),
    ) : this(context.credentialPreferences(STUMP_CREDENTIALS_PREFERENCES_NAME), cipher)

    override fun sessionFrom(fields: StoredSessionFields, decrypt: (String) -> String?) = StumpStoredSession(
        displayName = fields.displayName,
        baseUrl = fields.baseUrl,
        backupBaseUrl = fields.backupBaseUrl,
        activeBaseUrl = fields.activeBaseUrl,
        username = fields.username,
        accessToken = fields.accessToken,
        refreshToken = fields.refreshToken,
    )
}

private const val STUMP_CREDENTIALS_PREFERENCES_NAME = "unified_reader_stump_credentials"
