package com.example.unifiedreader.feature.translation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Your DeepL key, kept on this tablet only. */
interface DeepLKeyStore {
    /** The key, or null: none saved, or the tablet's keystore lost what encrypted it. */
    val key: Flow<String?>
    suspend fun save(key: String)
    suspend fun clear()
}

private val Context.deepLDataStore: DataStore<Preferences> by preferencesDataStore(name = "translation_deepl")

/** Encrypted with an AES key in the Android keystore, which never leaves the tablet. */
internal class KeystoreDeepLKeyStore(context: Context) : DeepLKeyStore {
    private val store = context.applicationContext.deepLDataStore

    override val key: Flow<String?> = store.data.map { values -> values[KeyKey]?.let(::decrypt) }

    override suspend fun save(key: String) {
        val encrypted = encrypt(key.trim())
        store.edit { values -> values[KeyKey] = encrypted }
    }

    override suspend fun clear() {
        store.edit { values -> values.remove(KeyKey) }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    // A wiped keystore (a restore, a new screen lock) can't open the old value: that reads as no key.
    private fun decrypt(value: String): String? = runCatching {
        val (iv, encrypted) = value.split(':').also { require(it.size == 2) }
        val key = existingSecretKey() ?: return null
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, Base64.decode(iv, Base64.NO_WRAP)))
        cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun existingSecretKey(): SecretKey? =
        (KeyStore.getInstance(AndroidKeyStore).apply { load(null) }.getEntry(Alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun secretKey(): SecretKey = synchronized(KeyLock) {
        existingSecretKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        val KeyKey = stringPreferencesKey("deepl_key")
        const val AndroidKeyStore = "AndroidKeyStore"
        const val Alias = "yabai_deepl_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val TagBits = 128
        val KeyLock = Any()
    }
}
