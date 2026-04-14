package com.example.unifiedreader.feature.reader.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

data class ComicReaderModePreferences(
    val libraryModesById: Map<String, ComicReaderMode> = emptyMap(),
    val seriesModesByKey: Map<String, ComicReaderMode> = emptyMap(),
)

interface ComicReaderModePreferencesStore {
    val preferencesFlow: StateFlow<ComicReaderModePreferences>

    suspend fun setLibraryMode(
        libraryId: String,
        mode: ComicReaderMode,
    )

    suspend fun setSeriesModeOverride(
        seriesKey: String,
        mode: ComicReaderMode?,
    )
}

class DataStoreComicReaderModePreferencesStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.comicReaderModePreferencesDataStore,
    private val ioRetryDelayMillis: Long = DATASTORE_IO_RETRY_DELAY_MILLIS,
) : ComicReaderModePreferencesStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val preferencesFlow: StateFlow<ComicReaderModePreferences> = dataStore.data
        .retryWhen { error, _ ->
            if (error is IOException) {
                emit(emptyPreferences())
                delay(ioRetryDelayMillis)
            }
            error is IOException
        }
        .map(::toComicReaderModePreferences)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ComicReaderModePreferences(),
        )

    override suspend fun setLibraryMode(
        libraryId: String,
        mode: ComicReaderMode,
    ) {
        dataStore.edit { preferences ->
            preferences[ReaderModeKeys.libraryMode(libraryId)] = mode.name
        }
    }

    override suspend fun setSeriesModeOverride(
        seriesKey: String,
        mode: ComicReaderMode?,
    ) {
        dataStore.edit { preferences ->
            val key = ReaderModeKeys.seriesMode(seriesKey)
            if (mode == null) {
                preferences.remove(key)
            } else {
                preferences[key] = mode.name
            }
        }
    }
}

private fun toComicReaderModePreferences(
    preferences: Preferences,
): ComicReaderModePreferences {
    val libraryModesById = mutableMapOf<String, ComicReaderMode>()
    val seriesModesByKey = mutableMapOf<String, ComicReaderMode>()
    preferences.asMap().forEach { (key, value) ->
        val rawValue = value as? String ?: return@forEach
        val mode = runCatching { ComicReaderMode.valueOf(rawValue) }.getOrNull() ?: return@forEach
        when {
            key.name.startsWith(LIBRARY_MODE_PREFIX) -> {
                decodeComicPreferenceId(key.name.removePrefix(LIBRARY_MODE_PREFIX))?.let { decodedId ->
                    libraryModesById[decodedId] = mode
                }
            }

            key.name.startsWith(SERIES_MODE_PREFIX) -> {
                decodeComicPreferenceId(key.name.removePrefix(SERIES_MODE_PREFIX))?.let { decodedId ->
                    seriesModesByKey[decodedId] = mode
                }
            }
        }
    }
    return ComicReaderModePreferences(
        libraryModesById = libraryModesById,
        seriesModesByKey = seriesModesByKey,
    )
}

private val Context.comicReaderModePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = COMIC_READER_MODE_DATASTORE_NAME,
)

private object ReaderModeKeys {
    fun libraryMode(
        libraryId: String,
    ) = stringPreferencesKey(LIBRARY_MODE_PREFIX + encodeComicPreferenceId(libraryId))

    fun seriesMode(
        seriesKey: String,
    ) = stringPreferencesKey(SERIES_MODE_PREFIX + encodeComicPreferenceId(seriesKey))
}

internal const val COMIC_READER_MODE_DATASTORE_NAME = "comic_reader_mode_preferences"
internal const val LIBRARY_MODE_PREFIX = "library_mode."
internal const val SERIES_MODE_PREFIX = "series_mode."
