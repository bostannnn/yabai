package com.example.unifiedreader

import android.app.Application
import com.example.unifiedreader.feature.settings.loadStartupSettingsPreferences
import com.example.unifiedreader.ui.theme.applyAndroidThemeMode
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UnifiedReaderApp : Application() {
    @Inject
    lateinit var appStartupInitializer: AppStartupInitializer

    override fun onCreate() {
        super.onCreate()
        appStartupInitializer.initialize()
        val themeMode = loadStartupSettingsPreferences(this).themeMode
        applyAndroidThemeMode(themeMode)
    }
}
