package com.example.unifiedreader

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.example.unifiedreader.feature.reader.epub.discardRestoredEpubNavigators
import com.example.unifiedreader.feature.reader.epub.installEpubNavigatorRestoreFactory
import com.example.unifiedreader.ui.components.AppLaunchSplashHold
import com.example.unifiedreader.ui.components.LocalAppLaunchSplashHold
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        installEpubNavigatorRestoreFactory()
        super.onCreate(savedInstanceState)
        discardRestoredEpubNavigators()
        // A fresh start keeps the splash up while the first screen is still placeholders; a
        // recreated activity (a rotation) has no splash to keep.
        val launchHold = if (savedInstanceState == null) {
            AppLaunchSplashHold(onReleased = ::logLaunchSplashReleased).also { hold ->
                splashScreen.setKeepOnScreenCondition(hold::shouldKeep)
            }
        } else {
            null
        }
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAppLaunchSplashHold provides launchHold) {
                UnifiedReaderRoute()
            }
        }
    }
}

private fun logLaunchSplashReleased(heldMillis: Long, timedOut: Boolean) {
    Log.d("LaunchSplash", "kept ${heldMillis} ms past the first frame" + if (timedOut) ", stopped waiting" else "")
}
