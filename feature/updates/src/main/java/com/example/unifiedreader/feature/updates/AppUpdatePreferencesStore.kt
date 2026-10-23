package com.example.unifiedreader.feature.updates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** What the updater remembers between launches. */
data class AppUpdatePreferences(
    val autoCheck: Boolean = true,
    /** When the last check that reached GitHub finished. */
    val lastCheckAtMillis: Long? = null,
    /** When the last check that failed was made; failures retry sooner than a day. */
    val lastFailedCheckAtMillis: Long? = null,
    /** The release the last successful check found, kept for the next launch and its notes. */
    val latestRelease: AppRelease? = null,
    /** The version whose offer was answered with Later. */
    val dismissedVersion: String? = null,
    /** The app version that last ran, to say "Updated to …" once after an update. */
    val lastRunVersion: String? = null,
)

interface AppUpdatePreferencesStore {
    suspend fun read(): AppUpdatePreferences

    suspend fun update(transform: (AppUpdatePreferences) -> AppUpdatePreferences)
}

class DataStoreAppUpdatePreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : AppUpdatePreferencesStore {
    constructor(context: Context) : this(context.applicationContext.appUpdatePreferencesDataStore)

    override suspend fun read(): AppUpdatePreferences = dataStore.data.first().toAppUpdatePreferences()

    override suspend fun update(transform: (AppUpdatePreferences) -> AppUpdatePreferences) {
        dataStore.edit { preferences ->
            preferences.write(transform(preferences.toAppUpdatePreferences()))
        }
    }
}

private val Context.appUpdatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_update_preferences",
)

private val storedJson = Json { ignoreUnknownKeys = true }

private val AutoCheckKey = booleanPreferencesKey("auto_check")
private val LastCheckAtKey = longPreferencesKey("last_check_at")
private val LastFailedCheckAtKey = longPreferencesKey("last_failed_check_at")
private val LatestReleaseKey = stringPreferencesKey("latest_release")
private val DismissedVersionKey = stringPreferencesKey("dismissed_version")
private val LastRunVersionKey = stringPreferencesKey("last_run_version")

private fun Preferences.toAppUpdatePreferences(): AppUpdatePreferences = AppUpdatePreferences(
    autoCheck = this[AutoCheckKey] ?: true,
    lastCheckAtMillis = this[LastCheckAtKey],
    lastFailedCheckAtMillis = this[LastFailedCheckAtKey],
    latestRelease = this[LatestReleaseKey]?.let { json ->
        runCatching { storedJson.decodeFromString(AppRelease.serializer(), json) }.getOrNull()
    },
    dismissedVersion = this[DismissedVersionKey],
    lastRunVersion = this[LastRunVersionKey],
)

private fun MutablePreferences.write(preferences: AppUpdatePreferences) {
    this[AutoCheckKey] = preferences.autoCheck
    setOrRemove(LastCheckAtKey, preferences.lastCheckAtMillis)
    setOrRemove(LastFailedCheckAtKey, preferences.lastFailedCheckAtMillis)
    setOrRemove(
        LatestReleaseKey,
        preferences.latestRelease?.let { release -> storedJson.encodeToString(AppRelease.serializer(), release) },
    )
    setOrRemove(DismissedVersionKey, preferences.dismissedVersion)
    setOrRemove(LastRunVersionKey, preferences.lastRunVersion)
}

private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else this[key] = value
}
