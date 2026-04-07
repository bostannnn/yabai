package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
)

interface AppPreferencesStore {
    val preferencesFlow: StateFlow<AppPreferences>

    suspend fun setThemeMode(themeMode: ThemeMode)
}

class DataStoreAppPreferencesStore(
    private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
    private val startupPreferences: SharedPreferences,
    private val ioRetryDelayMillis: Long = DATASTORE_IO_RETRY_DELAY_MILLIS,
) : AppPreferencesStore {
    constructor(
        context: Context,
    ) : this(
        appContext = context.applicationContext,
        dataStore = context.applicationContext.appPreferencesDataStore,
        startupPreferences = context.applicationContext.startupPreferences,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val legacyPreferences = appContext.getSharedPreferences(
        LEGACY_SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val startupSnapshot = loadStartupAppPreferences(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )

    override val preferencesFlow: StateFlow<AppPreferences> = dataStore.data
        .map { preferences ->
            CachedAppPreferences(
                preferences = toAppPreferences(preferences),
                shouldWriteStartupCache = true,
            )
        }
        .retryWhen { error, _ ->
            if (error is IOException) {
                emit(
                    CachedAppPreferences(
                        preferences = loadCurrentStartupPreferences(),
                        shouldWriteStartupCache = false,
                    ),
                )
                delay(ioRetryDelayMillis)
            }
            error is IOException
        }
        .onEach { cachedPreferences ->
            if (cachedPreferences.shouldWriteStartupCache) {
                writeStartupPreferences(cachedPreferences.preferences)
            }
        }
        .map { cachedPreferences -> cachedPreferences.preferences }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = startupSnapshot,
        )

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.ThemeMode] = themeMode.storageValue
        }
        writeStartupPreferences(AppPreferences(themeMode = themeMode))
    }

    private fun writeStartupPreferences(
        preferences: AppPreferences,
    ) {
        startupPreferences.edit()
            .putString(KEY_THEME_MODE, preferences.themeMode.storageValue)
            .commit()
    }

    private fun loadCurrentStartupPreferences(): AppPreferences = loadStartupAppPreferences(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )
}

private data class CachedAppPreferences(
    val preferences: AppPreferences,
    val shouldWriteStartupCache: Boolean,
)

fun loadStartupAppPreferences(
    context: Context,
): AppPreferences = AppPreferences(
    themeMode = readStartupThemeMode(
        startupPreferences = context.applicationContext.startupPreferences,
        legacyPreferences = context.applicationContext.getSharedPreferences(
            LEGACY_SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    ),
)

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_PREFERENCES_DATASTORE_NAME,
    produceMigrations = { appContext ->
        listOf(
            SharedPreferencesMigration(appContext, LEGACY_SHARED_PREFERENCES_NAME),
        )
    },
)

private fun toAppPreferences(
    preferences: Preferences,
): AppPreferences = AppPreferences(
    themeMode = ThemeMode.fromStorageValue(preferences[Keys.ThemeMode]),
)

private val Context.startupPreferences: SharedPreferences
    get() = getSharedPreferences(STARTUP_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun loadStartupAppPreferences(
    startupPreferences: SharedPreferences,
    legacyPreferences: SharedPreferences,
): AppPreferences = AppPreferences(
    themeMode = readStartupThemeMode(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    ),
)

private fun readStartupThemeMode(
    startupPreferences: SharedPreferences,
    legacyPreferences: SharedPreferences,
): ThemeMode {
    val startupValue = startupPreferences.getString(KEY_THEME_MODE, null)
    if (startupValue != null) {
        return ThemeMode.fromStorageValue(startupValue)
    }

    return ThemeMode.fromStorageValue(legacyPreferences.getString(KEY_THEME_MODE, null))
}

private object Keys {
    val ThemeMode = stringPreferencesKey(KEY_THEME_MODE)
}

internal const val APP_PREFERENCES_DATASTORE_NAME = "unified_reader_app_preferences"
internal const val LEGACY_SHARED_PREFERENCES_NAME = "unified_reader_app_preferences"
internal const val STARTUP_SHARED_PREFERENCES_NAME = "unified_reader_startup_preferences"
internal const val KEY_THEME_MODE = "theme_mode"
internal const val DATASTORE_IO_RETRY_DELAY_MILLIS = 1_000L

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object AppPreferencesStoreTestAccess {
    const val legacySharedPreferencesName: String = LEGACY_SHARED_PREFERENCES_NAME
    const val startupSharedPreferencesName: String = STARTUP_SHARED_PREFERENCES_NAME
    const val themeModeKey: String = KEY_THEME_MODE

    fun loadStartupPreferences(
        startupPreferences: SharedPreferences,
        legacyPreferences: SharedPreferences,
    ): AppPreferences = loadStartupAppPreferences(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )
}
