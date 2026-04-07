package com.example.unifiedreader.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import com.example.unifiedreader.feature.settings.ThemeMode

fun applyAndroidThemeMode(themeMode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(themeMode.toNightMode())
}

private fun ThemeMode.toNightMode(): Int = when (this) {
    ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
}
