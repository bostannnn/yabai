package com.example.unifiedreader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal fun runRoomBootstrap(
    bootstrapPreferences: SharedPreferences,
    bootstrappedKey: String,
    hasExistingRoomData: suspend () -> Boolean,
    migrateLegacyData: suspend () -> Unit,
    clearLegacyState: () -> Unit,
) {
    if (bootstrapPreferences.getBoolean(bootstrappedKey, false)) return

    val shouldSkipMigration = runBlocking(Dispatchers.IO) {
        hasExistingRoomData()
    }
    if (shouldSkipMigration) {
        clearLegacyState()
        markBootstrapped(
            bootstrapPreferences = bootstrapPreferences,
            bootstrappedKey = bootstrappedKey,
        )
        return
    }

    runBlocking(Dispatchers.IO) {
        migrateLegacyData()
    }
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
