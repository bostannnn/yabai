package com.example.unifiedreader.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import com.example.unifiedreader.feature.settings.ThemeMode

fun applyAndroidThemeMode(themeMode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(themeMode.toNightMode())
}

private fun ThemeMode.toNightMode(): Int = when (this) {
    ThemeMode.SystemDynamic -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    else -> if (isDark) {
        AppCompatDelegate.MODE_NIGHT_YES
    } else {
        AppCompatDelegate.MODE_NIGHT_NO
    }
}
