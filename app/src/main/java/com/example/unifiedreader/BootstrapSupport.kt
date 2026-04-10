package com.example.unifiedreader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun runRoomBootstrap(
    bootstrapPreferences: SharedPreferences,
    bootstrappedKey: String,
    hasExistingRoomData: suspend () -> Boolean,
    migrateLegacyData: suspend () -> Unit,
    clearLegacyState: () -> Unit,
) = withContext(Dispatchers.IO) {
    if (bootstrapPreferences.getBoolean(bootstrappedKey, false)) return@withContext

    val shouldSkipMigration = hasExistingRoomData()
    if (shouldSkipMigration) {
        clearLegacyState()
        markBootstrapped(
            bootstrapPreferences = bootstrapPreferences,
            bootstrappedKey = bootstrappedKey,
        )
        return@withContext
    }

    migrateLegacyData()
    clearLegacyState()
    markBootstrapped(
        bootstrapPreferences = bootstrapPreferences,
        bootstrappedKey = bootstrappedKey,
    )
}

private fun markBootstrapped(
    bootstrapPreferences: SharedPreferences,
    bootstrappedKey: String,
) {
    bootstrapPreferences.edit()
        .putBoolean(bootstrappedKey, true)
        .apply()
}
