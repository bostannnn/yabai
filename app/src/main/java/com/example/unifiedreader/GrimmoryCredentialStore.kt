package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.StateFlow

data class GrimmoryStoredSession(
    override val displayName: String,
    override val baseUrl: String,
    override val backupBaseUrl: String? = null,
    override val activeBaseUrl: String? = null,
    override val username: String,
    override val accessToken: String,
    override val refreshToken: String,
) : AccessTokenStoredSession {
    override val sourceType = com.example.unifiedreader.core.model.SourceType.GRIMMORY
}

interface GrimmoryCredentialStore : SourceSessionStore<GrimmoryStoredSession>

class SharedPreferencesGrimmoryCredentialStore @VisibleForTesting internal constructor(
    private val preferences: SharedPreferences,
    private val cipher: GrimmoryCredentialCipher,
) : GrimmoryCredentialStore {
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            GRIMMORY_CREDENTIALS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = AndroidKeystoreGrimmoryCredentialCipher(),
    )

    internal constructor(
        @ApplicationContext context: Context,
        cipher: GrimmoryCredentialCipher,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            GRIMMORY_CREDENTIALS_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        cipher = cipher,
    )
    private val sessionStore = EncryptedSourceSessionStore(
        preferences = preferences,
        cipher = cipher,
        saveErrorMessage = "Unable to save Grimmory credentials.",
        clearErrorMessage = "Unable to clear Grimmory credentials.",
        readSession = readSession@{ decrypt ->
            GrimmoryStoredSession(
                displayName = decrypt(GrimmoryCredentialKeys.DisplayName) ?: return@readSession null,
                baseUrl = decrypt(GrimmoryCredentialKeys.BaseUrl) ?: return@readSession null,
                backupBaseUrl = decrypt(GrimmoryCredentialKeys.BackupBaseUrl),
                activeBaseUrl = decrypt(GrimmoryCredentialKeys.ActiveBaseUrl),
                username = decrypt(GrimmoryCredentialKeys.Username) ?: return@readSession null,
                accessToken = decrypt(GrimmoryCredentialKeys.AccessToken) ?: return@readSession null,
                refreshToken = decrypt(GrimmoryCredentialKeys.RefreshToken) ?: return@readSession null,
            )
        },
        writeSession = { session ->
            mapOf(
                GrimmoryCredentialKeys.DisplayName to session.displayName,
                GrimmoryCredentialKeys.BaseUrl to session.baseUrl,
                GrimmoryCredentialKeys.BackupBaseUrl to session.backupBaseUrl,
                GrimmoryCredentialKeys.ActiveBaseUrl to session.activeBaseUrl,
                GrimmoryCredentialKeys.Username to session.username,
                GrimmoryCredentialKeys.AccessToken to session.accessToken,
                GrimmoryCredentialKeys.RefreshToken to session.refreshToken,
            )
        },
    )

    override val sessionFlow: StateFlow<GrimmoryStoredSession?>
        get() = sessionStore.sessionFlow

    override suspend fun save(
        session: GrimmoryStoredSession,
    ) = sessionStore.save(session)

    override suspend fun clear() = sessionStore.clear()
}

internal interface GrimmoryCredentialCipher {
    fun encrypt(
        value: String,
    ): String

    fun decrypt(
        value: String,
    ): String
}

internal class AndroidKeystoreGrimmoryCredentialCipher : GrimmoryCredentialCipher {
    override fun encrypt(
        value: String,
    ): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(ENCRYPTED_VALUE_SEPARATOR)
    }

    override fun decrypt(
        value: String,
    ): String {
        val parts = value.split(ENCRYPTED_VALUE_SEPARATOR, limit = 2)
        require(parts.size == 2) { "Invalid encrypted Grimmory credential value." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }
}

private object GrimmoryCredentialKeys {
    const val DisplayName = "display_name"
    const val BaseUrl = "base_url"
    const val BackupBaseUrl = "backup_base_url"
    const val ActiveBaseUrl = "active_base_url"
    const val Username = "username"
    const val AccessToken = "access_token"
    const val RefreshToken = "refresh_token"
}

private const val GRIMMORY_CREDENTIALS_PREFERENCES_NAME = "unified_reader_grimmory_credentials"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "unified_reader_grimmory_credentials"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val ENCRYPTED_VALUE_SEPARATOR = ":"
