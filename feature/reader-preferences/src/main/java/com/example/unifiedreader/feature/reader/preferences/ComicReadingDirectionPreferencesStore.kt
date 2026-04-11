package com.example.unifiedreader.feature.reader.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

interface ComicReadingDirectionPreferencesStore {
    val preferencesFlow: StateFlow<ComicReadingDirectionPreferences>

    suspend fun setLibraryDirection(
        libraryId: String,
        direction: ComicReadingDirection,
    )

    suspend fun setSeriesDirectionOverride(
        seriesKey: String,
        direction: ComicReadingDirection?,
    )
}

class DataStoreComicReadingDirectionPreferencesStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.comicReadingDirectionPreferencesDataStore,
    private val ioRetryDelayMillis: Long = DATASTORE_IO_RETRY_DELAY_MILLIS,
) : ComicReadingDirectionPreferencesStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val preferencesFlow: StateFlow<ComicReadingDirectionPreferences> = dataStore.data
        .retryWhen { error, _ ->
            if (error is IOException) {
                emit(emptyPreferences())
                delay(ioRetryDelayMillis)
            }
            error is IOException
        }
        .map(::toComicReadingDirectionPreferences)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ComicReadingDirectionPreferences(),
        )

    override suspend fun setLibraryDirection(
        libraryId: String,
        direction: ComicReadingDirection,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.libraryDirection(libraryId)] = direction.name
        }
    }

    override suspend fun setSeriesDirectionOverride(
        seriesKey: String,
        direction: ComicReadingDirection?,
    ) {
        dataStore.edit { preferences ->
            val key = Keys.seriesDirection(seriesKey)
            if (direction == null) {
                preferences.remove(key)
            } else {
                preferences[key] = direction.name
            }
        }
    }
}

private fun toComicReadingDirectionPreferences(
    preferences: Preferences,
): ComicReadingDirectionPreferences {
    val libraryDirectionsById = mutableMapOf<String, ComicReadingDirection>()
    val seriesDirectionsByKey = mutableMapOf<String, ComicReadingDirection>()
    preferences.asMap().forEach { (key, value) ->
        val rawValue = value as? String ?: return@forEach
        val direction = runCatching { ComicReadingDirection.valueOf(rawValue) }.getOrNull() ?: return@forEach
        when {
            key.name.startsWith(LIBRARY_DIRECTION_PREFIX) -> {
                decodePreferenceId(key.name.removePrefix(LIBRARY_DIRECTION_PREFIX))?.let { decodedId ->
                    libraryDirectionsById[decodedId] = direction
                }
            }

            key.name.startsWith(SERIES_DIRECTION_PREFIX) -> {
                decodePreferenceId(key.name.removePrefix(SERIES_DIRECTION_PREFIX))?.let { decodedId ->
                    seriesDirectionsByKey[decodedId] = direction
                }
            }
        }
    }
    return ComicReadingDirectionPreferences(
        libraryDirectionsById = libraryDirectionsById,
        seriesDirectionsByKey = seriesDirectionsByKey,
    )
}

private val Context.comicReadingDirectionPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = COMIC_READING_DIRECTION_DATASTORE_NAME,
)

private fun encodePreferenceId(
    value: String,
): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodePreferenceId(
    value: String,
): String? = runCatching {
    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}.getOrNull()

private object Keys {
    fun libraryDirection(
        libraryId: String,
    ) = stringPreferencesKey(LIBRARY_DIRECTION_PREFIX + encodePreferenceId(libraryId))

    fun seriesDirection(
        seriesKey: String,
    ) = stringPreferencesKey(SERIES_DIRECTION_PREFIX + encodePreferenceId(seriesKey))
}

internal const val COMIC_READING_DIRECTION_DATASTORE_NAME = "comic_reading_direction_preferences"
internal const val LIBRARY_DIRECTION_PREFIX = "library_direction."
internal const val SERIES_DIRECTION_PREFIX = "series_direction."
internal const val DATASTORE_IO_RETRY_DELAY_MILLIS = 1_000L
