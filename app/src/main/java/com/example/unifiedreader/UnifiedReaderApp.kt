package com.example.unifiedreader

import android.app.Application
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
        val themeMode = loadStartupAppPreferences(this).themeMode
        applyAndroidThemeMode(themeMode)
    }
}
