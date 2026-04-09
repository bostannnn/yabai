package com.example.unifiedreader

import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
        val committed = preferences.edit().apply {
            writeSession(session).forEach { (key, value) ->
                putString(key, value?.let(cipher::encrypt))
            }
        }.commit()
        if (!committed) {
            throw IOException(saveErrorMessage)
        }
        sessionState.value = session
    }

    suspend fun clear() {
        val committed = preferences.edit().clear().commit()
        if (!committed) {
            throw IOException(clearErrorMessage)
        }
        sessionState.value = null
    }

    private fun loadStoredSession(): T? {
        return try {
            readSession(::decrypt)
        } catch (_: Exception) {
            preferences.edit().clear().apply()
            null
        }
    }

    private fun decrypt(
        key: String,
    ): String? = preferences.getString(key, null)?.let(cipher::decrypt)
}
