package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow

/** The fields every server's saved sign-in has, as read from the store. */
internal data class StoredSessionFields(
    val displayName: String,
    val baseUrl: String,
    val backupBaseUrl: String?,
    val activeBaseUrl: String?,
    val username: String,
    val accessToken: String,
    val refreshToken: String?,
)

/**
 * A server's saved sign-in, encrypted in its own preferences file. The fields every server has are stored under the
 * same keys for all; a subclass builds its session from them and adds its own fields.
 */
internal abstract class EncryptedCredentialStore<S : AccessTokenStoredSession>(
    preferences: SharedPreferences,
    cipher: GrimmoryCredentialCipher,
    sourceName: String,
) : SourceSessionStore<S> {
    /** The session from [fields]; null when a field this server needs is missing. */
    protected abstract fun sessionFrom(fields: StoredSessionFields, decrypt: (key: String) -> String?): S?

    /** Fields only this server saves, by key. */
    protected open fun extraFields(session: S): Map<String, String?> = emptyMap()

    private val sessionStore = EncryptedSourceSessionStore(
        preferences = preferences,
        cipher = cipher,
        saveErrorMessage = "Unable to save $sourceName credentials.",
        clearErrorMessage = "Unable to clear $sourceName credentials.",
        readSession = readSession@{ decrypt ->
            val fields = StoredSessionFields(
                displayName = decrypt(CredentialKeys.DisplayName) ?: return@readSession null,
                baseUrl = decrypt(CredentialKeys.BaseUrl) ?: return@readSession null,
                backupBaseUrl = decrypt(CredentialKeys.BackupBaseUrl),
                activeBaseUrl = decrypt(CredentialKeys.ActiveBaseUrl),
                username = decrypt(CredentialKeys.Username) ?: return@readSession null,
                accessToken = decrypt(CredentialKeys.AccessToken) ?: return@readSession null,
                refreshToken = decrypt(CredentialKeys.RefreshToken),
            )
            sessionFrom(fields, decrypt)
        },
        writeSession = { session ->
            mapOf(
                CredentialKeys.DisplayName to session.displayName,
                CredentialKeys.BaseUrl to session.baseUrl,
                CredentialKeys.BackupBaseUrl to session.backupBaseUrl,
                CredentialKeys.ActiveBaseUrl to session.activeBaseUrl,
                CredentialKeys.Username to session.username,
                CredentialKeys.AccessToken to session.accessToken,
                CredentialKeys.RefreshToken to session.refreshToken,
            ) + extraFields(session)
        },
    )

    override val sessionFlow: StateFlow<S?>
        get() = sessionStore.sessionFlow

    override suspend fun save(session: S) = sessionStore.save(session)

    override suspend fun clear() = sessionStore.clear()
}

/** A server's own preferences file for its saved sign-in. */
internal fun Context.credentialPreferences(name: String): SharedPreferences =
    applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

private object CredentialKeys {
    const val DisplayName = "display_name"
    const val BaseUrl = "base_url"
    const val BackupBaseUrl = "backup_base_url"
    const val ActiveBaseUrl = "active_base_url"
    const val Username = "username"
    const val AccessToken = "access_token"
    const val RefreshToken = "refresh_token"
}
