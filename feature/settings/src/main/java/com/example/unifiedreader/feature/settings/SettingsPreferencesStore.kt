package com.example.unifiedreader.feature.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

data class SettingsPreferences(
    val themeMode: ThemeMode = ThemeMode.SystemDynamic,
)

interface SettingsPreferencesStore {
    val preferencesFlow: StateFlow<SettingsPreferences>

    suspend fun setThemeMode(themeMode: ThemeMode)
}

class DataStoreSettingsPreferencesStore(
    private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
    private val startupPreferences: SharedPreferences,
    private val ioRetryDelayMillis: Long = DATASTORE_IO_RETRY_DELAY_MILLIS,
) : SettingsPreferencesStore {
    constructor(
        context: Context,
    ) : this(
        appContext = context.applicationContext,
        dataStore = context.applicationContext.settingsPreferencesDataStore,
        startupPreferences = context.applicationContext.startupPreferences,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val legacyPreferences = appContext.getSharedPreferences(
        LEGACY_SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val startupSnapshot = loadStartupSettingsPreferences(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )

    override val preferencesFlow: StateFlow<SettingsPreferences> = dataStore.data
        .map { preferences ->
            CachedSettingsPreferences(
                preferences = toSettingsPreferences(preferences),
                shouldWriteStartupCache = true,
            )
        }
        .retryWhen { error, _ ->
            if (error is IOException) {
                emit(
                    CachedSettingsPreferences(
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
        writeStartupPreferences(SettingsPreferences(themeMode = themeMode))
    }

    private fun writeStartupPreferences(
        preferences: SettingsPreferences,
    ) {
        startupPreferences.edit()
            .putString(KEY_THEME_MODE, preferences.themeMode.storageValue)
            .commit()
    }

    private fun loadCurrentStartupPreferences(): SettingsPreferences = loadStartupSettingsPreferences(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )
}

private data class CachedSettingsPreferences(
    val preferences: SettingsPreferences,
    val shouldWriteStartupCache: Boolean,
)

fun loadStartupSettingsPreferences(
    context: Context,
): SettingsPreferences = SettingsPreferences(
    themeMode = readStartupThemeMode(
        startupPreferences = context.applicationContext.startupPreferences,
        legacyPreferences = context.applicationContext.getSharedPreferences(
            LEGACY_SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    ),
)

private val Context.settingsPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_PREFERENCES_DATASTORE_NAME,
    produceMigrations = { appContext ->
        listOf(
            SharedPreferencesMigration(appContext, LEGACY_SHARED_PREFERENCES_NAME),
        )
    },
)

private fun toSettingsPreferences(
    preferences: Preferences,
): SettingsPreferences = SettingsPreferences(
    themeMode = ThemeMode.fromStorageValue(preferences[Keys.ThemeMode]),
)

private val Context.startupPreferences: SharedPreferences
    get() = getSharedPreferences(STARTUP_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun loadStartupSettingsPreferences(
    startupPreferences: SharedPreferences,
    legacyPreferences: SharedPreferences,
): SettingsPreferences = SettingsPreferences(
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

internal const val SETTINGS_PREFERENCES_DATASTORE_NAME = "unified_reader_app_preferences"
internal const val LEGACY_SHARED_PREFERENCES_NAME = "unified_reader_app_preferences"
internal const val STARTUP_SHARED_PREFERENCES_NAME = "unified_reader_startup_preferences"
internal const val KEY_THEME_MODE = "theme_mode"
internal const val DATASTORE_IO_RETRY_DELAY_MILLIS = 1_000L

internal object SettingsPreferencesStoreTestAccess {
    const val legacySharedPreferencesName: String = LEGACY_SHARED_PREFERENCES_NAME
    const val startupSharedPreferencesName: String = STARTUP_SHARED_PREFERENCES_NAME
    const val themeModeKey: String = KEY_THEME_MODE

    fun loadStartupPreferences(
        startupPreferences: SharedPreferences,
        legacyPreferences: SharedPreferences,
    ): SettingsPreferences = loadStartupSettingsPreferences(
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )
}
