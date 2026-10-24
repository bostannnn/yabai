package com.example.unifiedreader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.example.unifiedreader.R
import com.example.unifiedreader.feature.settings.ThemeMode

/**
 * The splash styles in `res/values/themes.xml` (UI_REDESIGN_PLAN 9.3): a theme's background with
 * the mark in its primary. Each [style] is `Theme.UnifiedReader.Launch.<name>`.
 */
internal enum class AppLaunchSplash(@StyleRes val style: Int) {
    System(R.style.Theme_UnifiedReader_Launch_System),
    Paper(R.style.Theme_UnifiedReader_Launch_Paper),
    SepiaReading(R.style.Theme_UnifiedReader_Launch_SepiaReading),
    PanelPop(R.style.Theme_UnifiedReader_Launch_PanelPop),
    DetectiveFile(R.style.Theme_UnifiedReader_Launch_DetectiveFile),
    Graphite(R.style.Theme_UnifiedReader_Launch_Graphite),
    HighContrast(R.style.Theme_UnifiedReader_Launch_HighContrast),
    OledReader(R.style.Theme_UnifiedReader_Launch_OledReader),
    Terminal95(R.style.Theme_UnifiedReader_Launch_Terminal95),
    CrimsonCourt(R.style.Theme_UnifiedReader_Launch_CrimsonCourt),
    Blueprint(R.style.Theme_UnifiedReader_Launch_Blueprint),
    VhsNight(R.style.Theme_UnifiedReader_Launch_VhsNight),
}

internal val ThemeMode.launchSplash: AppLaunchSplash
    get() = when (this) {
        ThemeMode.SystemDynamic -> AppLaunchSplash.System
        ThemeMode.Paper, ThemeMode.Light -> AppLaunchSplash.Paper
        ThemeMode.SepiaReading -> AppLaunchSplash.SepiaReading
        ThemeMode.PanelPop -> AppLaunchSplash.PanelPop
        ThemeMode.DetectiveFile -> AppLaunchSplash.DetectiveFile
        ThemeMode.Graphite, ThemeMode.Dark -> AppLaunchSplash.Graphite
        ThemeMode.HighContrast -> AppLaunchSplash.HighContrast
        ThemeMode.OledReader -> AppLaunchSplash.OledReader
        ThemeMode.Terminal95 -> AppLaunchSplash.Terminal95
        ThemeMode.CrimsonCourt -> AppLaunchSplash.CrimsonCourt
        ThemeMode.Blueprint -> AppLaunchSplash.Blueprint
        ThemeMode.VhsNight -> AppLaunchSplash.VhsNight
    }

/**
 * Hands Android [themeMode]'s splash for the next cold start (Android 13 and later; the one on
 * screen has already been drawn). Before Android 13 the splash follows the device instead.
 */
@Composable
internal fun ApplyLaunchSplashTheme(themeMode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, themeMode) {
        setLaunchSplash(activity, themeMode.launchSplash)
        onDispose { }
    }
}

// Android writes the choice to disk, so it is given once per process and again on a theme change,
// not on every rotation.
private var givenLaunchSplash: AppLaunchSplash? = null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun setLaunchSplash(activity: Activity, splash: AppLaunchSplash) {
    if (splash == givenLaunchSplash) return
    activity.splashScreen.setSplashScreenTheme(splash.style)
    givenLaunchSplash = splash
}
