package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When each server's library last synced on this device, for its settings page. Written when a
 * sync (full or fast, by hand or at launch) succeeds; kept in a small preferences file.
 */
@Singleton
class SourceSyncTimeStore internal constructor(
    // Null keeps nothing: the tests that build the app's controllers without Android.
    openPreferences: () -> SharedPreferences?,
) {
    @Inject constructor(@ApplicationContext context: Context) : this({ sourceSyncTimePreferences(context) })

    private val preferences: SharedPreferences? by lazy(openPreferences)

    fun markSynced(sourceType: SourceType, atEpochMillis: Long = System.currentTimeMillis()) {
        preferences?.edit { putLong(sourceType.name, atEpochMillis) }
    }
}

/** The stored sync times, kept current while the caller is on screen. */
@Composable
internal fun rememberSourceSyncTimes(): Map<SourceType, Long> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { sourceSyncTimePreferences(context) }
    var times by remember(preferences) { mutableStateOf(preferences.readSyncTimes()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ -> times = changed.readSyncTimes() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return times
}

private fun sourceSyncTimePreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(SourceSyncTimesPreferences, Context.MODE_PRIVATE)

private fun SharedPreferences.readSyncTimes(): Map<SourceType, Long> =
    SourceType.entries.mapNotNull { sourceType ->
        getLong(sourceType.name, 0L).takeIf { it > 0L }?.let { sourceType to it }
    }.toMap()

private const val SourceSyncTimesPreferences = "source_sync_times"
