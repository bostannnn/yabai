package com.example.unifiedreader

import android.app.Application
import com.example.unifiedreader.ui.theme.applyAndroidThemeMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UnifiedReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val themeMode = loadStartupAppPreferences(this).themeMode
        applyAndroidThemeMode(themeMode)
    }
}
