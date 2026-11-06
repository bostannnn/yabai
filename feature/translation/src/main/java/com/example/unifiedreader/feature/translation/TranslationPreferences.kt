package com.example.unifiedreader.feature.translation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Your languages. [targetLanguage] is "Translate into" (null: the device's language);
 * [otherLanguage] is the last other language you translated into, used when a book is
 * already in your language.
 */
data class TranslationPreferences(
    val targetLanguage: String? = null,
    val otherLanguage: String? = null,
)

interface TranslationPreferencesStore {
    val preferences: Flow<TranslationPreferences>
    suspend fun current(): TranslationPreferences = preferences.first()
    suspend fun update(transform: (TranslationPreferences) -> TranslationPreferences)
}

private val Context.translationDataStore: DataStore<Preferences> by preferencesDataStore(name = "translation")

internal class DataStoreTranslationPreferencesStore(context: Context) : TranslationPreferencesStore {
    private val store = context.applicationContext.translationDataStore

    override val preferences: Flow<TranslationPreferences> = store.data.map { values ->
        TranslationPreferences(
            targetLanguage = values[TargetKey],
            otherLanguage = values[OtherKey],
        )
    }

    override suspend fun update(transform: (TranslationPreferences) -> TranslationPreferences) {
        store.edit { values ->
            val next = transform(TranslationPreferences(values[TargetKey], values[OtherKey]))
            values.setOrRemove(TargetKey, next.targetLanguage)
            values.setOrRemove(OtherKey, next.otherLanguage)
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val TargetKey = stringPreferencesKey("target_language")
        val OtherKey = stringPreferencesKey("other_language")
    }
}
