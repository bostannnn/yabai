package com.example.unifiedreader

import android.content.SharedPreferences
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

internal class EncryptedSourceSessionStore<T>(
    private val preferences: SharedPreferences,
    private val cipher: GrimmoryCredentialCipher,
    private val saveErrorMessage: String,
    private val clearErrorMessage: String,
    private val readSession: (decrypt: (String) -> String?) -> T?,
    private val writeSession: (T) -> Map<String, String?>,
) {
    private val sessionState = MutableStateFlow(loadStoredSession())

    val sessionFlow: StateFlow<T?>
        get() = sessionState

    suspend fun save(
        session: T,
    ) {
        // Encrypting and a synchronous commit both touch the disk or the keystore: never on the caller's thread.
        val committed = withContext(Dispatchers.IO) {
            preferences.edit().apply {
                writeSession(session).forEach { (key, value) ->
                    putString(key, value?.let(cipher::encrypt))
                }
            }.commit()
        }
        if (!committed) {
            throw IOException(saveErrorMessage)
        }
        sessionState.value = session
    }

    suspend fun clear() {
        val committed = withContext(Dispatchers.IO) { preferences.edit().clear().commit() }
        if (!committed) {
            throw IOException(clearErrorMessage)
        }
        sessionState.value = null
    }

    // A read that fails keeps what is saved. The keystore can fail for a moment, after a reboot or on
    // some phones, and deleting then signed the server out for good; the next start reads it again,
    // and signing in again overwrites it.
    private fun loadStoredSession(): T? {
        return try {
            readSession(::decrypt)
        } catch (throwable: Exception) {
            Log.w(LOG_TAG, "Unable to read the saved sign-in; keeping it for the next start.", throwable)
            null
        }
    }

    private fun decrypt(
        key: String,
    ): String? = preferences.getString(key, null)?.let(cipher::decrypt)
}

private const val LOG_TAG = "UnifiedReaderCredentials"
