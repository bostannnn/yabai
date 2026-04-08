package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GrimmoryStoredSession(
    override val displayName: String,
    override val baseUrl: String,
    override val username: String,
    val accessToken: String,
    val refreshToken: String,
) : StoredSourceSession {
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
    private val _sessionFlow: MutableStateFlow<GrimmoryStoredSession?> = MutableStateFlow(loadStoredSession())

    override val sessionFlow: StateFlow<GrimmoryStoredSession?>
        get() = _sessionFlow

    override suspend fun save(
        session: GrimmoryStoredSession,
    ) {
        val committed = preferences.edit()
            .putString(GrimmoryCredentialKeys.DisplayName, cipher.encrypt(session.displayName))
            .putString(GrimmoryCredentialKeys.BaseUrl, cipher.encrypt(session.baseUrl))
            .putString(GrimmoryCredentialKeys.Username, cipher.encrypt(session.username))
            .putString(GrimmoryCredentialKeys.AccessToken, cipher.encrypt(session.accessToken))
            .putString(GrimmoryCredentialKeys.RefreshToken, cipher.encrypt(session.refreshToken))
            .commit()
        if (!committed) {
            throw IOException("Unable to save Grimmory credentials.")
        }
        _sessionFlow.value = session
    }

    override suspend fun clear() {
        val committed = preferences.edit().clear().commit()
        if (!committed) {
            throw IOException("Unable to clear Grimmory credentials.")
        }
        _sessionFlow.value = null
    }

    private fun loadStoredSession(): GrimmoryStoredSession? {
        return try {
            GrimmoryStoredSession(
                displayName = decrypt(GrimmoryCredentialKeys.DisplayName) ?: return null,
                baseUrl = decrypt(GrimmoryCredentialKeys.BaseUrl) ?: return null,
                username = decrypt(GrimmoryCredentialKeys.Username) ?: return null,
                accessToken = decrypt(GrimmoryCredentialKeys.AccessToken) ?: return null,
                refreshToken = decrypt(GrimmoryCredentialKeys.RefreshToken) ?: return null,
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
