package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GrimmoryStoredSession(
    override val displayName: String,
    override val baseUrl: String,
    override val backupBaseUrl: String? = null,
    override val activeBaseUrl: String? = null,
    override val username: String,
    override val accessToken: String,
    override val refreshToken: String,
) : AccessTokenStoredSession, AddressedSession<GrimmoryStoredSession> {
    override val sourceType = com.example.unifiedreader.core.model.SourceType.GRIMMORY

    override fun withActiveBaseUrl(activeBaseUrl: String) = copy(activeBaseUrl = activeBaseUrl)
}

interface GrimmoryCredentialStore : SourceSessionStore<GrimmoryStoredSession>

internal class SharedPreferencesGrimmoryCredentialStore @VisibleForTesting internal constructor(
    preferences: SharedPreferences,
    cipher: GrimmoryCredentialCipher,
) : EncryptedCredentialStore<GrimmoryStoredSession>(preferences, cipher, sourceName = "Grimmory"),
    GrimmoryCredentialStore {
    constructor(
        @ApplicationContext context: Context,
        cipher: GrimmoryCredentialCipher = AndroidKeystoreGrimmoryCredentialCipher(),
    ) : this(context.credentialPreferences(GRIMMORY_CREDENTIALS_PREFERENCES_NAME), cipher)

    /** Grimmory always issues a refresh token: a sign-in saved without one is incomplete. */
    override fun sessionFrom(fields: StoredSessionFields, decrypt: (String) -> String?): GrimmoryStoredSession? =
        fields.refreshToken?.let { refreshToken ->
            GrimmoryStoredSession(
                displayName = fields.displayName,
                baseUrl = fields.baseUrl,
                backupBaseUrl = fields.backupBaseUrl,
                activeBaseUrl = fields.activeBaseUrl,
                username = fields.username,
                accessToken = fields.accessToken,
                refreshToken = refreshToken,
            )
        }
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
            getSecretKey() ?: throw GeneralSecurityException("The credential key is missing from the Android keystore."),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    // Reading never makes a key: a new one in place of a key the keystore didn't hand back for a moment
    // would leave every saved sign-in, on all three servers, unreadable for good.
    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    // One at a time: two stores saving their first sign-in at once would each make a key, and the
    // second would replace the one the first just encrypted with.
    private fun getOrCreateSecretKey(): SecretKey = synchronized(KeyCreationLock) {
        getSecretKey() ?: createSecretKey()
    }

    private fun createSecretKey(): SecretKey {
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

    private companion object {
        val KeyCreationLock = Any()
    }
}

private const val GRIMMORY_CREDENTIALS_PREFERENCES_NAME = "unified_reader_grimmory_credentials"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "unified_reader_grimmory_credentials"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val ENCRYPTED_VALUE_SEPARATOR = ":"
