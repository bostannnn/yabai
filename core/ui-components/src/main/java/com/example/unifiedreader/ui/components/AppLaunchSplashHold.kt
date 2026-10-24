package com.example.unifiedreader.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Keeps the launch splash up while the first screen is still placeholders: every
 * [appSkeletonPulse] block on screen holds it, and it goes once none is left, or after
 * [maxHoldMillis] whatever is still loading, counted from the first frame that could have drawn.
 * Once gone it stays gone, so placeholders later on never hold anything. One per cold start: the
 * activity asks [shouldKeep] before each frame (`setKeepOnScreenCondition`) and provides it through
 * [LocalAppLaunchSplashHold]. Main thread only.
 */
class AppLaunchSplashHold(
    private val maxHoldMillis: Long = DefaultMaxHoldMillis,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    // How long it held, and whether it gave up waiting; for the startup log.
    private val onReleased: (heldMillis: Long, timedOut: Boolean) -> Unit = { _, _ -> },
) {
    private var holds = 0
    private var firstCheckMillis = -1L
    var isReleased: Boolean = false
        private set

    /** Counts one more placeholder; false (and nothing counted) once the splash has gone. */
    fun hold(): Boolean {
        if (isReleased) return false
        holds++
        return true
    }

    /** One counted placeholder has left the screen. */
    fun release() {
        if (holds > 0) holds--
    }

    fun shouldKeep(): Boolean {
        if (isReleased) return false
        val now = clock()
        if (firstCheckMillis < 0) firstCheckMillis = now
        val heldMillis = now - firstCheckMillis
        val timedOut = heldMillis >= maxHoldMillis
        if (holds == 0 || timedOut) {
            isReleased = true
            onReleased(heldMillis, timedOut && holds > 0)
            return false
        }
        return true
    }

    companion object {
        const val DefaultMaxHoldMillis: Long = 800L
    }
}

/** The launch splash's hold during a cold start; null otherwise (a rotation, previews, tests). */
val LocalAppLaunchSplashHold = staticCompositionLocalOf<AppLaunchSplashHold?> { null }

/** Holds the launch splash for as long as this is in the composition. */
@Composable
internal fun HoldLaunchSplash() {
    val launchHold = LocalAppLaunchSplashHold.current ?: return
    DisposableEffect(launchHold) {
        val counted = launchHold.hold()
        onDispose { if (counted) launchHold.release() }
    }
}
